package com.example.heychat.service

import android.util.Log
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.gotrue.auth

class HeyChatMessagingService : FirebaseMessagingService() {

    private companion object {
        const val TAG = "HeyChat_FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
        if (!currentUserId.isNullOrBlank()) {
            FcmTokenManager.updateTokenInDatabase(currentUserId, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        val conversationId = data["conversation_id"] ?: data["conversationId"] ?: ""
        val title = data["title"] ?: data["sender_name"] ?: remoteMessage.notification?.title ?: "Hey Chat"
        val body = data["body"] ?: data["message"] ?: data["content"] ?: remoteMessage.notification?.body ?: "New message received"

        // Suppress system notification if user is actively viewing this conversation
        if (!NotificationHelper.activeConversationId.isNullOrBlank() && 
            NotificationHelper.activeConversationId == conversationId) {
            Log.d(TAG, "Notification suppressed because conversation $conversationId is currently open in foreground")
            return
        }

        NotificationHelper.showMessageNotification(
            context = applicationContext,
            title = title,
            body = body,
            conversationId = conversationId
        )
    }
}
