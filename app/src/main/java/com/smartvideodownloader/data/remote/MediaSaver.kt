package com.smartvideodownloader.data.remote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Moves a finished temp file into the public Movies/SmartVideoDownloader folder. */
object MediaSaver {
    private const val FOLDER = "SmartVideoDownloader"

    fun save(context: Context, source: File, displayName: String, mime: String): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(context, source, displayName, mime)
        else saveLegacy(context, source, displayName, mime)

    private fun saveScoped(context: Context, source: File, name: String, mime: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("Couldn't create the video file.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't open the video file.")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, source: File, name: String, mime: String): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Couldn't create the Movies folder.")
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "mp4")
        var target = File(dir, name)
        var i = 1
        while (target.exists()) { target = File(dir, "${base}_$i.$ext"); i++ }
        source.copyTo(target)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, target.name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.DATA, target.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(target)
    }
}
