package com.example.heychat.data.repository

import android.content.Context
import android.util.Log
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.BlockedUserEntity
import com.example.heychat.data.local.entities.ProfileEntity
import com.example.heychat.data.models.BlockedUser
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SettingsRepositoryImpl(
    private val context: Context? = null
) : SettingsRepository {

    private companion object {
        const val TAG = "HeyChat_Settings"
    }

    override suspend fun uploadAvatar(userId: String, imageBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
            val fileName = "avatar_$cleanUserId.jpg"
            val bucket = supabaseClient.storage.from("avatars")
            bucket.upload(path = fileName, data = imageBytes, upsert = true)
            val publicUrl = bucket.publicUrl(fileName)
            val timestampedUrl = "$publicUrl?t=${System.currentTimeMillis()}"
            timestampedUrl
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

    override suspend fun updateProfile(profile: Profile): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUserId = profile.id.replace("\"", "").replace("'", "").trim()
        runCatching {
            val avatarUrlToSave = if (!profile.avatarUrl.isNullOrBlank() && !profile.avatarUrl.contains("?t=")) {
                "${profile.avatarUrl}?t=${System.currentTimeMillis()}"
            } else {
                profile.avatarUrl
            }

            if (context != null) {
                val db = HeyChatDatabase.getInstance(context)
                val profileEntity = ProfileEntity(
                    id = cleanUserId,
                    email = profile.email ?: "",
                    fullName = profile.fullName,
                    username = profile.username,
                    avatarUrl = avatarUrlToSave,
                    about = profile.about ?: "Hey there! I am using Hey Chat 💬",
                    phoneNumber = profile.phoneNumber ?: ""
                )
                db.profileDao().upsertProfile(profileEntity)
                db.conversationDao().updateAvatarForUser(cleanUserId, avatarUrlToSave)
            }

            val updatePayload = buildJsonObject {
                put("full_name", profile.fullName ?: "")
                if (!profile.username.isNullOrBlank()) {
                    put("username", profile.username.replace("@", "").trim().lowercase())
                }
                put("about", profile.about ?: "Hey there! I am using Hey Chat 💬")
                put("phone_number", profile.phoneNumber ?: "")
                if (!avatarUrlToSave.isNullOrBlank()) {
                    put("avatar_url", avatarUrlToSave)
                }
            }

            supabaseClient.from("profiles").update(updatePayload) {
                filter {
                    eq("id", cleanUserId)
                }
            }
            Log.d(TAG, "updateProfile SUCCESS -> Saved phone_number '${profile.phoneNumber}' for user '$cleanUserId'")
            Unit
        }
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.modifyUser {
                password = newPassword
            }
            Unit
        }
    }

    override suspend fun updateEmail(newEmail: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.modifyUser {
                email = newEmail
            }
            Unit
        }
    }

    override suspend fun getBlockedUsers(userId: String): Result<List<BlockedUser>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
            val rawList = supabaseClient.from("blocked_users")
                .select {
                    filter {
                        eq("blocker_id", cleanUserId)
                    }
                }.decodeList<BlockedUser>()

            rawList.map { blockedRecord ->
                val targetProfile = runCatching {
                    supabaseClient.from("profiles")
                        .select {
                            filter {
                                eq("id", blockedRecord.blockedId)
                            }
                        }.decodeSingleOrNull<Profile>()
                }.getOrNull()

                blockedRecord.copy(
                    blockedName = targetProfile?.fullName.orEmpty().ifBlank { "User" },
                    blockedEmail = targetProfile?.email.orEmpty().ifBlank { "blocked@user.com" }
                )
            }
        }
    }

    override suspend fun blockUser(blockerId: String, blockedId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBlockerId = blockerId.replace("\"", "").replace("'", "").trim()
            val cleanBlockedId = blockedId.replace("\"", "").replace("'", "").trim()

            if (cleanBlockerId.isBlank() || cleanBlockedId.isBlank() || cleanBlockerId == cleanBlockedId) {
                Log.e("HeyChat_Search", "blockUser ABORTED -> Invalid IDs (blocker='$cleanBlockerId', blocked='$cleanBlockedId')")
                return@withContext Result.failure(IllegalArgumentException("Cannot block yourself or empty user ID"))
            }

            if (context != null) {
                val db = HeyChatDatabase.getInstance(context)
                db.blockedUserDao().blockUserLocally(BlockedUserEntity(cleanBlockedId))
                db.messageDao().hideAllMessagesFromSender(cleanBlockedId)
            }

            val params = buildJsonObject {
                put("p_blocked_id", cleanBlockedId)
            }
            runCatching {
                supabaseClient.postgrest.rpc(
                    function = "block_user",
                    parameters = params
                )
            }.onFailure {
                val payload = buildJsonObject {
                    put("blocker_id", cleanBlockerId)
                    put("blocked_id", cleanBlockedId)
                }
                supabaseClient.from("blocked_users").insert(payload)
            }
            Log.d("HeyChat_Search", "blockUser SUCCESS -> Blocker '$cleanBlockerId' blocked '$cleanBlockedId' in Supabase Database")
            Unit
        }.onFailure { throwable ->
            Log.e("HeyChat_Search", "blockUser FAILED -> ${throwable.message}", throwable)
        }
    }

    override suspend fun unblockUser(blockerId: String, blockedId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBlockerId = blockerId.replace("\"", "").replace("'", "").trim()
            val cleanBlockedId = blockedId.replace("\"", "").replace("'", "").trim()

            if (cleanBlockerId.isBlank() || cleanBlockedId.isBlank() || cleanBlockerId == cleanBlockedId) {
                Log.e("HeyChat_Search", "unblockUser ABORTED -> Invalid IDs (blocker='$cleanBlockerId', blocked='$cleanBlockedId')")
                return@withContext Result.failure(IllegalArgumentException("Cannot unblock yourself or empty user ID"))
            }

            if (context != null) {
                val db = HeyChatDatabase.getInstance(context)
                db.blockedUserDao().unblockUserLocally(cleanBlockedId)
            }

            val params = buildJsonObject {
                put("p_blocked_id", cleanBlockedId)
            }
            runCatching {
                supabaseClient.postgrest.rpc(
                    function = "unblock_user",
                    parameters = params
                )
            }.onFailure {
                supabaseClient.from("blocked_users")
                    .delete {
                        filter {
                            eq("blocker_id", cleanBlockerId)
                            eq("blocked_id", cleanBlockedId)
                        }
                    }
            }
            Log.d("HeyChat_Search", "unblockUser SUCCESS -> Unblocked '$cleanBlockedId' in Supabase Database")
            Unit
        }.onFailure { throwable ->
            Log.e("HeyChat_Search", "unblockUser FAILED -> ${throwable.message}", throwable)
        }
    }

    override suspend fun unblockAllUsers(blockerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBlockerId = blockerId.replace("\"", "").replace("'", "").trim()
            supabaseClient.from("blocked_users")
                .delete {
                    filter {
                        eq("blocker_id", cleanBlockerId)
                    }
                }
            Log.d("HeyChat_Search", "unblockAllUsers SUCCESS -> Cleared all blocks for '$cleanBlockerId'")
            Unit
        }
    }

    override suspend fun clearChatHistory(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
            supabaseClient.from("messages")
                .delete {
                    filter {
                        eq("sender_id", cleanUserId)
                    }
                }
            Unit
        }
    }
}
