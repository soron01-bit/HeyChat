package com.example.heychat.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Immutable
@Serializable
data class Profile(
    @SerialName("id") val id: String = "",
    @SerialName("email") val email: String? = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("about") val about: String? = "Hey there! I am using Hey Chat 💬",
    @SerialName("is_online") val isOnline: Boolean? = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("show_last_seen") val showLastSeen: Boolean? = true,
    @SerialName("disappearing_messages_duration") val disappearingMessagesDuration: String? = "OFF",
    @SerialName("two_step_enabled") val twoStepEnabled: Boolean? = false,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = "",
    @SerialName("created_at") val createdAt: String? = null
) {
    val handle: String
        get() {
            val clean = username?.replace("\"", "")?.replace("'", "")?.replace("@", "")?.trim()?.lowercase()
            return if (!clean.isNullOrBlank()) "@$clean" else ""
        }

    val displayName: String
        get() {
            val cleanName = fullName?.replace("\"", "")?.replace("'", "")?.trim()
            if (!cleanName.isNullOrBlank() && !cleanName.contains("@") && !cleanName.equals("User", ignoreCase = true)) {
                return cleanName
            }

            val cleanHandle = username?.replace("\"", "")?.replace("'", "")?.replace("@", "")?.trim()
            if (!cleanHandle.isNullOrBlank()) {
                return "@$cleanHandle"
            }

            if (!cleanName.isNullOrBlank() && cleanName.contains("@")) {
                val prefix = cleanName.substringBefore("@").replace(".", " ").replace("_", " ").trim()
                if (prefix.isNotBlank()) {
                    return prefix.split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
                }
            }

            val emailPrefix = email?.substringBefore("@")?.takeIf { it.isNotBlank() }?.trim()
            if (!emailPrefix.isNullOrBlank()) {
                return emailPrefix.replace(".", " ").replace("_", " ").split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
            }

            return "User"
        }
}

@Serializable
data class ParticipantRow(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String
)
