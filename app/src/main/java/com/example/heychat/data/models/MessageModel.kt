package com.example.heychat.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class MessageStatus {
    @SerialName("sent") SENT,
    @SerialName("delivered") DELIVERED,
    @SerialName("seen") SEEN
}

@Immutable
@Serializable
data class MessageModel(
    @SerialName("id") val id: String? = null,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("content") val content: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("status") val status: MessageStatus = MessageStatus.SENT,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
    @SerialName("reactions_json") val reactionsJson: String? = "{}",
    @SerialName("created_at") val createdAt: String? = null
) {
    val text: String get() = if (isDeleted) "This message was deleted" else content

    val reactionsList: List<MessageReactionModel>
        get() {
            if (reactionsJson.isNullOrBlank() || reactionsJson == "{}" || reactionsJson == "null") return emptyList()
            return runCatching {
                val jsonMap = Json.decodeFromString<Map<String, String>>(reactionsJson ?: "{}")
                jsonMap.map { (uId, emojiStr) ->
                    MessageReactionModel(messageId = id ?: "", userId = uId, emoji = emojiStr)
                }
            }.getOrDefault(emptyList())
        }
}

@Immutable
@Serializable
data class MessageInsert(
    @SerialName("id") val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("content") val content: String,
    @SerialName("status") val status: String = "sent",
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null
)
