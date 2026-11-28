package com.example.heychat.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class StatusPrivacyOption {
    MY_CONTACTS,
    MY_CONTACTS_EXCEPT,
    ONLY_SHARE_WITH
}

@Immutable
data class StatusPrivacySettings(
    val option: StatusPrivacyOption = StatusPrivacyOption.MY_CONTACTS,
    val excludedUserIds: List<String> = emptyList(),
    val selectedUserIds: List<String> = emptyList()
)

@Serializable
data class StatusPrivacyRow(
    @SerialName("user_id") val userId: String = "",
    @SerialName("privacy_option") val privacyOption: String = "MY_CONTACTS",
    @SerialName("excluded_user_ids") val excludedUserIds: List<String> = emptyList(),
    @SerialName("selected_user_ids") val selectedUserIds: List<String> = emptyList()
)

@Immutable
@Serializable
data class StatusItem(
    @SerialName("id") val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("user_name") val userName: String = "User",
    @SerialName("user_avatar") val userAvatar: String? = null,
    @SerialName("media_url") val mediaUrl: String = "",
    @SerialName("caption") val caption: String? = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("is_viewed") val isViewed: Boolean = false,
    @SerialName("view_count") val viewCount: Int = 0
)

@Immutable
data class UserStatusGroup(
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val statuses: List<StatusItem>,
    val lastUpdatedTime: String?,
    val hasUnseen: Boolean
)

@Immutable
@Serializable
data class StatusView(
    @SerialName("id") val id: String = "",
    @SerialName("status_id") val statusId: String = "",
    @SerialName("viewer_id") val viewerId: String = "",
    @SerialName("viewer_name") val viewerName: String = "User",
    @SerialName("viewer_avatar") val viewerAvatar: String? = null,
    @SerialName("reaction_emoji") val reactionEmoji: String? = null,
    @SerialName("viewed_at") val viewedAt: String? = null
)
