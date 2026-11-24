package com.example.heychat.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageReactionModel(
    @SerialName("message_id") val messageId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("emoji") val emoji: String = "",
    @SerialName("created_at") val createdAt: String? = null
)
