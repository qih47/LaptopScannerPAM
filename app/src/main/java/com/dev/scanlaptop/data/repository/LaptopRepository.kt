package com.dev.scanlaptop.data.repository

import android.util.Log
import com.dev.scanlaptop.data.SupabaseConfig
import com.dev.scanlaptop.data.LaptopDetail
import com.dev.scanlaptop.data.model.PerangkatStatusUpdate
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Repository untuk semua operasi yang berkaitan dengan data laptop.
 * Satu-satunya yang tahu cara query tabel 'registrasi_laptop' dan 'daftar_perangkat'.
 */
class LaptopRepository {

    /**
     * Ambil detail laptop berdasarkan QR code (dari scan kamera).
     * @throws Exception jika laptop tidak ditemukan atau error koneksi.
     */
    suspend fun getLaptopByQr(codeQr: String): LaptopDetail {
        return SupabaseConfig.client.from("registrasi_laptop")
            .select(columns = Columns.raw("*, daftar_perangkat(*)")) {
                filter { eq("code_qr", codeQr) }
            }
            .decodeSingle<LaptopDetail>()
    }

    /**
     * Ambil detail laptop berdasarkan UUID (dari klik item riwayat).
     * @throws Exception jika laptop tidak ditemukan atau error koneksi.
     */
    suspend fun getLaptopByUuid(uuid: String): LaptopDetail {
        return SupabaseConfig.client.from("registrasi_laptop")
            .select(columns = Columns.raw("*, daftar_perangkat(*)")) {
                filter { eq("uuid", uuid) }
            }
            .decodeSingle<LaptopDetail>()
    }

    /**
     * Cek apakah QR code terdaftar di database.
     * @return true jika terdaftar, false jika tidak.
     */
    suspend fun validateQr(codeQr: String): Boolean {
        return try {
            val response = SupabaseConfig.client.from("registrasi_laptop")
                .select { filter { eq("code_qr", codeQr) } }
            response.data != "[]"
        } catch (e: Exception) {
            Log.e("LaptopRepository", "QR validation error: ${e.message}")
            false
        }
    }

    /**
     * Mengambil status_terakhir paling aktual (real-time) dari Supabase
     * untuk mencegah race condition double IN/OUT.
     */
    suspend fun getDeviceLatestStatus(noSeri: String): String? {
        return try {
            val response = SupabaseConfig.client.from("daftar_perangkat")
                .select(columns = Columns.raw("status_terakhir")) {
                    filter { eq("no_seri", noSeri) }
                }
                .decodeSingleOrNull<com.dev.scanlaptop.data.PerangkatData>()
            response?.status_terakhir
        } catch (e: Exception) {
            Log.e("LaptopRepository", "Get device status error for $noSeri: ${e.message}")
            null
        }
    }

    /**
     * Update status_terakhir perangkat (IN/OUT) di tabel daftar_perangkat.
     */
    suspend fun updateDeviceStatus(noSeri: String, newStatus: String) {
        try {
            SupabaseConfig.client.from("daftar_perangkat")
                .update(PerangkatStatusUpdate(status_terakhir = newStatus)) {
                    filter { eq("no_seri", noSeri) }
                }
        } catch (e: Exception) {
            Log.e("LaptopRepository", "Update device status error for $noSeri: ${e.message}")
            throw e
        }
    }
}
