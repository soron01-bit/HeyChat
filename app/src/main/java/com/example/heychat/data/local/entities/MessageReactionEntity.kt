package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "message_reactions",
    primaryKeys = ["message_id", "user_id"]
)
data class MessageReactionEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "emoji") val emoji: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
