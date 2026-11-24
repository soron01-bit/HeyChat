package com.example.heychat.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Immutable
@Serializable
data class UserConversation(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("other_user_id") val otherUserId: String,
    @SerialName("other_user_name") val otherUserName: String = "User",
    @SerialName("other_user_avatar") val otherUserAvatar: String? = null,
    @SerialName("last_message_id") val lastMessageId: String? = null,
    @SerialName("last_message_sender_id") val lastMessageSenderId: String? = null,
    @SerialName("last_message_content") val lastMessageContent: String? = null,
    @SerialName("last_message_time") val lastMessageTime: String? = null,
    @SerialName("last_message_status") val lastMessageStatus: String? = "sent",
    @SerialName("unread_count") val unreadCount: Long = 0L,
    @SerialName("is_muted") val isMuted: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false
) {
    val cleanOtherUserName: String
        get() {
            val clean = otherUserName.replace("\"", "").replace("'", "").trim()
            if (clean.isBlank() || clean.equals("User", ignoreCase = true)) return "User"

            if (clean.startsWith("@")) {
                val handleClean = clean.removePrefix("@").trim()
                if (handleClean.isNotBlank()) return "@$handleClean"
            }

            if (!clean.contains("@")) return clean

            val prefix = clean.substringBefore("@").trim()
            if (prefix.isNotBlank()) {
                return prefix.replace(".", " ").replace("_", " ").split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
            }

            return "User"
        }

    val lastMessageText: String
        get() {
            val content = lastMessageContent?.trim() ?: ""
            return when {
                content.isBlank() -> "📷 Photo"
                content.lowercase() == "photo" || content.lowercase() == "📷 photo" -> "📷 Photo"
                else -> content
            }
        }
}
