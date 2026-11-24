package com.example.heychat.data.repository

import com.example.heychat.data.models.Profile

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<String?>
    suspend fun signIn(email: String, password: String): Result<String>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun resendVerificationEmail(email: String): Result<Unit>
    suspend fun isEmailVerified(): Boolean
    suspend fun checkUsernameAvailable(username: String): Result<Boolean>
    suspend fun getProfile(userId: String): Result<Profile?>
    suspend fun upsertProfile(profile: Profile): Result<Unit>
    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray): Result<String>
    fun getCurrentUserId(): String?
    suspend fun signOut(): Result<Unit>
}
