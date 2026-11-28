package com.example.heychat.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.heychat.data.models.StatusItem
import java.io.File

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "user_name") val userName: String,
    @ColumnInfo(name = "user_avatar") val userAvatar: String? = null,
    @ColumnInfo(name = "media_url") val mediaUrl: String,
    @ColumnInfo(name = "local_media_path") val localMediaPath: String? = null,
    @ColumnInfo(name = "caption") val caption: String? = "",
    @ColumnInfo(name = "created_at") val createdAt: String? = null,
    @ColumnInfo(name = "expires_at") val expiresAt: String? = null,
    @ColumnInfo(name = "is_viewed") val isViewed: Boolean = false,
    @ColumnInfo(name = "view_count") val viewCount: Int = 0
) {
    fun toStatusItem(): StatusItem {
        val effectiveMediaUrl = when {
            !localMediaPath.isNullOrBlank() && File(localMediaPath).exists() -> {
                if (localMediaPath.startsWith("file://") || localMediaPath.startsWith("http")) localMediaPath else "file://$localMediaPath"
            }
            !mediaUrl.isNullOrBlank() -> {
                if (mediaUrl.startsWith("file://") || mediaUrl.startsWith("http")) mediaUrl else "file://$mediaUrl"
            }
            else -> ""
        }
        return StatusItem(
            id = id,
            userId = userId,
            userName = userName,
            userAvatar = userAvatar,
            mediaUrl = effectiveMediaUrl,
            caption = caption,
            createdAt = createdAt,
            expiresAt = expiresAt,
            isViewed = isViewed,
            viewCount = viewCount
        )
    }

    companion object {
        fun fromStatusItem(item: StatusItem): StatusEntity {
            val isLocal = item.mediaUrl.startsWith("/") || item.mediaUrl.startsWith("file://")
            val cleanId = item.id.replace("\"", "").replace("'", "").trim().lowercase()
            val cleanUserId = item.userId.replace("\"", "").replace("'", "").trim().lowercase()
            return StatusEntity(
                id = cleanId,
                userId = cleanUserId,
                userName = item.userName,
                userAvatar = item.userAvatar,
                mediaUrl = if (isLocal) "" else item.mediaUrl,
                localMediaPath = if (isLocal) item.mediaUrl.removePrefix("file://") else null,
                caption = item.caption,
                createdAt = item.createdAt,
                expiresAt = item.expiresAt,
                isViewed = item.isViewed,
                viewCount = item.viewCount
            )
        }
    }
}
