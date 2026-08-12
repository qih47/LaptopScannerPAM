package com.dev.scanlaptop.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.SupabaseConfig
import com.dev.scanlaptop.data.repository.MonitoringRepository
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.github.jan.supabase.postgrest.from
import com.dev.scanlaptop.data.LaptopInfo
import com.dev.scanlaptop.data.PetugasInfo

/** Model sederhana untuk decode INSERT event dari Supabase Realtime.
 *  Event INSERT tidak menyertakan data relasi (join), hanya kolom dasar.
 */
@Serializable
private data class RealtimeInsertEvent(
    val uuid: String? = null,
    val status_io: String? = null,
    val laptop_uuid: String? = null,
    val petugas_npp: String? = null,
    val created_at: String? = null,
    val keterangan: String? = null,
    val lokasi: String? = null
)

/**
 * ForegroundService yang menjaga Supabase Realtime subscription tetap hidup
 * saat app ada di background. Mengirim push notification ke status bar
 * ketika ada transaksi baru dari petugas lain.
 *
 * Siklus hidup:
 * - Dimulai saat user login (dari DashboardScreen)
 * - Dihentikan saat user logout
 */
class RealtimeNotificationService : Service() {

    companion object {
        private const val TAG = "RealtimeNotifService"
        const val EXTRA_CURRENT_NPP = "current_user_npp"
        const val FOREGROUND_SERVICE_ID = 101
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentUserNpp: String = ""
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private val subscriptionMutex = kotlinx.coroutines.sync.Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        startForeground(
            FOREGROUND_SERVICE_ID,
            NotificationHelper.buildForegroundNotification(this)
        )
        Log.d(TAG, "RealtimeNotificationService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newNpp = intent?.getStringExtra(EXTRA_CURRENT_NPP) ?: ""
        
        serviceScope.launch {
            subscriptionMutex.withLock {
                if (currentUserNpp != newNpp || realtimeChannel == null) {
                    currentUserNpp = newNpp
                    Log.d(TAG, "Service started for NPP: $currentUserNpp")

                    try {
                        realtimeChannel?.unsubscribe()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unsubscribing previous channel: ${e.message}")
                    }
                    // Reset to allow new subscription
                    realtimeChannel = null
                    startRealtimeSubscription()
                } else {
                    Log.d(TAG, "Service already running for NPP: $currentUserNpp, ignoring start command")
                }
            }
        }
        return START_NOT_STICKY // Tidak me-restart service jika di-kill sistem untuk menghindari crash di Android 12+
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            try {
                realtimeChannel?.unsubscribe()
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing: ${e.message}")
            }
        }
        serviceScope.cancel()
        Log.d(TAG, "RealtimeNotificationService destroyed")
    }

    private suspend fun startRealtimeSubscription() {
        if (realtimeChannel != null) {
            Log.d(TAG, "Realtime channel already exists. Skipping new subscription.")
            return
        }

        serviceScope.launch {
            try {
                // Gunakan nama channel unik agar tidak bentrok dengan channel Dashboard
                val channelName = "service-notif-${System.currentTimeMillis()}"
                realtimeChannel = SupabaseConfig.client.channel(channelName)

                // Di Supabase 3.x: attach flow listener DULU, baru subscribe
                val flow = realtimeChannel!!
                    .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "monitoring_inout"
                    }

                // Subscribe dan tunggu sampai benar-benar terhubung (Supabase 3.x API)
                realtimeChannel!!.subscribe(blockUntilSubscribed = true)
                Log.d(TAG, "Realtime channel subscribed: $channelName")

                flow
                    .onEach { action ->
                        try {
                            // Gunakan RealtimeInsertEvent (bukan HistoryLog) karena
                            // event realtime INSERT tidak menyertakan data relasi (join)
                            val record = action.decodeRecord<RealtimeInsertEvent>()
                            val status = record.status_io ?: "UNKNOWN"
                            val petugasNpp = record.petugas_npp ?: "-"
                            val laptopUuid = record.laptop_uuid

                            Log.d(TAG, "Realtime INSERT received: status=$status npp=$petugasNpp uuid=$laptopUuid")

                            val isOwnTransaction = petugasNpp == currentUserNpp
                            if (!isOwnTransaction) {
                                Log.d(TAG, "Transaction from OTHER petugas $petugasNpp — sending notification")

                                var namaUser = "Pengguna Laptop"
                                var merkLaptop = "Laptop"
                                var namaPetugas = petugasNpp

                                // Fetch laptop info
                                if (laptopUuid != null) {
                                    try {
                                        val laptopList = SupabaseConfig.client.from("registrasi_laptop")
                                            .select { filter { eq("uuid", laptopUuid) } }
                                            .decodeList<LaptopInfo>()
                                        if (laptopList.isNotEmpty()) {
                                            namaUser = laptopList[0].nama_pengguna ?: "Pengguna Laptop"
                                            merkLaptop = laptopList[0].merk ?: "Laptop"
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error fetching laptop info: ${e.message}")
                                    }
                                }

                                // Fetch user info
                                if (petugasNpp != "-") {
                                    try {
                                        val userList = SupabaseConfig.client.from("users")
                                            .select { filter { eq("npp", petugasNpp) } }
                                            .decodeList<PetugasInfo>()
                                        if (userList.isNotEmpty()) {
                                            namaPetugas = userList[0].nama_lengkap ?: petugasNpp
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error fetching user info: ${e.message}")
                                    }
                                }

                                val notifId = NotificationHelper.TRANSACTION_NOTIFICATION_BASE_ID +
                                        (System.currentTimeMillis() % 1000).toInt()

                                NotificationHelper.showTransactionNotification(
                                    context = applicationContext,
                                    namaUser = namaUser,
                                    status = status,
                                    petugasNpp = petugasNpp,
                                    namaPetugas = namaPetugas,
                                    merkLaptop = merkLaptop,
                                    notifId = notifId
                                )
                            } else {
                                Log.d(TAG, "Own transaction, skipping notification")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing realtime record: ${e.message}")
                        }
                    }
                    .launchIn(serviceScope)

                Log.d(TAG, "Realtime flow listener attached")

            } catch (e: Exception) {
                Log.e(TAG, "Realtime subscription error: ${e.message}")
                // Retry setelah 30 detik jika gagal
                delay(30_000)
                startRealtimeSubscription()
            }
        }
    }
}
