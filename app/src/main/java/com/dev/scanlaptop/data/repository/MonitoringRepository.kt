package com.dev.scanlaptop.data.repository

import android.util.Log
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.LaptopDetail
import com.dev.scanlaptop.data.SupabaseConfig
import com.dev.scanlaptop.data.UserData
import com.dev.scanlaptop.data.model.IsOpenUpdate
import com.dev.scanlaptop.data.model.MonitoringInOutDetailInsert
import com.dev.scanlaptop.data.model.MonitoringInOutInsert
import com.dev.scanlaptop.data.model.StatsResult
import com.dev.scanlaptop.data.model.TimeRange
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Model sederhana untuk decode event INSERT dari Supabase Realtime.
 *  Event INSERT tidak menyertakan data relasi, hanya kolom dasar tabel. */
@Serializable
private data class RealtimeInsertEvent(
    val uuid: String? = null,
    val status_io: String? = null,
    val laptop_uuid: String? = null,
    val petugas_npp: String? = null,
    val created_at: String? = null
)

/**
 * Repository untuk semua operasi monitoring_inout dan monitoring_inout_detail.
 * Single source of truth untuk data riwayat transaksi.
 */
class MonitoringRepository {

    companion object {
        private const val TAG = "MonitoringRepository"
        const val PAGE_SIZE = 20

        // Query columns untuk history dengan relasi
        private val HISTORY_COLUMNS = Columns.raw(
            "uuid,created_at,status_io,laptop_uuid,petugas_npp,lokasi,keterangan,isOpen," +
                    "registrasi_laptop!monitoring_inout_laptop_uuid_fkey(*)," +
                    "users!monitoring_inout_petugas_npp_fkey(nama_lengkap)," +
                    "monitoring_inout_detail(*)"
        )
        
        // Simpan channel sebagai singleton agar tidak duplicate subscribe
        var liveChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    }

    // ─────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────

    /** Ambil data riwayat dengan pagination dan filter. */
    suspend fun fetchHistory(
        page: Int,
        statusFilter: String,
        timeFilter: String
    ): List<HistoryLog> {
        val from = page * PAGE_SIZE
        val to = from + PAGE_SIZE - 1
        return try {
            val timeRange = getTimeRange(timeFilter)
            SupabaseConfig.client.from("monitoring_inout")
                .select(columns = HISTORY_COLUMNS) {
                    order("created_at", order = Order.DESCENDING)
                    range(from.toLong(), to.toLong())
                    if (statusFilter != "ALL") filter { eq("status_io", statusFilter) }
                    if (timeRange.start != null && timeRange.end != null) {
                        filter {
                            and {
                                gte("created_at", timeRange.start)
                                lt("created_at", timeRange.end)
                            }
                        }
                    }
                }
                .decodeList<HistoryLog>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchHistory error: ${e.message}")
            emptyList()
        }
    }

    /** Ambil statistik (total, IN count, OUT count) secara paralel. */
    suspend fun fetchStats(timeFilter: String): StatsResult = coroutineScope {
        try {
            val timeRange = getTimeRange(timeFilter)
            val totalDef = async { fetchCount("ALL", timeRange) }
            val inDef = async { fetchCount("IN", timeRange) }
            val outDef = async { fetchCount("OUT", timeRange) }
            StatsResult(
                total = totalDef.await(),
                inCount = inDef.await(),
                outCount = outDef.await()
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchStats error: ${e.message}")
            StatsResult()
        }
    }

    /**
     * Search riwayat server-side menggunakan Supabase `ilike` operator.
     * Lebih efisien dari client-side filter 500 item.
     */
    suspend fun searchHistory(
        query: String,
        statusFilter: String,
        timeFilter: String
    ): List<HistoryLog> {
        if (query.isBlank()) return emptyList()
        return try {
            val timeRange = getTimeRange(timeFilter)
            // Cari berdasarkan nama_pengguna di tabel registrasi_laptop (relasi)
            // Kita ambil dengan filter di sisi query + limit reasonable
            val response = SupabaseConfig.client.from("monitoring_inout")
                .select(columns = HISTORY_COLUMNS) {
                    order("created_at", order = Order.DESCENDING)
                    limit(200) // limit wajar untuk search
                    if (statusFilter != "ALL") filter { eq("status_io", statusFilter) }
                    if (timeRange.start != null && timeRange.end != null) {
                        filter {
                            and {
                                gte("created_at", timeRange.start)
                                lt("created_at", timeRange.end)
                            }
                        }
                    }
                }
                .decodeList<HistoryLog>()

            // Filter client-side berdasarkan nama dan merk (data relasi tidak bisa ilike di PostgREST nested)
            response.filter { log ->
                val nama = log.registrasi_laptop?.nama_pengguna ?: ""
                val merk = log.registrasi_laptop?.merk ?: ""
                val divisi = log.registrasi_laptop?.instansi_divisi ?: ""
                nama.contains(query, ignoreCase = true) ||
                        merk.contains(query, ignoreCase = true) ||
                        divisi.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchHistory error: ${e.message}")
            emptyList()
        }
    }

    /** Ambil riwayat berdasarkan laptop_uuid (untuk DetailLaptopScreen). */
    suspend fun fetchHistoryByLaptop(laptopUuid: String): List<HistoryLog> {
        return try {
            SupabaseConfig.client.from("monitoring_inout")
                .select(columns = HISTORY_COLUMNS) {
                    filter { eq("laptop_uuid", laptopUuid) }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<HistoryLog>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchHistoryByLaptop error: ${e.message}")
            emptyList()
        }
    }

    /** Ambil riwayat khusus milik petugas yang sedang login (berdasarkan NPP). */
    suspend fun fetchHistoryByPetugas(npp: String): List<HistoryLog> {
        return try {
            SupabaseConfig.client.from("monitoring_inout")
                .select(columns = HISTORY_COLUMNS) {
                    filter { eq("petugas_npp", npp) }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<HistoryLog>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchHistoryByPetugas error: ${e.message}")
            emptyList()
        }
    }
    /**
     * Ambil transaksi terbaru untuk deteksi overdue.
     * Mengambil semua log OUT dalam rentang waktu tertentu.
     */
    suspend fun fetchRecentLogs(daysBack: Int = 7): List<HistoryLog> {
        return try {
            val startDate = if (daysBack > 0 && daysBack < 365) {
                val zdt = ZonedDateTime.now(ZoneId.of("Asia/Jakarta"))
                    .minusDays(daysBack.toLong())
                    .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                zdt.toInstant().toString()
            } else null

            val allLogs = mutableListOf<HistoryLog>()
            var offset = 0L
            val pageSize = 1000L

            while (true) {
                val chunk = SupabaseConfig.client.from("monitoring_inout")
                    .select(columns = HISTORY_COLUMNS) {
                        order("created_at", order = Order.DESCENDING)
                        if (startDate != null) {
                            filter {
                                gte("created_at", startDate)
                            }
                        }
                        range(offset, offset + pageSize - 1)
                    }
                    .decodeList<HistoryLog>()

                allLogs.addAll(chunk)
                if (chunk.size < pageSize) break
                offset += pageSize
            }
            allLogs
        } catch (e: Exception) {
            Log.e(TAG, "fetchRecentLogs error: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────

    /**
     * Ambil transaksi untuk ekspor PDF berdasarkan rentang tanggal.
     * endDate bersifat inklusif.
     */
    suspend fun fetchLogsByDateRange(startDate: LocalDate, endDate: LocalDate): List<HistoryLog> {
        return try {
            val startZdt = startDate.atStartOfDay(ZoneId.of("Asia/Jakarta"))
            val endZdt = endDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Jakarta"))
            val formatter = DateTimeFormatter.ISO_INSTANT

            val startUtcStr = startZdt.toInstant().toString()
            val endUtcStr = endZdt.toInstant().toString()

            val allLogs = mutableListOf<HistoryLog>()
            var offset = 0L
            val pageSize = 1000L

            while (true) {
                val chunk = SupabaseConfig.client.from("monitoring_inout")
                    .select(columns = HISTORY_COLUMNS) {
                        order("created_at", order = Order.DESCENDING)
                        filter {
                            gte("created_at", startUtcStr)
                            lt("created_at", endUtcStr)
                        }
                        range(offset, offset + pageSize - 1)
                    }
                    .decodeList<HistoryLog>()

                allLogs.addAll(chunk)
                if (chunk.size < pageSize) break
                offset += pageSize
            }
            allLogs
        } catch (e: Exception) {
            Log.e(TAG, "fetchLogsByDateRange error: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────
    // REALTIME
    // ─────────────────────────────────────────────────────────

    /**
     * Subscribe ke perubahan INSERT tabel monitoring_inout via Supabase Realtime.
     * @return Flow<HistoryLog> yang emit setiap kali ada transaksi baru.
     */
    fun subscribeToNewTransactions(): kotlinx.coroutines.flow.Flow<HistoryLog> {
        return getRealtimeChannel().postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "monitoring_inout"
        }.mapNotNull { action ->
            try {
                // Event INSERT dari realtime TIDAK menyertakan join data.
                // Decode basic event dulu, lalu fetch full record dengan join dari Supabase.
                val event = action.decodeRecord<RealtimeInsertEvent>()
                val uuid = event.uuid ?: return@mapNotNull null
                // Fetch full HistoryLog dengan semua relasi dari database
                SupabaseConfig.client.from("monitoring_inout")
                    .select(columns = HISTORY_COLUMNS) {
                        filter { eq("uuid", uuid) }
                        limit(1)
                    }
                    .decodeList<HistoryLog>()
                    .firstOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "subscribeToNewTransactions decode error: ${e.message}")
                null
            }
        }
    }

    /** Dapatkan channel realtime singleton untuk di-subscribe/unsubscribe dari luar. */
    fun getRealtimeChannel(): io.github.jan.supabase.realtime.RealtimeChannel {
        if (liveChannel == null) {
            liveChannel = SupabaseConfig.client.channel("monitoring-inout-live")
        }
        return liveChannel!!
    }
    
    suspend fun connectRealtime(blockUntilSubscribed: Boolean = false) {
        val channel = getRealtimeChannel()
        try {
            channel.subscribe(blockUntilSubscribed = blockUntilSubscribed)
        } catch (e: Exception) {
            Log.w(TAG, "Realtime channel might already be joined: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────────────────

    /** Tandai log sebagai sudah dibuka (isOpen = 1). */
    suspend fun markAsRead(laptopUuid: String, createdAt: String) {
        try {
            SupabaseConfig.client.from("monitoring_inout")
                .update(IsOpenUpdate(isOpen = 1)) {
                    filter {
                        and {
                            eq("laptop_uuid", laptopUuid)
                            eq("created_at", createdAt)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "markAsRead error: ${e.message}")
        }
    }

    /**
     * Simpan transaksi IN/OUT beserta detail perangkat.
     * @return [HistoryLog] hasil insert header.
     * @throws Exception jika ada kegagalan.
     */
    suspend fun saveTransaction(
        laptop: LaptopDetail,
        userData: UserData,
        selectedSerialNumbers: List<String>,
        keterangan: String?,
        newStatus: String
    ): HistoryLog {
        val finalKeterangan = if (keterangan.isNullOrBlank()) "Scan via Mobile" else keterangan

        // Siapkan detail perangkat
        val details = selectedSerialNumbers.mapNotNull { sn ->
            val dev = laptop.daftar_perangkat.find { it.no_seri == sn }
            com.dev.scanlaptop.data.model.RpcDeviceDetail(
                no_seri = sn,
                merk = dev?.merk,
                tipe = dev?.tipe
            )
        }

        // Siapkan parameter RPC
        val request = com.dev.scanlaptop.data.model.ProcessTransactionRequest(
            p_status_io = newStatus,
            p_laptop_uuid = laptop.uuid ?: error("Laptop UUID tidak boleh kosong"),
            p_petugas_npp = userData.npp,
            p_keterangan = finalKeterangan,
            p_lokasi = "Gerbang Pos 2",
            p_perangkat_details = details
        )

        // Panggil RPC yang menangani pengecekan status, insert log, insert detail, dan update status perangkat
        val insertedLog = SupabaseConfig.client.postgrest.rpc(
            "proses_transaksi",
            request
        ).decodeAs<HistoryLog>()

        return insertedLog
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────

    private suspend fun fetchCount(statusFilter: String, timeRange: TimeRange): Int {
        return try {
            val response = SupabaseConfig.client.from("monitoring_inout")
                .select(columns = Columns.raw("count")) {
                    if (statusFilter != "ALL") filter { eq("status_io", statusFilter) }
                    if (timeRange.start != null && timeRange.end != null) {
                        filter {
                            and {
                                gte("created_at", timeRange.start)
                                lt("created_at", timeRange.end)
                            }
                        }
                    }
                }
            parseCountFromResponse(response.data)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCount error: ${e.message}")
            0
        }
    }

    private fun parseCountFromResponse(responseStr: String): Int {
        return when {
            responseStr.contains("[{\"count\":") -> {
                val startIndex = responseStr.indexOf(":") + 1
                val endIndex = responseStr.indexOf("}")
                if (startIndex > 0 && endIndex > startIndex)
                    responseStr.substring(startIndex, endIndex).toIntOrNull() ?: 0
                else 0
            }
            responseStr.matches(Regex("\\[\\d+]")) ->
                responseStr.removeSurrounding("[", "]").toIntOrNull() ?: 0
            else -> {
                "\\d+".toRegex().findAll(responseStr).lastOrNull()?.value?.toIntOrNull() ?: 0
            }
        }
    }

    fun getTimeRange(timeFilter: String): TimeRange {
        if (timeFilter == "ALL") return TimeRange()
        val now = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        return when (timeFilter) {
            "TODAY" -> TimeRange(
                start = "${now.format(formatter)}T00:00:00Z",
                end = "${now.plusDays(1).format(formatter)}T00:00:00Z"
            )
            "WEEK" -> TimeRange(
                start = "${now.minusDays(7).format(formatter)}T00:00:00Z",
                end = "${now.plusDays(1).format(formatter)}T00:00:00Z"
            )
            "MONTH" -> TimeRange(
                start = "${now.minusMonths(1).format(formatter)}T00:00:00Z",
                end = "${now.plusDays(1).format(formatter)}T00:00:00Z"
            )
            else -> TimeRange()
        }
    }

    suspend fun fetchPairedTransaction(laptopUuid: String, statusIo: String, createdAt: String): HistoryLog? {
        val targetStatus = if (statusIo == "IN") "OUT" else "IN"
        return try {
            val result = SupabaseConfig.client.from("monitoring_inout")
                .select(columns = HISTORY_COLUMNS) {
                    filter {
                        eq("laptop_uuid", laptopUuid)
                        eq("status_io", targetStatus)
                        if (statusIo == "IN") {
                            // Find OUT that happened AFTER this IN
                            gte("created_at", createdAt)
                        } else {
                            // Find IN that happened BEFORE this OUT
                            lte("created_at", createdAt)
                        }
                    }
                    if (statusIo == "IN") {
                        order("created_at", order = Order.ASCENDING)
                    } else {
                        order("created_at", order = Order.DESCENDING)
                    }
                    limit(1)
                }
                .decodeList<HistoryLog>()
            result.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
