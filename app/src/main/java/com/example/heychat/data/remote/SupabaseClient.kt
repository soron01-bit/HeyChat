package com.example.heychat.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

object SupabaseConfig {
    const val SUPABASE_URL = "https://qlzlwpiyxdqupvavtvlr.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFsemx3cGl5eGRxdXB2YXZ0dmxyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAyNTA3NDMsImV4cCI6MjEwNTgyNjc0M30.sPOD_TuffiwIFIuZxBrp58IBjWgTsbzNvewi3mBK9SI"
}

val supabaseClient: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = SupabaseConfig.SUPABASE_URL,
        supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
    ) {
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
        install(Auth) {
            autoLoadFromStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}
