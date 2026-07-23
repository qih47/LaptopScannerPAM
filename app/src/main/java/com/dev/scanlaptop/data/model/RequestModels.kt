package com.dev.scanlaptop.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO untuk insert ke tabel monitoring_inout.
 */
@Serializable
data class MonitoringInOutInsert(
    @SerialName("status_io") val status_io: String,
    @SerialName("laptop_uuid") val laptop_uuid: String,
    @SerialName("petugas_npp") val petugas_npp: String,
    @SerialName("keterangan") val keterangan: String? = null,
    @SerialName("lokasi") val lokasi: String? = null
)

/**
 * DTO untuk parameter RPC proses_transaksi.
 */
@Serializable
data class ProcessTransactionRequest(
    val p_status_io: String,
    val p_laptop_uuid: String,
    val p_petugas_npp: String,
    val p_keterangan: String,
    val p_lokasi: String,
    val p_perangkat_details: List<RpcDeviceDetail>
)

@Serializable
data class RpcDeviceDetail(
    val no_seri: String,
    val merk: String?,
    val tipe: String?
)

/**
 * DTO untuk insert ke tabel monitoring_inout_detail.
 */
@Serializable
data class MonitoringInOutDetailInsert(
    @SerialName("log_id") val log_id: String,
    @SerialName("no_seri") val no_seri: String,
    @SerialName("merk") val merk: String?,
    @SerialName("tipe") val tipe: String?
)

/**
 * DTO untuk update status_terakhir di tabel daftar_perangkat.
 */
@Serializable
data class PerangkatStatusUpdate(
    @SerialName("status_terakhir") val status_terakhir: String
)

/**
 * Model untuk update isOpen di tabel monitoring_inout.
 */
@Serializable
data class IsOpenUpdate(
    @SerialName("isOpen") val isOpen: Int
)

/**
 * Model range waktu untuk filter query.
 */
data class TimeRange(
    val start: String? = null,
    val end: String? = null
)

/**
 * Model hasil statistik dashboard.
 */
data class StatsResult(
    val total: Int = 0,
    val inCount: Int = 0,
    val outCount: Int = 0
)
