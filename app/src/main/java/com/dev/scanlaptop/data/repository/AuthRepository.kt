package com.dev.scanlaptop.data.repository

import android.util.Log
import com.dev.scanlaptop.data.SupabaseConfig
import com.dev.scanlaptop.data.UserData
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt

/**
 * Repository untuk semua operasi autentikasi.
 * Satu-satunya yang tahu cara query tabel 'users' ke Supabase.
 */
class AuthRepository {

    /**
     * Login dengan NPP dan password.
     * @return [UserData] jika credentials valid, null jika tidak ditemukan.
     * @throws Exception jika ada error koneksi/server.
     */
    suspend fun login(npp: String, password: String): UserData? {
        return try {
            // Ambil user berdasarkan NPP saja
            val user = SupabaseConfig.client.from("users")
                .select {
                    filter {
                        eq("npp", npp)
                    }
                }
                .decodeSingleOrNull<UserData>()

            if (user != null && user.password != null) {
                if (user.password.startsWith("$2a$")) {
                    // Password sudah di-hash pakai bcrypt
                    if (BCrypt.checkpw(password, user.password)) {
                        return user
                    }
                } else {
                    // Password masih plaintext, cek kesamaan
                    if (password == user.password) {
                        // AUTO MIGRATE: Hash password dan update ke DB
                        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
                        updatePassword(npp, hashed)
                        return user
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login error: ${e.message}")
            throw e
        }
    }

    @Serializable
    private data class PasswordUpdate(val password: String)

    @Serializable
    private data class FotoUpdate(val foto_profil: String)

    /**
     * Update password ke Supabase.
     */
    suspend fun updatePassword(npp: String, newPasswordHashed: String) {
        try {
            SupabaseConfig.client.from("users")
                .update(PasswordUpdate(newPasswordHashed)) {
                    filter { eq("npp", npp) }
                }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Update password error: ${e.message}")
        }
    }

    /**
     * Update foto profil ke Supabase (berupa base64 atau URL).
     */
    suspend fun updateFotoProfil(npp: String, fotoBase64: String) {
        try {
            SupabaseConfig.client.from("users")
                .update(FotoUpdate(fotoBase64)) {
                    filter { eq("npp", npp) }
                }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Update foto error: ${e.message}")
            throw e
        }
    }

    @Serializable
    private data class VersionUpdate(val app_version_code: Int)

    /**
     * Update app_version_code ke Supabase agar RLS mendeteksi user memakai versi terbaru.
     */
    suspend fun updateAppVersionCode(npp: String, versionCode: Int) {
        try {
            SupabaseConfig.client.from("users")
                .update(VersionUpdate(versionCode)) {
                    filter { eq("npp", npp) }
                }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Update version code error: ${e.message}")
        }
    }
}
