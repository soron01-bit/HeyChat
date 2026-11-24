package com.example.heychat.service

import android.util.Log
import com.example.heychat.data.remote.supabaseClient
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object FcmTokenManager {

    private const val TAG = "HeyChat_FcmToken"

    fun syncFcmTokenWithSupabase() {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            if (!token.isNullOrBlank()) {
                updateTokenInDatabase(currentUserId, token)
            }
        }
    }

    fun updateTokenInDatabase(userId: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val updateData = buildJsonObject {
                    put("fcm_token", token)
                }
                supabaseClient.from("profiles").update(updateData) {
                    filter {
                        eq("id", userId)
                    }
                }
                Log.d(TAG, "FCM token updated successfully in Supabase for user: $userId")
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to update FCM token in Supabase: ${throwable.message}", throwable)
            }
        }
    }
}
