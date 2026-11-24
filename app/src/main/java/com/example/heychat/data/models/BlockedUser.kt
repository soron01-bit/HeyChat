package com.example.heychat.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlockedUser(
    @SerialName("id") val id: String = "",
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
    @SerialName("blocked_name") val blockedName: String = "",
    @SerialName("blocked_email") val blockedEmail: String = ""
)
