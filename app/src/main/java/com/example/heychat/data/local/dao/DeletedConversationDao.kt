package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.DeletedConversationEntity

@Dao
interface DeletedConversationDao {

    @Query("SELECT conversation_id FROM deleted_conversations")
    suspend fun getDeletedConversationIds(): List<String>

    @Query("SELECT * FROM deleted_conversations WHERE conversation_id = :conversationId LIMIT 1")
    suspend fun getDeletedConversation(conversationId: String): DeletedConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markConversationAsDeleted(entity: DeletedConversationEntity)

    @Query("DELETE FROM deleted_conversations WHERE conversation_id = :conversationId")
    suspend fun clearDeletedMark(conversationId: String)

    @Query("DELETE FROM deleted_conversations")
    suspend fun clearAllDeletedMarks()
}
