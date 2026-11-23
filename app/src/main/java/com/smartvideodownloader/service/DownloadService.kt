package com.smartvideodownloader.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.smartvideodownloader.SmartVideoApp
import com.smartvideodownloader.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps downloads alive in the background and shows one ongoing progress notification. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val first = Notifications.progress(this, count = 0, percent = -1, speedBps = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.ID_FOREGROUND, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifications.ID_FOREGROUND, first)
        }

        if (observer == null) {
            val app = application as SmartVideoApp
            val nm = getSystemService(NotificationManager::class.java)
            observer = scope.launch {
                app.engine.activeDownloads.collect { active ->
                    if (active.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val known = active.filter { it.totalBytes > 0 }
                        val total = known.sumOf { it.totalBytes }
                        val done = known.sumOf { it.downloadedBytes }
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        val speed = active.sumOf { it.speedBps }
                        nm.notify(
                            Notifications.ID_FOREGROUND,
                            Notifications.progress(this@DownloadService, active.size, percent, speed),
                        )
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 limits "data sync" services to ~6 hours. Pause cleanly so nothing is lost. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val app = application as SmartVideoApp
        app.appScope.launch { app.engine.pauseAll() }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
