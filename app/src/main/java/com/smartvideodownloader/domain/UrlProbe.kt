package com.smartvideodownloader.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class ProbeResult(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val acceptsRanges: Boolean,
)

sealed interface ProbeOutcome {
    data class Ok(val result: ProbeResult) : ProbeOutcome
    /** A platform this app deliberately does not support (Terms of Service). */
    data class Blocked(val reason: String) : ProbeOutcome
    data class Invalid(val reason: String) : ProbeOutcome
}

/**
 * Checks that a URL is a direct, secure link to a plain video file before anything is downloaded.
 * It never touches DRM or streaming manifests, and refuses YouTube links.
 */
class UrlProbe(private val client: OkHttpClient) {

    suspend fun probe(raw: String): ProbeOutcome = withContext(Dispatchers.IO) {
        val text = raw.trim()
        if (text.isEmpty()) return@withContext ProbeOutcome.Invalid("Please enter a link.")
        if (text.startsWith("http://", ignoreCase = true)) {
            return@withContext ProbeOutcome.Invalid("Only secure https:// links are supported.")
        }
        val url = (if ("://" in text) text else "https://$text").toHttpUrlOrNull()
            ?: return@withContext ProbeOutcome.Invalid("That doesn't look like a valid link.")
        if (url.scheme != "https") {
            return@withContext ProbeOutcome.Invalid("Only secure https:// links are supported.")
        }

        if (isBlockedHost(url.host)) {
            return@withContext ProbeOutcome.Blocked(
                "This app can't download from YouTube. Please use YouTube's own Download feature."
            )
        }

        try {
            request(url).use { resp ->
                if (!resp.isSuccessful) {
                    ProbeOutcome.Invalid("The server replied with error ${resp.code}.")
                } else {
                    evaluate(url, resp)
                }
            }
        } catch (e: UnknownHostException) {
            ProbeOutcome.Invalid("Server not found. Check the link and your internet connection.")
        } catch (e: SocketTimeoutException) {
            ProbeOutcome.Invalid("The server took too long to respond.")
        } catch (e: SSLException) {
            ProbeOutcome.Invalid("A secure connection to the server couldn't be established.")
        } catch (e: IOException) {
            ProbeOutcome.Invalid("Couldn't reach the server (${e.message ?: "network error"}).")
        }
    }

    private fun request(url: HttpUrl): Response {
        val head = try {
            client.newCall(Request.Builder().url(url).head().build()).execute()
        } catch (e: IOException) {
            null
        }
        if (head != null && head.isSuccessful) return head
        head?.close()
        // Some servers reject HEAD; ask for a single byte instead.
        return client.newCall(Request.Builder().url(url).header("Range", "bytes=0-0").get().build()).execute()
    }

    private fun evaluate(url: HttpUrl, resp: Response): ProbeOutcome {
        val contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val rawName = resolveName(resp, url)
        val ext = rawName.substringAfterLast('.', "").lowercase()

        if ("mpegurl" in contentType || ext == "m3u8" || ext == "mpd" || contentType == "application/dash+xml") {
            return ProbeOutcome.Invalid("Streaming playlists (HLS/DASH) aren't supported. Use a direct video file link.")
        }
        if (contentType.startsWith("text/") || contentType == "application/json") {
            return ProbeOutcome.Invalid("This link opens a web page, not a video file.")
        }
        if (!(contentType.startsWith("video/") || ext in ALLOWED_EXT)) {
            return ProbeOutcome.Invalid("This link doesn't point to a supported video file (MP4, WebM, MKV, MOV, 3GP).")
        }

        val mime = if (contentType.startsWith("video/")) contentType else EXT_TO_MIME[ext] ?: "video/mp4"
        val name = if (ext in ALLOWED_EXT) rawName else "$rawName.${MIME_TO_EXT[mime] ?: "mp4"}"

        val size = if (resp.code == 206) {
            resp.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull() ?: -1L
        } else {
            resp.header("Content-Length")?.toLongOrNull() ?: -1L
        }
        val ranges = resp.code == 206 ||
            resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true

        return ProbeOutcome.Ok(ProbeResult(url.toString(), name, mime, size, ranges))
    }

    private fun resolveName(resp: Response, url: HttpUrl): String {
        val fromHeader = resp.header("Content-Disposition")?.let {
            Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE)
                .find(it)?.groupValues?.get(1)
        }
        val fromPath = url.pathSegments.lastOrNull { it.isNotBlank() }
        val candidate = (fromHeader ?: fromPath).orEmpty()
        val decoded = runCatching { URLDecoder.decode(candidate, "UTF-8") }.getOrDefault(candidate)
        return sanitize(decoded).ifBlank { "video_${System.currentTimeMillis()}" }
    }

    companion object {
        private val ALLOWED_EXT = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp")
        private val EXT_TO_MIME = mapOf(
            "mp4" to "video/mp4", "m4v" to "video/mp4", "webm" to "video/webm",
            "mkv" to "video/x-matroska", "mov" to "video/quicktime", "3gp" to "video/3gpp",
        )
        private val MIME_TO_EXT = mapOf(
            "video/mp4" to "mp4", "video/webm" to "webm", "video/x-matroska" to "mkv",
            "video/quicktime" to "mov", "video/3gpp" to "3gp",
        )
        private val BLOCKED = listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com")

        fun isBlockedHost(host: String): Boolean {
            val h = host.lowercase()
            return BLOCKED.any { h == it || h.endsWith(".$it") }
        }

        fun sanitize(name: String): String =
            name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim().trim('.').take(120)
    }
}
