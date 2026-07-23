package com.dev.scanlaptop.data.repository

import android.util.Log
import com.dev.scanlaptop.data.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────
// Model lokal (hanya dipakai di repository ini)
// ─────────────────────────────────────────────────────────────

@Serializable
data class PerangkatOutDevice(
    @SerialName("id") val id: Int? = null,
    @SerialName("uuid_reg") val uuid_reg: String? = null,
    @SerialName("merk") val merk: String? = null,
    @SerialName("tipe") val tipe: String? = null,
    @SerialName("no_seri") val no_seri: String? = null,
    @SerialName("status_terakhir") val status_terakhir: String? = null
)

@Serializable
data class LaptopBasicInfo(
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("nama_pengguna") val nama_pengguna: String? = null,
    @SerialName("instansi_divisi") val instansi_divisi: String? = null,
    @SerialName("kepemilikan") val kepemilikan: String? = null
)

@Serializable
data class OutLogInfo(
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("created_at") val created_at: String = "",
    @SerialName("laptop_uuid") val laptop_uuid: String = "",
    @SerialName("status_io") val status_io: String = "",
    @SerialName("petugas_npp") val petugas_npp: String? = null,
    @SerialName("users") val users: com.dev.scanlaptop.data.PetugasInfo? = null
)

// ─────────────────────────────────────────────────────────────
// Data class hasil akhir untuk UI
// ─────────────────────────────────────────────────────────────

data class OverdueItem(
    val laptopUuid: String,
    val namaUser: String,
    val instansiDivisi: String,
    val kepemilikan: String,
    val lastInTime: String? = null,    // ISO timestamp saat terakhir IN/OUT
    val durasiJam: Long,               // Sudah berapa jam di luar/dalam
    val perangkatList: List<String>,   // Serial number perangkat
    val namaPetugasIn: String? = null, // Nama petugas
    val type: String = "IN"            // "IN" (visitor) or "OUT" (asset)
)

/**
 * Repository untuk deteksi laptop overdue (terlambat kembali).
 */
class OverdueRepository {

    companion object {
        private const val TAG = "OverdueRepository"
    }

    /**
     * Ambil daftar laptop yang masih di dalam (overdue) melebihi threshold.
     */
    suspend fun fetchOverdueItems(thresholdHours: Int = 8): List<OverdueItem> = coroutineScope {
        try {
            // Step 1: Ambil semua perangkat yang statusnya IN atau OUT
            val allDevices = SupabaseConfig.client
                .from("daftar_perangkat")
                .select(Columns.raw("id,uuid_reg,merk,tipe,no_seri,status_terakhir")) {
                    filter { isIn("status_terakhir", listOf("IN", "OUT")) }
                }
                .decodeList<PerangkatOutDevice>()

            Log.d(TAG, "Devices found: ${allDevices.size}")

            if (allDevices.isEmpty()) return@coroutineScope emptyList()

            val laptopUuids = allDevices.mapNotNull { it.uuid_reg }.distinct()

            // ── Step 2 & 3 dijalankan paralel ─────────────────────────────
            val laptopInfoDef = async {
                try {
                    val infoList = mutableListOf<LaptopBasicInfo>()
                    laptopUuids.chunked(50).forEach { chunk ->
                        val chunkInfo = SupabaseConfig.client
                            .from("registrasi_laptop")
                            .select(Columns.raw("uuid,nama_pengguna,instansi_divisi,kepemilikan")) {
                                filter { isIn("uuid", chunk) }
                            }
                            .decodeList<LaptopBasicInfo>()
                        infoList.addAll(chunkInfo)
                    }
                    infoList.associateBy { it.uuid }
                } catch (e: Exception) {
                    Log.e(TAG, "Fetch laptop info error:${e.message}")
                    emptyMap()
                }
            }

            val allLogsDef = async {
                try {
                    val logsList = mutableListOf<OutLogInfo>()
                    laptopUuids.chunked(50).forEach { chunk ->
                        val chunkLogs = SupabaseConfig.client
                            .from("monitoring_inout")
                            .select(Columns.raw("uuid,created_at,laptop_uuid,status_io,petugas_npp,users(nama_lengkap)")) {
                                filter {
                                    isIn("laptop_uuid", chunk)
                                }
                                order("created_at", order = Order.DESCENDING)
                                limit(10000)
                            }
                            .decodeList<OutLogInfo>()
                        logsList.addAll(chunkLogs)
                    }
                    logsList.groupBy { it.laptop_uuid }
                } catch (e: Exception) {
                    Log.e(TAG, "Fetch logs error:${e.message}")
                    emptyMap()
                }
            }

            val laptopInfoMap = laptopInfoDef.await()
            val allLogsPerLaptop = allLogsDef.await()

            // Step 4: Build OverdueItem
            val nowUtc = ZonedDateTime.now(ZoneOffset.UTC)
            val devicesByLaptop = allDevices.groupBy { it.uuid_reg ?: "" }

            laptopUuids.mapNotNull { laptopUuid ->
                val devices = devicesByLaptop[laptopUuid] ?: return@mapNotNull null
                val laptopInfo = laptopInfoMap[laptopUuid]

                val statusTerakhir = devices.firstOrNull()?.status_terakhir ?: "IN"
                
                // Cek kepemilikan lebih fleksibel
                val kepemilikan = laptopInfo?.kepemilikan ?: ""
                val isAsset = kepemilikan.contains("asset", ignoreCase = true) || 
                              kepemilikan.contains("aset", ignoreCase = true)
                // Anggap visitor/pribadi jika mengandung kata "pribadi" atau kosong (belum diisi)
                val isPribadi = kepemilikan.contains("pribadi", ignoreCase = true) || kepemilikan.isBlank()
                
                // Tamu/karyawan biasa overdue jika IN. Aset perusahaan overdue jika OUT.
                val isVisitorOverdue = statusTerakhir == "IN" && isPribadi
                val isAssetOverdue = statusTerakhir == "OUT" && isAsset
                
                if (!isVisitorOverdue && !isAssetOverdue) {
                    Log.d(TAG, "Skipped $laptopUuid: status=$statusTerakhir, kepemilikan='$kepemilikan'")
                    return@mapNotNull null
                }

                val overdueType = if (isVisitorOverdue) "IN" else "OUT"
                
                val logsForLaptop = allLogsPerLaptop[laptopUuid] ?: emptyList()
                val targetLog = logsForLaptop.firstOrNull { it.status_io == overdueType }

                // Hitung durasi
                val durasiJam: Long = if (targetLog != null && targetLog.created_at.isNotBlank()) {
                    try {
                        val logTime = ZonedDateTime.parse(targetLog.created_at)
                        ChronoUnit.HOURS.between(logTime, nowUtc)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot parse timestamp for $laptopUuid: ${e.message}")
                        -1L
                    }
                } else {
                    -1L
                }

                if (durasiJam >= thresholdHours) {
                    val petugasName = targetLog?.users?.nama_lengkap ?: targetLog?.petugas_npp ?: "Petugas Tidak Diketahui"
                    Log.d(TAG, "Included $laptopUuid: durasi=$durasiJam >= $thresholdHours")

                    OverdueItem(
                        laptopUuid = laptopUuid,
                        namaUser = laptopInfo?.nama_pengguna ?: "Pengguna Tidak Diketahui",
                        instansiDivisi = laptopInfo?.instansi_divisi ?: "-",
                        kepemilikan = laptopInfo?.kepemilikan ?: "-",
                        lastInTime = targetLog?.created_at,
                        durasiJam = durasiJam,
                        perangkatList = devices.mapNotNull { it.no_seri },
                        namaPetugasIn = petugasName,
                        type = overdueType
                    )
                } else null
            }.sortedByDescending { it.durasiJam }

        } catch (e: Exception) {
            Log.e(TAG, "fetchOverdueItems error:$e.message")
            emptyList()
        }
    }

    /** Hitung total overdue untuk badge counter di bottom bar. */
    suspend fun fetchOverdueCount(thresholdHours: Int = 8): Int {
        return fetchOverdueItems(thresholdHours).size
    }
}
