package com.example.heychat.data.repository

import com.example.heychat.data.models.Profile
import com.example.heychat.data.models.StatusItem
import com.example.heychat.data.models.StatusPrivacySettings
import com.example.heychat.data.models.StatusView
import com.example.heychat.data.models.UserStatusGroup
import kotlinx.coroutines.flow.Flow

interface StatusRepository {
    fun observeLocalStatuses(): Flow<List<StatusEntityWrapper>>
    fun subscribeToStatusRealtimeUpdates(): Flow<Unit>
    suspend fun uploadStatusPhoto(userId: String, imageBytes: ByteArray, caption: String): Result<Unit>
    suspend fun fetchRecentStatuses(currentUserId: String): Result<List<UserStatusGroup>>
    suspend fun markStatusAsViewed(statusId: String, currentUserId: String, reactionEmoji: String? = null): Result<Unit>
    suspend fun getStatusViewers(statusId: String): Result<List<StatusView>>
    suspend fun sendStatusReply(statusItem: StatusItem, replyText: String): Result<Unit>
    suspend fun deleteStatus(statusId: String): Result<Unit>
    suspend fun getStatusPrivacySettings(): StatusPrivacySettings
    suspend fun saveStatusPrivacySettings(settings: StatusPrivacySettings): Result<Unit>
    suspend fun getEligibleContacts(): Result<List<Profile>>
}

data class StatusEntityWrapper(
    val myStatuses: List<StatusItem>,
    val recentStatusGroups: List<UserStatusGroup>
)
