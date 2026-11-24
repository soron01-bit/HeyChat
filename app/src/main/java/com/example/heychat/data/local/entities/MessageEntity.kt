package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.heychat.data.models.MessageModel
import com.example.heychat.data.models.MessageStatus
import java.io.File

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "media_url") val mediaUrl: String? = null,
    @ColumnInfo(name = "local_media_path") val localMediaPath: String? = null,
    @ColumnInfo(name = "status") val status: String = "sent",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "is_hidden_for_me") val isHiddenForMe: Boolean = false,
    @ColumnInfo(name = "reply_to_message_id") val replyToMessageId: String? = null,
    @ColumnInfo(name = "reactions_json") val reactionsJson: String? = "{}",
    @ColumnInfo(name = "created_at") val createdAt: String? = null
) {
    fun toMessageModel(): MessageModel {
        val msgStatus = when (status.lowercase()) {
            "delivered" -> MessageStatus.DELIVERED
            "seen" -> MessageStatus.SEEN
            else -> MessageStatus.SENT
        }
        val effectiveMediaUrl = if (!localMediaPath.isNullOrBlank() && File(localMediaPath).exists()) {
            localMediaPath
        } else {
            mediaUrl
        }
        return MessageModel(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            mediaUrl = effectiveMediaUrl,
            status = msgStatus,
            isDeleted = isDeleted,
            replyToMessageId = replyToMessageId,
            reactionsJson = reactionsJson,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromMessageModel(model: MessageModel): MessageEntity {
            val isLocalPath = model.mediaUrl?.startsWith("/") == true
            return MessageEntity(
                id = model.id ?: "temp_${System.currentTimeMillis()}",
                conversationId = model.conversationId.replace("\"", "").replace("'", "").trim().lowercase(),
                senderId = model.senderId,
                content = model.content,
                mediaUrl = if (isLocalPath) null else model.mediaUrl,
                localMediaPath = if (isLocalPath) model.mediaUrl else null,
                status = model.status.name.lowercase(),
                isDeleted = model.isDeleted,
                isHiddenForMe = false,
                replyToMessageId = model.replyToMessageId,
                reactionsJson = model.reactionsJson,
                createdAt = model.createdAt
            )
        }
    }
}
