package com.barcode.scanner

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScanRecord>>

    @Insert
    suspend fun insert(record: ScanRecord): Long

    @Delete
    suspend fun delete(record: ScanRecord)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
