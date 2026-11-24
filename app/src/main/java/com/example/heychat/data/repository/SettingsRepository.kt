package com.example.heychat.data.repository

import com.example.heychat.data.models.BlockedUser
import com.example.heychat.data.models.Profile

interface SettingsRepository {
    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray): Result<String>
    suspend fun updateProfile(profile: Profile): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun updateEmail(newEmail: String): Result<Unit>
    suspend fun checkUsernameAvailable(username: String): Result<Boolean>
    suspend fun getBlockedUsers(userId: String): Result<List<BlockedUser>>
    suspend fun blockUser(blockerId: String, blockedId: String): Result<Unit>
    suspend fun unblockUser(blockerId: String, blockedId: String): Result<Unit>
    suspend fun unblockAllUsers(blockerId: String): Result<Unit>
    suspend fun clearChatHistory(userId: String): Result<Unit>
}
