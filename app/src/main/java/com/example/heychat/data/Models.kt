package com.example.heychat.data

import androidx.compose.runtime.Immutable

@Immutable
data class User(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val status: String = "Available",
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: String = "Recently"
)

@Immutable
data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: String,
    val isFromMe: Boolean,
    val status: MessageStatus = MessageStatus.READ
)

enum class MessageStatus {
    SENT, DELIVERED, READ
}

@Immutable
data class Chat(
    val id: String,
    val contact: User,
    val lastMessage: Message,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isFavorite: Boolean = false
)

@Immutable
data class CountryCode(
    val code: String,
    val name: String,
    val flag: String
)

@Immutable
data class StatusStory(
    val id: String,
    val user: User,
    val timeAgo: String,
    val isSeen: Boolean = false
)

@Immutable
data class CallRecord(
    val id: String,
    val user: User,
    val timestamp: String,
    val isVideo: Boolean,
    val isIncoming: Boolean,
    val isMissed: Boolean
)
