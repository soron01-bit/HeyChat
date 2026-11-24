package com.example.heychat.data.repository

import android.content.Context
import android.util.Log
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.ProfileEntity
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.service.PresenceManager
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepositoryImpl(
    private val context: Context? = null
) : AuthRepository {

    private companion object {
        const val TAG = "HeyChat_Auth"
    }

    private suspend fun verifyUserIsolationAndClearOldData(currentUserId: String) {
        if (context == null) return
        val cleanUserId = currentUserId.replace("\"", "").replace("'", "").trim()
        if (cleanUserId.isBlank()) return

        val prefs = context.getSharedPreferences("heychat_session_prefs", Context.MODE_PRIVATE)
        val lastKnownId = prefs.getString("last_logged_in_user_id", null)

        val didClear = if (lastKnownId != null && lastKnownId != cleanUserId) {
            Log.d(TAG, "clearAllTables() called, reason: different user detected, old=$lastKnownId new=$cleanUserId")
            HeyChatDatabase.getInstance(context).clearDatabase()
            true
        } else {
            false
        }

        prefs.edit().putString("last_logged_in_user_id", cleanUserId).apply()
        Log.d(TAG, "Logged in user id = $cleanUserId, Room's last known user id = $lastKnownId, clearing local data = $didClear")
    }

    override suspend fun signUp(email: String, password: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                verifyUserIsolationAndClearOldData(userId)
            }
            userId
        }
    }

    override suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = supabaseClient.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("Invalid email or password")
            verifyUserIsolationAndClearOldData(userId)
            userId
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.resetPasswordForEmail(email)
            Unit
        }
    }

    override suspend fun resendVerificationEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.resendEmail(OtpType.Email.SIGNUP, email)
            Unit
        }
    }

    override suspend fun isEmailVerified(): Boolean = withContext(Dispatchers.IO) {
        val user = supabaseClient.auth.currentUserOrNull()
        user?.emailConfirmedAt != null
    }

    override suspend fun getProfile(userId: String): Result<Profile?> = withContext(Dispatchers.IO) {
        val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
        verifyUserIsolationAndClearOldData(cleanUserId)

        runCatching {
            var localProfile: Profile? = null
            if (context != null) {
                localProfile = HeyChatDatabase.getInstance(context).profileDao().getProfile(cleanUserId)?.toProfile()
            }

            val remoteResult = runCatching {
                val list = supabaseClient.from("profiles")
                    .select {
                        filter {
                            eq("id", cleanUserId)
                        }
                    }.decodeList<Profile>()
                list.firstOrNull()
            }

            val remoteProfile = remoteResult.getOrNull()
            if (remoteProfile != null) {
                if (context != null) {
                    HeyChatDatabase.getInstance(context).profileDao().upsertProfile(ProfileEntity.fromProfile(remoteProfile))
                }
                Log.d(TAG, "getProfile REMOTE SUCCESS -> fullName: '${remoteProfile.fullName}'")
                remoteProfile
            } else if (localProfile != null) {
                Log.d(TAG, "getProfile OFFLINE LOCAL -> fullName: '${localProfile.fullName}'")
                localProfile
            } else if (remoteResult.isFailure) {
                val authUser = supabaseClient.auth.currentUserOrNull()
                val fallbackProfile = Profile(
                    id = cleanUserId,
                    email = authUser?.email ?: "",
                    fullName = authUser?.email?.substringBefore("@") ?: "User"
                )
                Log.w(TAG, "getProfile OFFLINE FALLBACK -> ${remoteResult.exceptionOrNull()?.message}")
                fallbackProfile
            } else {
                null
            }
        }
    }

    override suspend fun checkUsernameAvailable(username: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val clean = username.replace("\"", "").replace("'", "").replace("@", "").trim().lowercase()
        if (clean.length < 3) return@withContext Result.success(false)

        runCatching {
            val params = buildJsonObject {
                put("p_username", clean)
            }
            val response = supabaseClient.postgrest.rpc(
                function = "check_username_available",
                parameters = params
            )
            response.decodeAs<Boolean>()
        }
    }

    override suspend fun upsertProfile(profile: Profile): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUserId = profile.id.replace("\"", "").replace("'", "").trim()
        verifyUserIsolationAndClearOldData(cleanUserId)
        val authUser = supabaseClient.auth.currentUserOrNull()
        val userEmail = authUser?.email?.ifBlank { profile.email } ?: profile.email ?: ""

        runCatching {
            val updatePayload = buildJsonObject {
                put("id", cleanUserId)
                if (userEmail.isNotBlank()) {
                    put("email", userEmail)
                }
                put("full_name", profile.fullName ?: "")
                if (!profile.username.isNullOrBlank()) {
                    put("username", profile.username.replace("@", "").trim().lowercase())
                }
                put("about", profile.about ?: "Hey there! I am using Hey Chat 💬")
            }

            supabaseClient.from("profiles").upsert(updatePayload)
            if (context != null) {
                HeyChatDatabase.getInstance(context).profileDao().upsertProfile(ProfileEntity.fromProfile(profile))
            }
            Log.d(TAG, "Profile upserted successfully for user '$cleanUserId'")
            Unit
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to upsert profile for '$cleanUserId': ${throwable.message}", throwable)
        }
    }

    override suspend fun uploadAvatar(
        userId: String,
        imageBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
        runCatching {
            val bucket = supabaseClient.storage.from("avatars")
            val path = "$cleanUserId/avatar.jpg"

            bucket.upload(
                path = path,
                data = imageBytes,
                upsert = true
            )

            val publicUrl = bucket.publicUrl(path)
            Log.d(TAG, "uploadAvatar SUCCESS -> publicUrl: '$publicUrl'")

            // Update avatar_url in Supabase profiles table
            val updatePayload = buildJsonObject {
                put("id", cleanUserId)
                put("avatar_url", publicUrl)
            }
            supabaseClient.from("profiles").upsert(updatePayload)

            // Update Room DB
            if (context != null) {
                val currentLocalProfile = HeyChatDatabase.getInstance(context).profileDao().getProfile(cleanUserId)?.toProfile()
                val updatedProfile = (currentLocalProfile ?: Profile(id = cleanUserId, email = "")).copy(avatarUrl = publicUrl)
                HeyChatDatabase.getInstance(context).profileDao().upsertProfile(ProfileEntity.fromProfile(updatedProfile))
            }

            publicUrl
        }.onFailure { throwable ->
            Log.e(TAG, "uploadAvatar FAILED for user '$cleanUserId': ${throwable.message}", throwable)
        }
    }

    override fun getCurrentUserId(): String? {
        return supabaseClient.auth.currentUserOrNull()?.id
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            PresenceManager.onAppStop()
            supabaseClient.auth.signOut()
            if (context != null) {
                val prefs = context.getSharedPreferences("heychat_session_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("last_logged_in_user_id").apply()
                Log.d(TAG, "clearAllTables() called, reason: logout")
                HeyChatDatabase.getInstance(context).clearDatabase()
            }
            Unit
        }
    }
}
