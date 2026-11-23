package com.smartvideodownloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED }

/** One row per download. Also serves as the download history. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    /** -1 when the server did not report a size. */
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBps: Long = 0,
    val errorMessage: String? = null,
    /** content:// Uri of the finished file in MediaStore. */
    val savedUri: String? = null,
    /** Pause/resume is only offered when the server supports HTTP Range. */
    val acceptsRanges: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)
