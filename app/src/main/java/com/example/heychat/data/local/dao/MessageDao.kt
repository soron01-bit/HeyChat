package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE (LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId) AND is_hidden_for_me = 0 AND id NOT IN (SELECT message_id FROM hidden_messages) ORDER BY created_at DESC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE (LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId) AND media_url IS NOT NULL AND media_url != '' AND is_hidden_for_me = 0 ORDER BY created_at DESC")
    fun getMediaMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE (LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId) AND is_hidden_for_me = 0 AND id NOT IN (SELECT message_id FROM hidden_messages) ORDER BY created_at DESC")
    suspend fun getMessagesForConversationOnce(conversationId: String): List<MessageEntity>

    @Query("SELECT created_at FROM messages WHERE (LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId) ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestCreatedAt(conversationId: String): String?

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND is_hidden_for_me = 0 AND id NOT IN (SELECT message_id FROM hidden_messages) ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestMessageForConversation(conversationId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("UPDATE messages SET is_hidden_for_me = 1 WHERE id = :messageId")
    suspend fun hideMessageForMe(messageId: String)

    @Query("UPDATE messages SET is_hidden_for_me = 1 WHERE id IN (:messageIds)")
    suspend fun hideMessagesForMe(messageIds: List<String>)

    @Query("UPDATE messages SET is_hidden_for_me = 1 WHERE sender_id = :senderId")
    suspend fun hideAllMessagesFromSender(senderId: String)

    @Query("UPDATE messages SET is_deleted = 1, content = 'This message was deleted', media_url = NULL WHERE id = :messageId")
    suspend fun markMessageAsDeleted(messageId: String)

    @Query("UPDATE messages SET is_deleted = 1, content = 'This message was deleted', media_url = NULL WHERE id IN (:messageIds)")
    suspend fun markMessagesAsDeleted(messageIds: List<String>)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET local_media_path = :localPath WHERE id = :messageId")
    suspend fun updateLocalMediaPath(messageId: String, localPath: String)

    @Query("UPDATE messages SET status = 'seen' WHERE conversation_id = :conversationId AND status != 'seen'")
    suspend fun markAllMessagesInConversationAsSeen(conversationId: String)

    @Query("UPDATE messages SET status = 'delivered' WHERE conversation_id = :conversationId AND status = 'sent'")
    suspend fun markAllMessagesInConversationAsDelivered(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE (media_url IS NULL OR media_url = '') AND created_at IS NOT NULL AND created_at < :fiveHoursAgoIso AND is_deleted = 0")
    suspend fun expireOldTextMessages(fiveHoursAgoIso: String)

    @Query("DELETE FROM messages WHERE media_url IS NOT NULL AND media_url != '' AND created_at IS NOT NULL AND created_at < :twentyFourHoursAgoIso AND is_deleted = 0")
    suspend fun expireOldMediaMessages(twentyFourHoursAgoIso: String)

    @Query("DELETE FROM messages WHERE (media_url IS NULL OR media_url = '') AND created_at IS NOT NULL AND created_at < :cutoffIsoTimestamp")
    suspend fun deleteTextMessagesOlderThan(cutoffIsoTimestamp: String)

    @Query("DELETE FROM messages WHERE media_url IS NOT NULL AND media_url != '' AND created_at IS NOT NULL AND created_at < :cutoffIsoTimestamp")
    suspend fun deleteMediaMessagesOlderThan(cutoffIsoTimestamp: String)

    @Query("DELETE FROM messages WHERE created_at IS NOT NULL AND created_at < :cutoffIsoTimestamp")
    suspend fun deleteMessagesOlderThan(cutoffIsoTimestamp: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}
