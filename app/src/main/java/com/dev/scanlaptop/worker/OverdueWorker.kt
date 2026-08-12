package com.dev.scanlaptop.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dev.scanlaptop.data.repository.OverdueRepository
import com.dev.scanlaptop.service.NotificationHelper
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OverdueWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "OverdueWorker"
        const val WORK_NAME = "OverdueNotificationWork"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Mengecek perangkat overdue...")
        
        try {
            val repository = OverdueRepository()
            // Mengambil semua perangkat yang mengendap >= 8 jam (atau sesuai threshold)
            val overdueItems = repository.fetchOverdueItems(thresholdHours = 8)
            
            if (overdueItems.isEmpty()) {
                Log.d(TAG, "Tidak ada perangkat overdue.")
                return Result.success()
            }
            
            // Pisahkan berdasarkan jenis overdue:
            // "IN" = belum keluar (tamu/pribadi)
            // "OUT" = belum kembali (aset perusahaan)
            val overdueIn = overdueItems.filter { it.type == "IN" }
            val overdueOut = overdueItems.filter { it.type == "OUT" }

            fun formatTime(timeStrRaw: String?): String {
                return timeStrRaw?.let {
                    try {
                        val zdt = ZonedDateTime.parse(it)
                        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                        zdt.format(formatter)
                    } catch (e: Exception) {
                        it.take(16).replace("T", " ")
                    }
                } ?: "waktu tidak diketahui"
            }

            val total = overdueItems.size

            if (total == 1) {
                // 1. Notifikasi tunggal jika hanya 1 perangkat yang mengendap
                val item = overdueItems.first()
                val statusText = if (item.type == "IN") "belum keluar" else "belum kembali"
                val locationText = if (item.type == "IN") "di dalam area" else "di luar area"
                val merk = item.perangkatList.firstOrNull() ?: "Laptop"
                val timeStr = formatTime(item.lastInTime)

                val title = "⚠️ Peringatan Mengendap"
                val message = "$merk milik ${item.namaUser} $statusText."
                val bigText = "Perangkat $merk milik ${item.namaUser} terpantau mengendap $locationText sejak $timeStr. Harap lakukan pengecekan."

                NotificationHelper.showOverdueNotification(
                    context = appContext,
                    title = title,
                    message = message,
                    bigText = bigText,
                    notifId = NotificationHelper.OVERDUE_NOTIFICATION_ID
                )
            } else {
                // 2. Notifikasi kolektif (1 notif merangkum baik IN maupun OUT)
                val title = "⚠️ Peringatan Mengendap"
                val message = "Terdapat $total perangkat mengendap."

                val sb = StringBuilder()
                sb.append("Terdapat $total perangkat yang terdeteksi mengendap melebihi batas waktu operasional, dengan rincian:\n")
                if (overdueIn.isNotEmpty()) {
                    sb.append("• ${overdueIn.size} perangkat di dalam area (belum keluar)\n")
                }
                if (overdueOut.isNotEmpty()) {
                    sb.append("• ${overdueOut.size} perangkat di luar area (belum kembali)\n")
                }
                sb.append("\nMohon segera lakukan pengecekan lebih lanjut melalui menu Mengendap.")

                NotificationHelper.showOverdueNotification(
                    context = appContext,
                    title = title,
                    message = message,
                    bigText = sb.toString(),
                    notifId = NotificationHelper.OVERDUE_NOTIFICATION_ID
                )
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengecek overdue: ${e.message}")
            return Result.retry()
        }
    }
}
