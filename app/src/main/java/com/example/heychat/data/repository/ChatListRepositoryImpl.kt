package com.example.heychat.data.repository

import android.content.Context
import android.util.Log
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.DeletedConversationEntity
import com.example.heychat.data.local.entities.MessageEntity
import com.example.heychat.data.local.entities.UserConversationEntity
import com.example.heychat.data.models.MessageModel
import com.example.heychat.data.models.MessageStatus
import com.example.heychat.data.models.UserConversation
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.util.NotificationHelper
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatListRepositoryImpl(
    private val context: Context
) : ChatListRepository {

    private companion object {
        const val TAG = "HeyChat_ChatList"
        const val defaultNilUuid = "00000000-0000-0000-0000-000000000000"
    }

    private val conversationDao = HeyChatDatabase.getInstance(context).conversationDao()
    private val messageDao = HeyChatDatabase.getInstance(context).messageDao()
    private val deletedConversationDao = HeyChatDatabase.getInstance(context).deletedConversationDao()
    private val syncMutex = Mutex()

    override fun observeConversations(): Flow<List<UserConversation>> {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
        return conversationDao.getAllValidConversations(currentUserId).map { entities ->
            entities.map { it.toUserConversation() }
        }
    }

    override fun observeConversationById(conversationId: String): Flow<UserConversation?> {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        return conversationDao.getConversationById(cleanConvId).map { entity ->
            entity?.toUserConversation()
        }
    }

    override suspend fun saveConversationToRoom(conversation: UserConversation) = withContext(Dispatchers.IO) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
        val cleanConvId = conversation.conversationId.replace("\"", "").replace("'", "").trim()
        deletedConversationDao.clearDeletedMark(cleanConvId)
        val entity = UserConversationEntity.fromUserConversation(conversation, currentUserId)
        conversationDao.upsertConversation(entity)
        Log.d(TAG, "saveConversationToRoom -> Upserted '${conversation.otherUserName}' for conv '$cleanConvId' into Room for owner '$currentUserId'")
        Unit
    }

    override suspend fun syncUserConversations(currentUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim()
        if (safeUserId.isBlank() || safeUserId == defaultNilUuid) {
            Log.w(TAG, "syncUserConversations ABORTED -> Invalid user ID ($safeUserId)")
            return@withContext Result.failure(IllegalStateException("User not authenticated"))
        }

        syncMutex.withLock {
            Log.d("HeyChat_ChatList", "syncUserConversations MUTEX LOCKED -> Executing RPC get_user_conversations for userId: '$safeUserId'")

            runCatching {
                val existingLocalConvs = conversationDao.getConversationsForUserOnce(safeUserId)
                val localRowCount = existingLocalConvs.size

                // On fresh login / after clearing app data, clear stale local deleted marks so all server conversations load cleanly!
                if (localRowCount == 0) {
                    deletedConversationDao.clearAllDeletedMarks()
                }

                val params = buildJsonObject {
                    put("current_user_id", safeUserId)
                }

                Log.d("HeyChat_ChatList", "Calling get_user_conversations RPC for userId='$safeUserId' (local Room count: $localRowCount)")

                val rpcResult = runCatching {
                    val response = supabaseClient.postgrest.rpc(
                        function = "get_user_conversations",
                        parameters = params
                    )
                    response.decodeList<UserConversation>()
                }

                val conversations = if (rpcResult.isSuccess && rpcResult.getOrNull() != null) {
                    rpcResult.getOrThrow()
                } else {
                    // Direct Table Fallback Query (queries conversation_participants + messages table)
                    val myConvsFromParticipants = runCatching {
                        supabaseClient.from("conversation_participants")
                            .select { filter { eq("user_id", safeUserId) } }
                            .decodeList<Map<String, String>>()
                            .mapNotNull { it["conversation_id"]?.replace("\"", "")?.replace("'", "")?.trim() }
                    }.getOrDefault(emptyList())

                    val myConvsFromMessages = runCatching {
                        supabaseClient.from("messages")
                            .select { filter { eq("sender_id", safeUserId) } }
                            .decodeList<Map<String, String>>()
                            .mapNotNull { it["conversation_id"]?.replace("\"", "")?.replace("'", "")?.trim() }
                    }.getOrDefault(emptyList())

                    val convIds = (myConvsFromParticipants + myConvsFromMessages).distinct().filter { it.isNotBlank() && it != defaultNilUuid }

                    convIds.map { convId ->
                        val participants = supabaseClient.from("conversation_participants")
                            .select { filter { eq("conversation_id", convId) } }
                            .decodeList<Map<String, String>>()

                        val otherId = participants.mapNotNull { it["user_id"]?.replace("\"", "")?.replace("'", "")?.trim() }
                            .firstOrNull { it != safeUserId }
                            ?: runCatching {
                                val msgParticipants = supabaseClient.from("messages")
                                    .select { filter { eq("conversation_id", convId) } }
                                    .decodeList<Map<String, String>>()
                                msgParticipants.mapNotNull { it["sender_id"]?.replace("\"", "")?.replace("'", "")?.trim() }
                                    .firstOrNull { it != safeUserId }
                            }.getOrNull()
                            ?: safeUserId

                        val otherProfile = runCatching {
                            supabaseClient.from("profiles").select { filter { eq("id", otherId) } }.decodeSingleOrNull<Map<String, String>>()
                        }.getOrNull()

                        val lastMsg = runCatching {
                            supabaseClient.from("messages")
                                .select {
                                    filter { eq("conversation_id", convId) }
                                    order("created_at", Order.DESCENDING)
                                    limit(1)
                                }.decodeList<Map<String, String>>().firstOrNull()
                        }.getOrNull()

                        val rawFullName = otherProfile?.get("full_name")?.replace("\"", "")?.replace("'", "")?.trim()
                        val rawUsername = otherProfile?.get("username")?.replace("\"", "")?.replace("'", "")?.trim()
                        val rawEmail = otherProfile?.get("email")?.replace("\"", "")?.replace("'", "")?.trim()

                        val resolvedName = when {
                            !rawFullName.isNullOrBlank() && !rawFullName.contains("@") -> rawFullName
                            !rawUsername.isNullOrBlank() -> "@$rawUsername"
                            !rawEmail.isNullOrBlank() -> rawEmail.substringBefore("@")
                            else -> "User"
                        }

                        val rawMsgContent = lastMsg?.get("content")?.replace("\"", "")?.replace("'", "")?.trim()
                        val rawMediaUrl = lastMsg?.get("media_url")?.replace("\"", "")?.replace("'", "")?.trim()
                        val isDeleted = lastMsg?.get("is_deleted")?.lowercase() == "true"

                        val resolvedMsgContent = when {
                            isDeleted -> "This message was deleted"
                            !rawMediaUrl.isNullOrBlank() -> "📷 Photo"
                            !rawMsgContent.isNullOrBlank() -> rawMsgContent
                            else -> "Tap to start chatting"
                        }

                        UserConversation(
                            conversationId = convId,
                            otherUserId = otherId,
                            otherUserName = resolvedName,
                            otherUserAvatar = otherProfile?.get("avatar_url"),
                            lastMessageId = lastMsg?.get("id"),
                            lastMessageSenderId = lastMsg?.get("sender_id"),
                            lastMessageContent = resolvedMsgContent,
                            lastMessageTime = lastMsg?.get("created_at"),
                            lastMessageStatus = lastMsg?.get("status") ?: "sent",
                            unreadCount = 0
                        )
                    }
                }

                // Automatically un-hide any deleted conversation that received a NEW message created after the deletion cutoff timestamp
                conversations.forEach { conv ->
                    val cleanId = conv.conversationId.replace("\"", "").replace("'", "").trim()
                    if (cleanId.isNotBlank() && cleanId != defaultNilUuid) {
                        val prefs = context.getSharedPreferences("heychat_deleted_conv_cutoffs", Context.MODE_PRIVATE)
                        val prefCutoffIso = prefs.getString(cleanId, null) ?: ""
                        val lastMsgIso = conv.lastMessageTime ?: ""

                        val isNewMessageAfterDeletion = prefCutoffIso.isBlank() || (lastMsgIso.isNotBlank() && lastMsgIso > prefCutoffIso)

                        if (isNewMessageAfterDeletion) {
                            // Message is newer than the deletion cutoff -> Un-hide conversation!
                            deletedConversationDao.clearDeletedMark(cleanId)
                            prefs.edit().remove(cleanId).apply()
                        }
                    }
                }

                val deletedIds = deletedConversationDao.getDeletedConversationIds().toSet()

                val validConversations = conversations.filter { conv ->
                    val cleanId = conv.conversationId.replace("\"", "").replace("'", "").trim()
                    val hasMessages = !conv.lastMessageId.isNullOrBlank() ||
                        !conv.lastMessageContent.isNullOrBlank() ||
                        !conv.lastMessageTime.isNullOrBlank()

                    cleanId.isNotBlank() &&
                    cleanId != defaultNilUuid &&
                    !deletedIds.contains(cleanId) &&
                    hasMessages
                }

                Log.d("HeyChat_ChatList", "RPC get_user_conversations returned ${validConversations.size} valid rows (local Room count: $localRowCount)")

                if (validConversations.isNotEmpty()) {
                    val existingUnreadMap = existingLocalConvs.associate { it.conversationId to it.unreadCount }
                    val existingFavoriteMap = existingLocalConvs.associate { it.conversationId to it.isFavorite }
                    val existingMuteMap = existingLocalConvs.associate { it.conversationId to it.isMuted }

                    val entities = validConversations.map { conv ->
                        val localUnread = existingUnreadMap[conv.conversationId] ?: 0L
                        val finalUnread = if (localUnread > 0L) maxOf(localUnread, conv.unreadCount) else conv.unreadCount
                        val localFavorite = existingFavoriteMap[conv.conversationId] ?: conv.isFavorite
                        val localMuted = existingMuteMap[conv.conversationId] ?: conv.isMuted

                        // Query local Room DB for the latest message in this conversation
                        val latestLocalMsg = messageDao.getLatestMessageForConversation(conv.conversationId)

                        val resolvedText = when {
                            latestLocalMsg != null && latestLocalMsg.isDeleted -> "This message was deleted"
                            latestLocalMsg != null && !latestLocalMsg.mediaUrl.isNullOrBlank() -> "📷 Photo"
                            latestLocalMsg != null && !latestLocalMsg.content.isNullOrBlank() -> latestLocalMsg.content
                            !conv.lastMessageContent.isNullOrBlank() -> conv.lastMessageContent
                            else -> "Tap to start chatting"
                        }

                        val resolvedTime = when {
                            latestLocalMsg != null && !latestLocalMsg.createdAt.isNullOrBlank() -> latestLocalMsg.createdAt
                            !conv.lastMessageTime.isNullOrBlank() -> conv.lastMessageTime
                            else -> null
                        }

                        val resolvedMsgId = when {
                            latestLocalMsg != null && !latestLocalMsg.id.isNullOrBlank() -> latestLocalMsg.id
                            !conv.lastMessageId.isNullOrBlank() -> conv.lastMessageId
                            else -> "msg_${conv.conversationId}"
                        }

                        UserConversationEntity.fromUserConversation(conv, safeUserId).copy(
                            unreadCount = finalUnread,
                            isFavorite = localFavorite,
                            isMuted = localMuted,
                            lastMessageId = resolvedMsgId,
                            lastMessageTime = resolvedTime,
                            lastMessageContent = resolvedText
                        )
                    }

                    // Single Room Transaction
                    conversationDao.upsertConversations(entities)
                    Log.d("HeyChat_ChatList", "syncUserConversations SUCCESS -> Wrote ${entities.size} conversations into Room DB with local message previews preserved")
                } else if (localRowCount > 0) {
                    Log.w("HeyChat_ChatList", "RPC returned 0 conversations but local Room DB has $localRowCount rows. Keeping local Room DB intact (protecting cached rows).")
                } else {
                    Log.d("HeyChat_ChatList", "RPC returned 0 conversations and local Room DB is 0. Confirmed 0 conversations.")
                }
                Unit
            }.onFailure { throwable ->
                Log.e("HeyChat_ChatList", "syncUserConversations FAILED -> Error: ${throwable.message}", throwable)
            }
        }
    }

    override suspend fun getUserConversations(currentUserId: String): Result<List<UserConversation>> {
        syncUserConversations(currentUserId)
        return runCatching {
            emptyList<UserConversation>()
        }
    }

    override suspend fun toggleMuteConversation(conversationId: String, isMuted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        runCatching {
            conversationDao.updateConversationMuteStatus(cleanConvId, isMuted)
            Log.d(TAG, "toggleMuteConversation -> Set isMuted=$isMuted for conv '$cleanConvId' in Room DB")
            Unit
        }
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""

        runCatching {
            // 1. Mark as deleted in local Room DB & clear local messages on phone
            deletedConversationDao.markConversationAsDeleted(DeletedConversationEntity(cleanConvId))
            conversationDao.deleteConversationById(cleanConvId)
            messageDao.deleteMessagesForConversation(cleanConvId)

            // 2. Call Supabase RPC delete_conversation_permanently to delete from cloud server
            if (currentUserId.isNotBlank()) {
                val params = buildJsonObject {
                    put("p_conversation_id", cleanConvId)
                    put("p_user_id", currentUserId)
                }
                runCatching {
                    supabaseClient.postgrest.rpc(
                        function = "delete_conversation_permanently",
                        parameters = params
                    )
                }
            }

            Log.d(TAG, "deleteConversation -> Deleted conv '$cleanConvId' permanently from Room DB & Supabase Cloud Server")
            Unit
        }.onFailure { throwable ->
            Log.e(TAG, "deleteConversation FAILED for '$cleanConvId': ${throwable.message}", throwable)
        }
    }

    override suspend fun toggleFavoriteConversation(conversationId: String, isFavorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        runCatching {
            conversationDao.updateConversationFavoriteStatus(cleanConvId, isFavorite)
            val params = buildJsonObject {
                put("p_conversation_id", cleanConvId)
                put("p_is_favorite", isFavorite)
            }
            runCatching {
                supabaseClient.postgrest.rpc("toggle_conversation_favorite", params)
            }
            Log.d(TAG, "toggleFavoriteConversation -> Updated conv '$cleanConvId' isFavorite=$isFavorite in Room DB & Supabase")
            Unit
        }
    }

    override suspend fun toggleFavoriteConversationsBulk(conversationIds: List<String>, isFavorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanIds = conversationIds.map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return@withContext Result.success(Unit)

        runCatching {
            conversationDao.updateConversationFavoriteStatusBulk(cleanIds, isFavorite)
            cleanIds.forEach { cleanConvId ->
                val params = buildJsonObject {
                    put("p_conversation_id", cleanConvId)
                    put("p_is_favorite", isFavorite)
                }
                runCatching {
                    supabaseClient.postgrest.rpc("toggle_conversation_favorite", params)
                }
            }
            Log.d(TAG, "toggleFavoriteConversationsBulk -> Updated ${cleanIds.size} convs isFavorite=$isFavorite in Room DB & Supabase")
            Unit
        }
    }

    override fun subscribeToUserChatUpdates(currentUserId: String): Flow<Unit> {
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim()
        val channel = supabaseClient.channel("chat_list_realtime_$safeUserId")
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { channel.subscribe() }
            flow.collect { action ->
                val record = action.record
                val id = record["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val convId = record["conversation_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
                val senderId = record["sender_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val content = record["content"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val mediaUrl = record["media_url"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                val createdAt = record["created_at"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val replyToId = record["reply_to_message_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "00000000-0000-0000-0000-000000000000" }

                if (id.isNotBlank() && convId.isNotBlank()) {
                    val messageModel = MessageModel(
                        id = id,
                        conversationId = convId,
                        senderId = senderId,
                        content = content,
                        mediaUrl = mediaUrl,
                        status = if (senderId != safeUserId) MessageStatus.DELIVERED else MessageStatus.SENT,
                        replyToMessageId = replyToId,
                        createdAt = createdAt
                    )
                    val entity = MessageEntity.fromMessageModel(messageModel)
                    messageDao.upsertMessage(entity)

                    val activeChat = NotificationHelper.activeConversationId
                    if (activeChat != convId) {
                        conversationDao.incrementUnreadCount(convId, messageModel.text, createdAt)
                        Log.d("HeyChat_ChatList", "Unread state updated for conv=$convId, count=incremented")
                    } else {
                        conversationDao.updateLastMessagePreview(convId, messageModel.text, createdAt)
                    }
                }
            }
        }

        return flow.map { Unit }
    }
}
