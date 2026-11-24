package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.UserConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE (owner_user_id = '' OR LOWER(owner_user_id) = LOWER(:ownerUserId) OR owner_user_id = :ownerUserId) AND conversation_id NOT IN (SELECT conversation_id FROM deleted_conversations) ORDER BY last_message_time DESC")
    fun getAllValidConversations(ownerUserId: String): Flow<List<UserConversationEntity>>

    @Query("SELECT * FROM conversations WHERE owner_user_id = '' OR LOWER(owner_user_id) = LOWER(:ownerUserId) OR owner_user_id = :ownerUserId ORDER BY last_message_time DESC")
    fun getConversationsForUser(ownerUserId: String): Flow<List<UserConversationEntity>>

    @Query("SELECT * FROM conversations WHERE owner_user_id = '' OR LOWER(owner_user_id) = LOWER(:ownerUserId) OR owner_user_id = :ownerUserId")
    suspend fun getConversationsForUserOnce(ownerUserId: String): List<UserConversationEntity>

    @Query("SELECT * FROM conversations ORDER BY last_message_time DESC")
    fun getAllConversations(): Flow<List<UserConversationEntity>>

    @Query("SELECT * FROM conversations WHERE LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId LIMIT 1")
    fun getConversationById(conversationId: String): Flow<UserConversationEntity?>

    @Query("UPDATE conversations SET unread_count = unread_count + 1, last_message_content = :lastMsgText, last_message_time = :lastMsgTime WHERE LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId")
    suspend fun incrementUnreadCount(conversationId: String, lastMsgText: String, lastMsgTime: String)

    @Query("UPDATE conversations SET last_message_content = :lastMsgText, last_message_time = :lastMsgTime, last_message_id = :lastMsgId WHERE LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId")
    suspend fun updateLastMessagePreview(conversationId: String, lastMsgText: String, lastMsgTime: String, lastMsgId: String = "")

    @Query("UPDATE conversations SET unread_count = 0 WHERE LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId")
    suspend fun clearUnreadCount(conversationId: String)

    @Query("UPDATE conversations SET is_muted = :isMuted WHERE LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId")
    suspend fun updateConversationMuteStatus(conversationId: String, isMuted: Boolean)

    @Query("UPDATE conversations SET is_favorite = :isFavorite WHERE LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId")
    suspend fun updateConversationFavoriteStatus(conversationId: String, isFavorite: Boolean)

    @Query("UPDATE conversations SET is_favorite = :isFavorite WHERE conversation_id IN (:conversationIds)")
    suspend fun updateConversationFavoriteStatusBulk(conversationIds: List<String>, isFavorite: Boolean)

    @Query("UPDATE conversations SET other_user_avatar = :avatarUrl WHERE LOWER(other_user_id) = LOWER(:userId) OR other_user_id = :userId")
    suspend fun updateAvatarForUser(userId: String, avatarUrl: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(conversations: List<UserConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: UserConversationEntity)

    @Query("DELETE FROM conversations WHERE LOWER(conversation_id) = LOWER(:conversationId) OR conversation_id = :conversationId")
    suspend fun deleteConversationById(conversationId: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()
}
