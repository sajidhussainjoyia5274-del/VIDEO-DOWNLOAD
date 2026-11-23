package com.smartvideodownloader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observe(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBps = :speed WHERE id = :id")
    suspend fun progress(id: Long, downloaded: Long, total: Long, speed: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error, speedBps = 0 WHERE id = :id")
    suspend fun setStatus(id: Long, status: DownloadStatus, error: String?)

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', downloadedBytes = :size, totalBytes = :size, " +
            "speedBps = 0, savedUri = :uri, completedAt = :at, errorMessage = NULL WHERE id = :id"
    )
    suspend fun complete(id: Long, size: Long, uri: String, at: Long)

    /** Called on app start: anything that was running when the process died becomes PAUSED. */
    @Query("UPDATE downloads SET status = 'PAUSED', speedBps = 0 WHERE status IN ('QUEUED','DOWNLOADING')")
    suspend fun pauseInterrupted()
}
