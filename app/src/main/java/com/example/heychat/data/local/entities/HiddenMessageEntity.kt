package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_messages")
data class HiddenMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "hidden_at") val hiddenAt: Long = System.currentTimeMillis()
)
