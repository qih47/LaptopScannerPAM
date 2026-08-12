package com.dev.scanlaptop.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Inisialisasi DataStore (Singleton)
val Context.dataStore by preferencesDataStore(name = "user_session")

class SessionManager(private val context: Context) {

    companion object {
        private val NPP_KEY = stringPreferencesKey("npp")
        private val NAMA_KEY = stringPreferencesKey("nama_lengkap")
        private val ROLE_KEY = stringPreferencesKey("role")
        private val FOTO_KEY = stringPreferencesKey("foto_profil")
        private val BIOMETRIC_KEY = booleanPreferencesKey("biometric_enabled")
        private val PUSH_NOTIF_KEY = booleanPreferencesKey("push_notif_enabled")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        private val LAST_SEEN_VERSION_KEY = intPreferencesKey("last_seen_version_code")
    }

    // Fungsi Simpan Session (Dipanggil pas Login Sukses)
    suspend fun saveSession(user: UserData) {
        context.dataStore.edit { pref ->
            pref[NPP_KEY] = user.npp
            pref[NAMA_KEY] = user.nama_lengkap
            pref[ROLE_KEY] = user.role
            user.foto_profil?.let { pref[FOTO_KEY] = it }
            pref[IS_LOGGED_IN_KEY] = true
        }
    }

    // Fungsi update foto ke DataStore (ketika ganti foto)
    suspend fun saveFotoProfil(fotoBase64: String) {
        context.dataStore.edit { pref ->
            pref[FOTO_KEY] = fotoBase64
        }
    }

    // Fungsi Ambil Data Session (Buat ngecek di SplashScreen) - Hanya yang beneran lagi Logged In
    val userDataFlow: Flow<UserData?> = context.dataStore.data.map { pref ->
        val isLoggedIn = pref[IS_LOGGED_IN_KEY] ?: false
        if (!isLoggedIn) return@map null

        val npp = pref[NPP_KEY]
        val nama = pref[NAMA_KEY]
        val role = pref[ROLE_KEY]
        val foto = pref[FOTO_KEY]

        if (npp != null && nama != null && role != null) {
            UserData(npp, nama, role, null, foto)
        } else null
    }

    // Ambil data session tersimpan untuk Biometrik (mengabaikan status login saat ini)
    val savedUserFlow: Flow<UserData?> = context.dataStore.data.map { pref ->
        val npp = pref[NPP_KEY]
        val nama = pref[NAMA_KEY]
        val role = pref[ROLE_KEY]
        val foto = pref[FOTO_KEY]

        if (npp != null && nama != null && role != null) {
            UserData(npp, nama, role, null, foto)
        } else null
    }

    // Ambil preferensi biometric
    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[BIOMETRIC_KEY] ?: false
    }

    // Set preferensi biometric
    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { pref ->
            pref[BIOMETRIC_KEY] = enabled
        }
    }

    // Ambil preferensi push notification
    val pushNotifEnabledFlow: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[PUSH_NOTIF_KEY] ?: false
    }

    // Set preferensi push notification
    suspend fun setPushNotifEnabled(enabled: Boolean) {
        context.dataStore.edit { pref ->
            pref[PUSH_NOTIF_KEY] = enabled
        }
    }

    // Fungsi Logout (Cuma ubah status login, data biometrik tetap aman)
    suspend fun clearSession() {
        context.dataStore.edit { pref -> 
            pref[IS_LOGGED_IN_KEY] = false 
        }
    }

    // Ambil preferensi last seen version code
    val lastSeenVersionCodeFlow: Flow<Int> = context.dataStore.data.map { pref ->
        pref[LAST_SEEN_VERSION_KEY] ?: 0
    }

    // Set preferensi last seen version code
    suspend fun setLastSeenVersionCode(versionCode: Int) {
        context.dataStore.edit { pref ->
            pref[LAST_SEEN_VERSION_KEY] = versionCode
        }
    }
}