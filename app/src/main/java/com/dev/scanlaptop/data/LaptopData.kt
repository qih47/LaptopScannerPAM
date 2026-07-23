package com.dev.scanlaptop.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class HistoryLogDetail(
    @SerialName("id") val id: Int? = null,
    @SerialName("log_id") val log_id: String? = null,
    @SerialName("no_seri") val no_seri: String? = null,
    @SerialName("merk") val merk: String? = null,
    @SerialName("tipe") val tipe: String? = null
) : Parcelable

@Parcelize
@Serializable
data class HistoryLog(
    @SerialName("uuid") val uuid: String? = null, // ✅ Pake UUID sesuai Primary Key tabel monitoring_inout
    @SerialName("created_at") val created_at: String,
    @SerialName("status_io") val status_io: String,
    @SerialName("laptop_uuid") val laptop_uuid: String,
    @SerialName("isOpen") val isOpen: Int = 0,
    @SerialName("petugas_npp") val petugas_npp: String? = null,
    @SerialName("lokasi") val lokasi: String? = null,
    @SerialName("keterangan") val keterangan: String? = null,
    @SerialName("registrasi_laptop") val registrasi_laptop: LaptopInfo? = null,
    @SerialName("users") val users: PetugasInfo? = null,
    // ✅ Relasi ke tabel detail (monitoring_inout_detail)
    @SerialName("monitoring_inout_detail") val details: List<HistoryLogDetail> = emptyList()
) : Parcelable

@Parcelize
@Serializable
data class PetugasInfo(
    @SerialName("nama_lengkap") val nama_lengkap: String? = null
) : Parcelable

@Parcelize
@Serializable
data class LaptopInfo(
    @SerialName("no_registrasi") val no_registrasi: String? = null,
    @SerialName("nama_pengguna") val nama_pengguna: String? = null,
    @SerialName("merk") val merk: String? = null,
    @SerialName("tipe_laptop") val tipe_laptop: String? = null,
    @SerialName("instansi_divisi") val instansi_divisi: String? = null,
    @SerialName("no_dokumen") val no_dokumen: String? = null,
    @SerialName("tanggal_dokumen") val tanggal_dokumen: String? = null,
    @SerialName("nomor_seri") val nomor_seri: String? = null,
    @SerialName("nomor_id_card") val nomor_id_card: String? = null,
    @SerialName("jenis") val jenis: String? = null,
    @SerialName("golongan") val golongan: String? = null,
    @SerialName("kepemilikan") val kepemilikan: String? = null
) : Parcelable

@Parcelize
@Serializable
data class PerangkatData(
    @SerialName("id") val id: Int? = null,
    @SerialName("uuid_reg") val uuid_reg: String? = null,
    @SerialName("merk") val merk: String? = null,
    @SerialName("tipe") val tipe: String? = null,
    @SerialName("no_seri") val no_seri: String? = null,
    @SerialName("keterangan") val keterangan: String? = null,
    @SerialName("status_terakhir") val status_terakhir: String? = "OUT"
) : Parcelable

@Parcelize
@Serializable
data class LaptopDetail(
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("no_registrasi") val no_registrasi: String? = null,
    @SerialName("code_qr") val code_qr: String? = null,
    @SerialName("nama_pengguna") val nama_pengguna: String? = null,
    @SerialName("instansi_divisi") val instansi_divisi: String? = null,
    @SerialName("tipe_laptop") val tipe_laptop: String? = null,
    @SerialName("merk") val merk: String? = null,
    @SerialName("no_dokumen") val no_dokumen: String? = null,
    @SerialName("tanggal_dokumen") val tanggal_dokumen: String? = null,
    @SerialName("nomor_seri") val nomor_seri: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("jenis") val jenis: String? = null,
    @SerialName("berlaku_sampai") val berlaku_sampai: String? = null,
    @SerialName("nomor_id_card") val nomor_id_card: String? = null,
    @SerialName("golongan") val golongan: String? = null,
    @SerialName("kepemilikan") val kepemilikan: String? = null,
    @SerialName("daftar_perangkat") val daftar_perangkat: List<PerangkatData> = emptyList()
) : Parcelable