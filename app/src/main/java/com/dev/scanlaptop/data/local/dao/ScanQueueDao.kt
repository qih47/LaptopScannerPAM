package com.dev.scanlaptop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dev.scanlaptop.data.local.entity.ScanQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(item: ScanQueueEntity)

    @Query("SELECT * FROM scan_queue ORDER BY createdAt ASC")
    fun getAllQueues(): Flow<List<ScanQueueEntity>>

    @Query("SELECT * FROM scan_queue ORDER BY createdAt ASC")
    suspend fun getAllQueuesSync(): List<ScanQueueEntity>

    @Query("DELETE FROM scan_queue WHERE id = :id")
    suspend fun deleteQueue(id: Int)
}
