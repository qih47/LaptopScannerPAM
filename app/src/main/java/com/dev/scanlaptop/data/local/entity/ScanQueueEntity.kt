package com.dev.scanlaptop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_queue")
data class ScanQueueEntity(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    val newStatus: String, // "IN" atau "OUT"
    val laptopUuid: String,
    val nppPetugas: String,
    val keterangan: String,
    val lokasi: String,
    val perangkatDetailsJson: String, // List of RpcDeviceDetail in JSON string format
    val createdAt: Long = System.currentTimeMillis() // timestamp saat di-scan
)
