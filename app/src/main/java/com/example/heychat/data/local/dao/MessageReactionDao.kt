package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.MessageReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageReactionDao {

    @Query("SELECT * FROM message_reactions WHERE message_id = :messageId")
    fun getReactionsForMessage(messageId: String): Flow<List<MessageReactionEntity>>

    @Query("SELECT * FROM message_reactions WHERE message_id IN (:messageIds)")
    fun getReactionsForMessages(messageIds: List<String>): Flow<List<MessageReactionEntity>>

    @Query("SELECT * FROM message_reactions WHERE message_id IN (:messageIds)")
    suspend fun getReactionsForMessagesOnce(messageIds: List<String>): List<MessageReactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaction(entity: MessageReactionEntity)

    @Query("DELETE FROM message_reactions WHERE message_id = :messageId AND user_id = :userId")
    suspend fun deleteReaction(messageId: String, userId: String)

    @Query("DELETE FROM message_reactions WHERE message_id = :messageId")
    suspend fun deleteReactionsForMessage(messageId: String)
}
