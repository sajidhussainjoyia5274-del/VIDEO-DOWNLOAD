package com.smartvideodownloader.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smartvideodownloader.MainActivity
import com.smartvideodownloader.R

object Notifications {
    private const val CH_PROGRESS = "downloads_progress"
    private const val CH_DONE = "downloads_done"
    const val ID_FOREGROUND = 1001

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, ctx.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, ctx.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** [percent] < 0 means unknown (indeterminate bar). */
    fun progress(ctx: Context, count: Int, percent: Int, speedBps: Long): Notification {
        val text = if (count == 0) "Starting…" else buildString {
            append(if (count == 1) "1 download" else "$count downloads")
            if (percent >= 0) append(" • $percent%")
            if (speedBps > 0) append(" • ${FormatUtils.speed(speedBps)}")
        }
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun done(ctx: Context, id: Long, name: String) =
        post(ctx, id, "Download complete", name, android.R.drawable.stat_sys_download_done)

    fun failed(ctx: Context, id: Long, name: String, reason: String) =
        post(ctx, id, "Download failed: $name", reason, android.R.drawable.stat_notify_error)

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Long, title: String, text: String, icon: Int) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(2000 + id.toInt(), n)
    }
}
