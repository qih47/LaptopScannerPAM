package com.dev.scanlaptop.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dev.scanlaptop.data.SupabaseConfig
import com.dev.scanlaptop.data.local.AppDatabase
import com.dev.scanlaptop.data.model.ProcessTransactionRequest
import com.dev.scanlaptop.data.model.RpcDeviceDetail
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.Json

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val queueDao = database.scanQueueDao()
        
        val offlineItems = queueDao.getAllQueuesSync()
        
        if (offlineItems.isEmpty()) {
            return Result.success()
        }

        Log.d("SyncWorker", "Memulai sinkronisasi \${offlineItems.size} data offline...")

        var allSuccess = true

        for (item in offlineItems) {
            try {
                // Decode JSON ke list RpcDeviceDetail
                val details = Json.decodeFromString<List<RpcDeviceDetail>>(item.perangkatDetailsJson)
                
                val request = ProcessTransactionRequest(
                    p_status_io = item.newStatus,
                    p_laptop_uuid = item.laptopUuid,
                    p_petugas_npp = item.nppPetugas,
                    p_keterangan = item.keterangan + " (Auto-Sync)",
                    p_lokasi = item.lokasi,
                    p_perangkat_details = details
                )

                // Push ke Supabase
                SupabaseConfig.client.postgrest.rpc("proses_transaksi", request)
                
                // Jika sukses (tidak throw exception), hapus dari antrean lokal
                queueDao.deleteQueue(item.id)
                Log.d("SyncWorker", "Berhasil sync item \${item.id}")
            } catch (e: Exception) {
                Log.e("SyncWorker", "Gagal sync item \${item.id}: \${e.message}")
                allSuccess = false
            }
        }

        return if (allSuccess) Result.success() else Result.retry()
    }
}
