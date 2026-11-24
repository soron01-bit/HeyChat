package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_blocked_users")
data class BlockedUserEntity(
    @PrimaryKey
    @ColumnInfo(name = "blocked_id") val blockedId: String,
    @ColumnInfo(name = "blocked_name") val blockedName: String = "",
    @ColumnInfo(name = "blocked_email") val blockedEmail: String = "",
    @ColumnInfo(name = "blocked_at") val blockedAt: Long = System.currentTimeMillis()
)
