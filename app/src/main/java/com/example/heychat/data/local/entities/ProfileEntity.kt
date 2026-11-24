package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.heychat.data.models.Profile

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "email") val email: String? = "",
    @ColumnInfo(name = "full_name") val fullName: String? = null,
    @ColumnInfo(name = "username") val username: String? = null,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String? = null,
    @ColumnInfo(name = "about") val about: String? = "Hey there! I am using Hey Chat 💬",
    @ColumnInfo(name = "is_online") val isOnline: Boolean? = false,
    @ColumnInfo(name = "last_seen") val lastSeen: String? = null,
    @ColumnInfo(name = "fcm_token") val fcmToken: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String? = null
) {
    fun toProfile(): Profile {
        return Profile(
            id = id,
            email = email,
            fullName = fullName,
            username = username,
            avatarUrl = avatarUrl,
            about = about,
            isOnline = isOnline,
            lastSeen = lastSeen,
            fcmToken = fcmToken,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromProfile(profile: Profile): ProfileEntity {
            val cleanId = profile.id.replace("\"", "").replace("'", "").trim().lowercase()
            return ProfileEntity(
                id = cleanId,
                email = profile.email,
                fullName = profile.fullName,
                username = profile.username,
                avatarUrl = profile.avatarUrl,
                about = profile.about,
                isOnline = profile.isOnline,
                lastSeen = profile.lastSeen,
                fcmToken = profile.fcmToken,
                createdAt = profile.createdAt
            )
        }
    }
}
