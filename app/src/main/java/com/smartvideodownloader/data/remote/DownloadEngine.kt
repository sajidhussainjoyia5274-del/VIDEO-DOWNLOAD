package com.smartvideodownloader.data.remote

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.smartvideodownloader.data.local.DownloadDao
import com.smartvideodownloader.data.local.DownloadEntity
import com.smartvideodownloader.data.local.DownloadStatus
import com.smartvideodownloader.domain.ProbeResult
import com.smartvideodownloader.service.DownloadService
import com.smartvideodownloader.util.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

/**
 * Download engine: OkHttp + HTTP Range requests.
 *  - Data is streamed into a private ".part" file, so pausing/resuming continues from the same byte.
 *  - A semaphore limits parallel downloads; the rest wait as QUEUED.
 *  - On completion the file is moved into Movies/SmartVideoDownloader via MediaStore.
 */
class DownloadEngine(
    private val context: Context,
    private val dao: DownloadDao,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val permits = Semaphore(MAX_PARALLEL)

    val activeDownloads: Flow<List<DownloadEntity>> = dao.observeAll().map { list ->
        list.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
    }

    suspend fun enqueue(probe: ProbeResult): Long {
        val id = dao.insert(
            DownloadEntity(
                url = probe.url,
                fileName = probe.fileName,
                mimeType = probe.mimeType,
                totalBytes = probe.sizeBytes,
                acceptsRanges = probe.acceptsRanges,
            )
        )
        startJob(id)
        return id
    }

    /** Resume a paused download, or retry a failed/canceled one. */
    suspend fun resume(id: Long) {
        val e = dao.get(id) ?: return
        if (e.status == DownloadStatus.COMPLETED || e.status == DownloadStatus.DOWNLOADING) return
        startJob(id)
    }

    /** Returns false when the server doesn't support resuming (pause is then not offered). */
    suspend fun pause(id: Long, force: Boolean = false): Boolean {
        val e = dao.get(id) ?: return false
        if (!e.acceptsRanges && !force) return false
        jobs.remove(id)?.cancelAndJoin()
        val now = dao.get(id) ?: return false
        if (now.status == DownloadStatus.DOWNLOADING || now.status == DownloadStatus.QUEUED) {
            dao.setStatus(id, DownloadStatus.PAUSED, null)
        }
        return true
    }

    suspend fun pauseAll() {
        jobs.keys.toList().forEach { pause(it, force = true) }
    }

    /** Stops the download and discards the partial data. */
    suspend fun cancel(id: Long) {
        jobs.remove(id)?.cancelAndJoin()
        partFile(id).delete()
        val now = dao.get(id) ?: return
        if (now.status != DownloadStatus.COMPLETED) {
            dao.setStatus(id, DownloadStatus.CANCELED, null)
            dao.progress(id, 0, now.totalBytes, 0)
        }
    }

    private suspend fun startJob(id: Long) {
        if (jobs[id]?.isActive == true) return
        dao.setStatus(id, DownloadStatus.QUEUED, null)
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        jobs[id] = scope.launch { permits.withPermit { process(id) } }
    }

    private suspend fun process(id: Long) {
        val e = dao.get(id) ?: return
        dao.setStatus(id, DownloadStatus.DOWNLOADING, null)
        try {
            transfer(e, allowRestart = true)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (!currentCoroutineContext().isActive) throw CancellationException("Canceled")
            val msg = friendlyError(t)
            dao.setStatus(id, DownloadStatus.FAILED, msg)
            Notifications.failed(context, id, e.fileName, msg)
        }
    }

    private suspend fun transfer(e: DownloadEntity, allowRestart: Boolean) {
        val part = partFile(e.id)
        var existing = if (part.exists()) part.length() else 0L
        val builder = Request.Builder().url(e.url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        val call = client.newCall(builder.build())

        var restart = false
        var finalSize = -1L

        coroutineScope {
            // Cancelling the coroutine aborts the blocking network read immediately.
            val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { resp ->
                    if (resp.code == 416 && existing > 0 && allowRestart) {
                        part.delete()
                        restart = true
                        return@use
                    }
                    if (!resp.isSuccessful) throw IOException("Server returned error ${resp.code}")
                    val body = resp.body ?: throw IOException("Empty response from server")

                    val resumed = resp.code == 206
                    if (!resumed) existing = 0 // server ignored Range: start over
                    val len = body.contentLength()
                    val total = if (resumed) {
                        resp.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                            ?: if (len >= 0) existing + len else -1L
                    } else len

                    var downloaded = existing
                    dao.progress(e.id, downloaded, total, 0)

                    val buf = ByteArray(64 * 1024)
                    var lastWrite = SystemClock.elapsedRealtime()
                    var windowBytes = 0L
                    FileOutputStream(part, resumed).use { out ->
                        body.byteStream().use { input ->
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                downloaded += n
                                windowBytes += n
                                ensureActive()
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastWrite >= 500) {
                                    val speed = windowBytes * 1000 / (now - lastWrite)
                                    dao.progress(e.id, downloaded, total, speed)
                                    lastWrite = now
                                    windowBytes = 0
                                }
                            }
                        }
                    }
                    if (total > 0 && downloaded != total) throw IOException("Download incomplete")
                    finalSize = downloaded
                }
            } finally {
                watcher.cancel()
            }
        }

        if (restart) {
            transfer(e, allowRestart = false)
            return
        }

        val uri = MediaSaver.save(context, part, e.fileName, e.mimeType)
        part.delete()
        dao.complete(e.id, finalSize, uri.toString(), System.currentTimeMillis())
        Notifications.done(context, e.id, e.fileName)
    }

    private fun friendlyError(t: Throwable): String = when (t) {
        is UnknownHostException -> "No internet connection, or the server wasn't found."
        is SocketTimeoutException -> "The connection timed out."
        is SSLException -> "A secure connection couldn't be established."
        is SecurityException -> "Storage permission is required to save videos."
        is IOException -> t.message ?: "Network error."
        else -> t.message ?: "Download failed."
    }

    private fun partFile(id: Long): File {
        val dir = File(context.filesDir, "downloads")
        dir.mkdirs()
        return File(dir, "$id.part")
    }

    companion object {
        const val MAX_PARALLEL = 2
    }
}
