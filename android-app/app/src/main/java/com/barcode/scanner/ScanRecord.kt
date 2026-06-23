package com.barcode.scanner

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val data: String,
    val format: String,
    val isUrl: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
