package com.example.heychat.data.repository

import android.content.Context
import android.util.Log
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.models.Profile
import com.example.heychat.data.models.SearchResultUser
import com.example.heychat.data.remote.supabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SearchRepositoryImpl(
    private val context: Context? = null
) : SearchRepository {

    private companion object {
        const val TAG = "HeyChat_Search"
        const val defaultNilUuid = "00000000-0000-0000-0000-000000000000"
    }

    override suspend fun searchUsers(
        query: String,
        currentUserId: String,
        limit: Int,
        offset: Int
    ): Result<List<SearchResultUser>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = query.replace("\"", "").replace("'", "").trim()
            val cleanDigits = cleanQuery.replace(" ", "").replace("-", "").replace("+", "")
            val safeUserId = currentUserId.ifBlank { defaultNilUuid }

            // 1. Try RPC search_users
            val params = buildJsonObject {
                put("query_text", cleanQuery)
                put("current_user_id", safeUserId)
                put("page_limit", limit)
                put("page_offset", offset)
            }
            val rpcResult = runCatching {
                val response = supabaseClient.postgrest.rpc(
                    function = "search_users",
                    parameters = params
                )
                response.decodeList<SearchResultUser>()
            }.getOrNull()

            if (!rpcResult.isNullOrEmpty()) {
                return@runCatching rpcResult
            }

            // 2. Direct PostgREST Query matching partial phone number OR username ONLY!
            val rawProfiles = supabaseClient.from("profiles")
                .select {
                    filter {
                        or {
                            ilike("phone_number", "%$cleanQuery%")
                            if (cleanDigits.isNotBlank()) ilike("phone_number", "%$cleanDigits%")
                            ilike("username", "%$cleanQuery%")
                        }
                    }
                    limit(limit.toLong())
                }.decodeList<Profile>()

            rawProfiles.filter { it.id.replace("\"", "").replace("'", "").trim().lowercase() != safeUserId.lowercase() }.map { profile ->
                SearchResultUser(
                    id = profile.id,
                    fullName = profile.fullName.orEmpty().ifBlank { profile.displayName },
                    username = profile.phoneNumber?.takeIf { it.isNotBlank() } ?: profile.username,
                    avatarUrl = profile.avatarUrl,
                    about = profile.about.orEmpty().ifBlank { "Hey there! I am using Hey Chat 💬" }
                )
            }
        }
    }

    override suspend fun getOrCreateConversation(
        currentUserId: String,
        otherUserId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val safeUser1 = currentUserId.replace("\"", "").replace("'", "").trim()
        val safeUser2 = otherUserId.replace("\"", "").replace("'", "").trim()

        if (safeUser1.isBlank() || safeUser1 == defaultNilUuid) {
            Log.e(TAG, "getOrCreateConversation ABORTED -> Current user ID is invalid/blank ($safeUser1)")
            return@withContext Result.failure(IllegalStateException("User not authenticated"))
        }

        runCatching {
            val params = buildJsonObject {
                put("user_1", safeUser1)
                put("user_2", safeUser2)
            }
            val response = supabaseClient.postgrest.rpc(
                function = "get_or_create_conversation",
                parameters = params
            )
            val rawId = response.decodeAs<String>()
            val cleanConvId = rawId.replace("\"", "").replace("'", "").trim()

            // Clear local deleted mark in Room DB so re-opening the same conversation restores it to Chat List!
            if (context != null && cleanConvId.isNotBlank()) {
                val db = HeyChatDatabase.getInstance(context)
                db.deletedConversationDao().clearDeletedMark(cleanConvId)
            }

            Log.d("HeyChat_Conversation", "getOrCreateConversation RPC SUCCESS -> conversation_id: '$cleanConvId' for users ($safeUser1, $safeUser2)")
            cleanConvId
        }
    }
}
