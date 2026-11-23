package com.smartvideodownloader

import android.app.Application
import androidx.room.Room
import com.smartvideodownloader.data.local.AppDatabase
import com.smartvideodownloader.data.remote.DownloadEngine
import com.smartvideodownloader.domain.UrlProbe
import com.smartvideodownloader.util.NetworkMonitor
import com.smartvideodownloader.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Simple manual dependency container (no DI framework needed for an app this size). */
class SmartVideoApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "smart_video_downloader.db").build()
    }
    val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followSslRedirects(false) // never follow an https -> http redirect
            .build()
    }
    val urlProbe by lazy { UrlProbe(httpClient) }
    val engine by lazy { DownloadEngine(this, database.downloadDao(), httpClient, appScope) }
    val networkMonitor by lazy { NetworkMonitor(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Anything that was running when the process died is paused, ready to resume.
        appScope.launch { database.downloadDao().pauseInterrupted() }
    }
}
