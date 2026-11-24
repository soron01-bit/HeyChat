package com.example.heychat

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.service.FcmTokenManager
import com.example.heychat.service.PresenceManager
import com.example.heychat.service.SessionManager
import io.github.jan.supabase.gotrue.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HeyChatApplication : Application(), ImageLoaderFactory {

    private companion object {
        const val TAG = "HeyChat_Auth"
    }

    private val authScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val authRefreshMutex = Mutex()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeyChatApplication onCreate -> SupabaseClient initialized as Application singleton")
        supabaseClient

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d("HeyChat_Presence", "Application Process ON_START -> Joining global presence")
                PresenceManager.onAppStart()

                refreshSessionOnProcessStart("Process ON_START")
            }

            override fun onStop(owner: LifecycleOwner) {
                Log.d("HeyChat_Presence", "Application Process ON_STOP -> Leaving global presence")
                PresenceManager.onAppStop()
            }
        })
    }

    fun refreshSessionOnProcessStart(source: String) {
        authScope.launch {
            authRefreshMutex.withLock {
                val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                Log.d(TAG, "[$time] [$source] Explicit session refresh check STARTED")

                val sessionBefore = runCatching { supabaseClient.auth.currentSessionOrNull() }.getOrNull()
                val userBefore = runCatching { supabaseClient.auth.currentUserOrNull() }.getOrNull() ?: sessionBefore?.user

                if (userBefore == null && sessionBefore == null) {
                    Log.d(TAG, "[$time] [$source] No session found locally. User is unauthenticated.")
                    return@launch
                }

                Log.d(TAG, "[$time] [$source] Active local user '${userBefore?.id}' found. Calling supabaseClient.auth.refreshCurrentSession()...")

                runCatching {
                    supabaseClient.auth.refreshCurrentSession()
                }.onSuccess {
                    val newTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                    Log.d(TAG, "[$newTime] [$source] Refresh SUCCESS! Token refreshed for user '${userBefore?.id}'")
                    FcmTokenManager.syncFcmTokenWithSupabase()
                }.onFailure { throwable ->
                    val newTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                    val errorMsg = throwable.message ?: ""
                    val isExplicitServerRejection = errorMsg.contains("invalid_grant", ignoreCase = true) ||
                        errorMsg.contains("refresh_token_not_found", ignoreCase = true) ||
                        errorMsg.contains("invalid refresh token", ignoreCase = true) ||
                        errorMsg.contains("Already revoked", ignoreCase = true)

                    if (isExplicitServerRejection) {
                        Log.e(TAG, "[$newTime] [$source] SERVER EXPLICITLY REJECTED REFRESH TOKEN ($errorMsg)")
                        SessionManager.notifySessionExpired()
                    } else {
                        Log.w(TAG, "[$newTime] [$source] Network or transient failure ($errorMsg). User STAYS LOGGED IN.")
                    }
                }
            }
        }
    }
}
