package com.dev.scanlaptop.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseConfig {
    val client = createSupabaseClient(
        supabaseUrl = "https://mbixnhlvccvbgyopahtv.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1iaXhuaGx2Y2N2Ymd5b3BhaHR2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE5ODkwMjAsImV4cCI6MjA4NzU2NTAyMH0.ruYKq6du-ybFwqpRns3nHFXIbG4Ggw-WNsJLdw1xaFw"
    ) {
        install(Postgrest)
        install(Realtime)
    }
}