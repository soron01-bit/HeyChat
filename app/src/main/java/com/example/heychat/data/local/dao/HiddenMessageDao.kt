package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.HiddenMessageEntity

@Dao
interface HiddenMessageDao {

    @Query("SELECT message_id FROM hidden_messages")
    suspend fun getHiddenMessageIdsOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideMessageLocally(entity: HiddenMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideMessagesLocally(entities: List<HiddenMessageEntity>)

    @Query("DELETE FROM hidden_messages WHERE message_id = :messageId")
    suspend fun clearHiddenMessageLocally(messageId: String)

    @Query("DELETE FROM hidden_messages")
    suspend fun clearAllHiddenMessagesLocally()
}
