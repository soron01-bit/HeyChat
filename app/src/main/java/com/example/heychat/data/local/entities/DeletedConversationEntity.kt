package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_conversations")
data class DeletedConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long = System.currentTimeMillis()
)
