package com.example.heychat.service

import android.util.Log
import com.example.heychat.data.remote.supabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PresenceManager {

    private const val TAG = "HeyChat_Presence"
    private val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    
    private val presenceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var presenceChannel: RealtimeChannel? = null
    @Volatile
    private var trackedUserId: String? = null

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()

    init {
        presenceScope.launch {
            runCatching {
                supabaseClient.auth.sessionStatus.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        Log.d(TAG, "[${timeLog()}] Auth session status -> Authenticated (${status.session.user?.id}). Triggering presence onAppStart()")
                        onAppStart()
                    }
                }
            }
        }
    }

    private fun timeLog(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
    }

    @OptIn(SupabaseExperimental::class)
    @Synchronized
    fun onAppStart() {
        val user = supabaseClient.auth.currentUserOrNull() ?: supabaseClient.auth.currentSessionOrNull()?.user
        val currentUserId = user?.id?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase()

        Log.d(TAG, "[${timeLog()}] onAppStart called, currentUser = ${if (currentUserId.isNullOrBlank()) "null" else "'$currentUserId'"}")

        if (currentUserId.isNullOrBlank()) {
            Log.d(TAG, "[${timeLog()}] Process ON_START -> No authenticated user session found")
            return
        }

        val isAlreadySubscribed = presenceChannel != null && presenceChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
        if (trackedUserId == currentUserId && isAlreadySubscribed) {
            Log.d(TAG, "[${timeLog()}] Process ON_START -> Channel already active and SUBSCRIBED for '$currentUserId'")
            return
        }

        trackedUserId = currentUserId

        presenceScope.launch {
            runCatching {
                val params = buildJsonObject {
                    put("p_user_id", currentUserId)
                    put("p_is_online", true)
                }
                supabaseClient.postgrest.rpc("update_user_online_status", params)
                Log.d(TAG, "[${timeLog()}] update_user_online_status -> set is_online=true in DB for '$currentUserId'")
            }

            runCatching {
                val session = supabaseClient.auth.currentSessionOrNull()
                if (session != null) {
                    runCatching { supabaseClient.realtime.connect() }
                }

                if (presenceChannel == null || presenceChannel?.status?.value != RealtimeChannel.Status.SUBSCRIBED) {
                    Log.d(TAG, "[${timeLog()}] Channel CREATED/RE-CREATED: 'global_presence'")
                    val channel = supabaseClient.channel("global_presence")
                    presenceChannel = channel

                    // 1. Observe presenceDataFlow snapshot for ALL active online users (Normalized to lowercase)
                    launch {
                        channel.presenceDataFlow<JsonObject>().collect { userList ->
                            val activeUserIds = mutableSetOf<String>()
                            userList.forEach { jsonObject ->
                                uuidRegex.findAll(jsonObject.toString()).forEach { match ->
                                    activeUserIds.add(match.value.lowercase())
                                }
                            }
                            Log.d(TAG, "[${timeLog()}] Presence snapshot updated -> Active online user_ids: $activeUserIds")
                            _onlineUsers.value = activeUserIds
                        }
                    }

                    // 2. Observe presenceChangeFlow for real-time joins/leaves delta events (Normalized to lowercase)
                    launch {
                        channel.presenceChangeFlow().collect { action ->
                            val currentSet = _onlineUsers.value.toMutableSet()

                            action.joins.values.forEach { element ->
                                uuidRegex.findAll(element.toString()).forEach { match ->
                                    currentSet.add(match.value.lowercase())
                                }
                            }

                            action.leaves.values.forEach { element ->
                                uuidRegex.findAll(element.toString()).forEach { match ->
                                    currentSet.remove(match.value.lowercase())
                                }
                            }

                            Log.d(TAG, "[${timeLog()}] Presence delta change -> Active online user_ids: $currentSet")
                            _onlineUsers.value = currentSet
                        }
                    }

                    // 3. Observe channel status & launch periodic 20-second heartbeat loop
                    launch {
                        channel.status.collect { status ->
                            Log.d(TAG, "[${timeLog()}] Channel STATUS CHANGED -> $status")
                            if (status == RealtimeChannel.Status.SUBSCRIBED) {
                                Log.d(TAG, "[${timeLog()}] track() CALLED for user_id: '$currentUserId'")
                                channel.track(buildJsonObject {
                                    put("user_id", currentUserId)
                                    put("online_at", System.currentTimeMillis().toString())
                                })
                            }
                        }
                    }

                    // 4. Periodic Presence Heartbeat (re-tracks presence every 20s to prevent server timeout eviction!)
                    launch {
                        while (isActive && presenceChannel == channel) {
                            delay(20000L)
                            if (channel.status.value == RealtimeChannel.Status.SUBSCRIBED) {
                                runCatching {
                                    channel.track(buildJsonObject {
                                        put("user_id", currentUserId)
                                        put("online_at", System.currentTimeMillis().toString())
                                    })
                                    Log.d(TAG, "[${timeLog()}] Presence heartbeat track() refreshed for user_id: '$currentUserId'")
                                }.onFailure { throwable ->
                                    Log.w(TAG, "[${timeLog()}] Presence heartbeat track() failed: ${throwable.message}")
                                }
                            }
                        }
                    }

                    // Subscribe to Channel
                    channel.subscribe()
                    Log.d(TAG, "[${timeLog()}] Subscribed to 'global_presence' channel successfully")
                }
            }.onFailure { throwable ->
                Log.e(TAG, "[${timeLog()}] Failed to join presence channel: ${throwable.message}", throwable)
            }
        }
    }

    @Synchronized
    fun onAppStop() {
        val lastUser = trackedUserId
        Log.d(TAG, "[${timeLog()}] Process ON_STOP -> App backgrounded for user '$lastUser'")

        trackedUserId = null
        val channelToClose = presenceChannel
        presenceChannel = null

        presenceScope.launch {
            if (!lastUser.isNullOrBlank()) {
                runCatching {
                    val params = buildJsonObject {
                        put("p_user_id", lastUser)
                        put("p_is_online", false)
                    }
                    supabaseClient.postgrest.rpc("update_user_online_status", params)
                    Log.d(TAG, "[${timeLog()}] update_user_online_status -> set is_online=false in DB for '$lastUser'")
                }
            }

            runCatching {
                channelToClose?.untrack()
                if (channelToClose != null) {
                    supabaseClient.realtime.removeChannel(channelToClose)
                }
                _onlineUsers.value = emptySet()
                Log.d(TAG, "[${timeLog()}] Process ON_STOP -> Channel REMOVED/UNSUBSCRIBED for user '$lastUser'")
            }.onFailure { throwable ->
                Log.e(TAG, "[${timeLog()}] Error leaving presence channel: ${throwable.message}", throwable)
            }
        }
    }
}
