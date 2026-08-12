package com.dev.scanlaptop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dev.scanlaptop.MainActivity
import com.dev.scanlaptop.R

/**
 * Helper untuk membuat dan menampilkan notifikasi.
 * Mengelola notification channels dan building notification.
 */
object NotificationHelper {

    private const val FOREGROUND_CHANNEL_ID = "scan_laptop_service"
    private const val TRANSACTION_CHANNEL_ID = "scan_laptop_transactions"
    private const val OVERDUE_CHANNEL_ID = "scan_laptop_overdue"
    
    const val TRANSACTION_NOTIFICATION_BASE_ID = 2000
    const val OVERDUE_NOTIFICATION_ID = 3000
    const val OVERDUE_IN_NOTIFICATION_ID = 3001
    const val OVERDUE_OUT_NOTIFICATION_ID = 3002

    /**
     * Daftarkan NotificationChannel. Harus dipanggil saat app mulai atau service dibuat.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Realtime Service Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status background service (diam)"
            }

            val transactionChannel = NotificationChannel(
                TRANSACTION_CHANNEL_ID,
                "Transaksi Realtime",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi suara/pop-up saat ada petugas lain scan laptop"
                enableVibration(true)
            }
            
            val overdueChannel = NotificationChannel(
                OVERDUE_CHANNEL_ID,
                "Peringatan Mengendap",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan laptop yang mengendap melebihi batas waktu"
                enableVibration(true)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(transactionChannel)
            manager.createNotificationChannel(overdueChannel)
        }
    }

    /**
     * Build persistent notification untuk ForegroundService.
     */
    fun buildForegroundNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Monitoring Laptop")
            .setContentText("Menerima notifikasi transaksi realtime")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Sembunyikan dari lock screen
            .build()
    }

    /**
     * Tampilkan notifikasi transaksi baru di status bar.
     * @param namaUser nama pemilik laptop
     * @param status "IN" atau "OUT"
     * @param petugasNpp NPP petugas yang melakukan transaksi
     * @param notifId ID unik untuk notifikasi (agar tidak tertimpa)
     */
    fun showTransactionNotification(
        context: Context,
        namaUser: String,
        status: String,
        petugasNpp: String,
        namaPetugas: String = petugasNpp,
        merkLaptop: String = "Laptop",
        notifId: Int = TRANSACTION_NOTIFICATION_BASE_ID
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", "history")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusLabel = if (status == "IN") "MASUK" else "KELUAR"
        val statusText = if (status == "IN") "masuk" else "keluar"
        val emoji = if (status == "IN") "🟢" else "🔴"

        val notification = NotificationCompat.Builder(context, TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$emoji Transaksi $statusLabel")
            .setContentText("$namaPetugas memindai $statusText device milik $namaUser")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Petugas $namaPetugas (NIP $petugasNpp) telah memindai $statusText device milik $namaUser.")
                    .setBigContentTitle("$emoji Transaksi $statusLabel")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("GROUP_TRANSACTIONS")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(notifId, notification)
    }

    /**
     * Tampilkan notifikasi jika ada perangkat yang mengendap (overdue).
     */
    fun showOverdueNotification(
        context: Context,
        title: String,
        message: String,
        bigText: String,
        notifId: Int = OVERDUE_NOTIFICATION_ID
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", "overdue") // Arahkan ke tab overdue jika memungkinkan
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, OVERDUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ganti dengan ikon aplikasi yang sesuai
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setBigContentTitle(title)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("GROUP_OVERDUE")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(notifId, notification)
    }
}
