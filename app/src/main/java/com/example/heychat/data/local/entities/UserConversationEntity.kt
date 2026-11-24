package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.heychat.data.models.UserConversation

@Entity(tableName = "conversations")
data class UserConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String = "",
    @ColumnInfo(name = "other_user_id") val otherUserId: String,
    @ColumnInfo(name = "other_user_name") val otherUserName: String = "User",
    @ColumnInfo(name = "other_user_avatar") val otherUserAvatar: String? = null,
    @ColumnInfo(name = "last_message_id") val lastMessageId: String? = null,
    @ColumnInfo(name = "last_message_sender_id") val lastMessageSenderId: String? = null,
    @ColumnInfo(name = "last_message_content") val lastMessageContent: String? = null,
    @ColumnInfo(name = "last_message_time") val lastMessageTime: String? = null,
    @ColumnInfo(name = "last_message_status") val lastMessageStatus: String? = "sent",
    @ColumnInfo(name = "unread_count") val unreadCount: Long = 0L,
    @ColumnInfo(name = "is_muted") val isMuted: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false
) {
    fun toUserConversation(): UserConversation {
        return UserConversation(
            conversationId = conversationId,
            otherUserId = otherUserId,
            otherUserName = otherUserName,
            otherUserAvatar = otherUserAvatar,
            lastMessageId = lastMessageId,
            lastMessageSenderId = lastMessageSenderId,
            lastMessageContent = lastMessageContent,
            lastMessageTime = lastMessageTime,
            lastMessageStatus = lastMessageStatus,
            unreadCount = unreadCount,
            isMuted = isMuted,
            isFavorite = isFavorite
        )
    }

    companion object {
        fun fromUserConversation(conv: UserConversation, ownerUserId: String = ""): UserConversationEntity {
            val displayContent = conv.lastMessageContent?.trim()
            val finalContent = when {
                displayContent.isNullOrBlank() -> "📷 Photo"
                displayContent.equals("Photo", ignoreCase = true) || displayContent.equals("📷 Photo", ignoreCase = true) -> "📷 Photo"
                else -> displayContent
            }

            val cleanConvId = conv.conversationId.replace("\"", "").replace("'", "").trim().lowercase()
            val cleanOtherId = conv.otherUserId.replace("\"", "").replace("'", "").trim().lowercase()
            val cleanOwnerId = ownerUserId.replace("\"", "").replace("'", "").trim().lowercase()

            return UserConversationEntity(
                conversationId = cleanConvId,
                ownerUserId = cleanOwnerId,
                otherUserId = cleanOtherId,
                otherUserName = conv.otherUserName,
                otherUserAvatar = conv.otherUserAvatar,
                lastMessageId = conv.lastMessageId ?: "msg_${cleanConvId}",
                lastMessageSenderId = conv.lastMessageSenderId,
                lastMessageContent = finalContent,
                lastMessageTime = conv.lastMessageTime,
                lastMessageStatus = conv.lastMessageStatus,
                unreadCount = conv.unreadCount,
                isMuted = conv.isMuted,
                isFavorite = conv.isFavorite
            )
        }
    }
}
