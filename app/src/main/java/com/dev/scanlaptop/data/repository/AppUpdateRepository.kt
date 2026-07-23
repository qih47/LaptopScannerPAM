package com.dev.scanlaptop.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class AppVersion(
    val id: Int = 0,
    val version_code: Int,
    val version_name: String,
    val release_notes: String? = null,
    val download_url: String,
    val is_force_update: Boolean = false,
    val created_at: String? = null
)

class AppUpdateRepository(private val supabaseClient: SupabaseClient) {

    suspend fun getLatestAppVersion(): AppVersion? {
        return withContext(Dispatchers.IO) {
            try {
                val response = supabaseClient.postgrest["app_versions"]
                    .select {
                        limit(1)
                        order("version_code", order = Order.DESCENDING)
                    }
                    .decodeList<AppVersion>()
                
                response.firstOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
