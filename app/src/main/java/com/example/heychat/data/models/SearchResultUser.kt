package com.example.heychat.data.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SearchResultUser(
    @SerialName("id") val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("username") val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("about") val about: String = "Hey there! I am using Hey Chat 💬"
) {
    val handle: String
        get() {
            val clean = username?.replace("\"", "")?.replace("'", "")?.replace("@", "")?.trim()?.lowercase()
            return if (!clean.isNullOrBlank()) "@$clean" else ""
        }
}
