package com.example.heychat.data.repository

import android.content.Context
import android.util.Log
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.HiddenMessageEntity
import com.example.heychat.data.local.entities.MessageEntity
import com.example.heychat.data.local.entities.MessageReactionEntity
import com.example.heychat.data.local.entities.ProfileEntity
import com.example.heychat.data.models.BlockedUser
import com.example.heychat.data.models.MessageInsert
import com.example.heychat.data.models.MessageModel
import com.example.heychat.data.models.MessageReactionModel
import com.example.heychat.data.models.MessageStatus
import com.example.heychat.data.models.ParticipantRow
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URL
import java.util.Collections

class ChatRepositoryImpl(
    private val context: Context
) : ChatRepository {

    private companion object {
        const val TAG = "HeyChat_ChatRepository"
        const val REALTIME_TAG = "HeyChat_Realtime"
        const val LATENCY_TAG = "HeyChat_Latency"
        const val SAFETY_NET_INTERVAL_MS = 2000L
    }

    private fun parseIsoToMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cleanIso = isoString.replace("Z", "").substringBefore(".")
            sdf.parse(cleanIso)?.time ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }

    private val messageDao = HeyChatDatabase.getInstance(context).messageDao()
    private val conversationDao = HeyChatDatabase.getInstance(context).conversationDao()
    private val deletedConversationDao = HeyChatDatabase.getInstance(context).deletedConversationDao()
    private val messageReactionDao = HeyChatDatabase.getInstance(context).messageReactionDao()
    private val messageMutex = Mutex()
    private var activeChannel: RealtimeChannel? = null

    override fun observeMessages(conversationId: String): Flow<List<MessageModel>> {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        return messageDao.getMessagesForConversation(cleanConvId).map { entities ->
            val list = entities.map { it.toMessageModel() }
            val newestMsg = list.firstOrNull()
            if (newestMsg != null) {
                Log.d(REALTIME_TAG, "Message UI update emitted for id=${newestMsg.id} at timestamp=${System.currentTimeMillis()}")
            }
            list
        }
    }

    override fun observeReactionsForMessages(messageIds: List<String>): Flow<Map<String, List<MessageReactionModel>>> {
        if (messageIds.isEmpty()) return flowOf(emptyMap())
        return messageReactionDao.getReactionsForMessages(messageIds).map { entities ->
            entities.map {
                MessageReactionModel(
                    messageId = it.messageId,
                    userId = it.userId,
                    emoji = it.emoji,
                    createdAt = it.createdAt.toString()
                )
            }.groupBy { it.messageId }
        }
    }

    override suspend fun toggleReaction(messageId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanMsgId = messageId.replace("\"", "").replace("'", "").trim()
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
        if (cleanMsgId.isBlank() || currentUserId.isBlank()) return@withContext Result.failure(IllegalStateException("Invalid message or session"))

        runCatching {
            val existingLocal = messageReactionDao.getReactionsForMessagesOnce(listOf(cleanMsgId))
                .firstOrNull { it.userId == currentUserId }

            if (existingLocal == null) {
                messageReactionDao.upsertReaction(
                    MessageReactionEntity(messageId = cleanMsgId, userId = currentUserId, emoji = emoji)
                )
            } else if (existingLocal.emoji == emoji) {
                messageReactionDao.deleteReaction(cleanMsgId, currentUserId)
            } else {
                messageReactionDao.upsertReaction(
                    MessageReactionEntity(messageId = cleanMsgId, userId = currentUserId, emoji = emoji)
                )
            }

            val broadcastPayload = buildJsonObject {
                put("message_id", cleanMsgId)
                put("user_id", currentUserId)
                put("emoji", emoji)
            }
            runCatching {
                activeChannel?.broadcast(
                    event = "message_reaction_broadcast",
                    message = broadcastPayload
                )
            }

            val params = buildJsonObject {
                put("p_message_id", cleanMsgId)
                put("p_emoji", emoji)
            }
            runCatching {
                supabaseClient.postgrest.rpc(
                    function = "toggle_message_reaction",
                    parameters = params
                )
            }

            Unit
        }
    }

    private suspend fun cleanupOldMessagesLocked() {
        runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            // 1. Text messages cutoff: 5 hours
            val fiveHoursAgoMillis = System.currentTimeMillis() - (5 * 60 * 60 * 1000L)
            val fiveHoursAgoIso = sdf.format(Date(fiveHoursAgoMillis))

            // 2. Photo/media messages cutoff: 24 hours
            val twentyFourHoursAgoMillis = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            val twentyFourHoursAgoIso = sdf.format(Date(twentyFourHoursAgoMillis))

            messageDao.expireOldTextMessages(fiveHoursAgoIso)
            messageDao.expireOldMediaMessages(twentyFourHoursAgoIso)

            Log.d(TAG, "cleanupOldMessagesLocked -> Expired text messages older than 5 hours ($fiveHoursAgoIso) and photo messages older than 24 hours ($twentyFourHoursAgoIso)")
        }.onFailure { throwable ->
            Log.e(TAG, "cleanupOldMessagesLocked FAILED -> ${throwable.message}", throwable)
        }
    }

    private fun downloadAndCacheMediaAsync(messageId: String, mediaUrl: String) {
        if (messageId.isBlank() || mediaUrl.isBlank() || mediaUrl.startsWith("/")) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val mediaDir = File(context.filesDir, "media_cache")
                if (!mediaDir.exists()) mediaDir.mkdirs()

                val localFile = File(mediaDir, "photo_$messageId.jpg")
                if (!localFile.exists()) {
                    val url = URL(mediaUrl)
                    val connection = url.openConnection()
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    val bytes = connection.getInputStream().readBytes()
                    localFile.writeBytes(bytes)
                }

                if (localFile.exists() && localFile.length() > 0) {
                    messageDao.updateLocalMediaPath(messageId, localFile.absolutePath)
                    Log.d(TAG, "downloadAndCacheMediaAsync SUCCESS -> Cached media for msg '$messageId' to '${localFile.absolutePath}'")
                }
            }.onFailure { throwable ->
                Log.w(TAG, "downloadAndCacheMediaAsync FAILED for msg '$messageId': ${throwable.message}")
            }
        }
    }

    override suspend fun cleanupOldMessages(): Result<Unit> = withContext(Dispatchers.IO) {
        messageMutex.withLock {
            runCatching {
                cleanupOldMessagesLocked()
                Unit
            }
        }
    }

    override suspend fun syncNewMessages(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()

        messageMutex.withLock {
            runCatching {
                cleanupOldMessagesLocked()

                val prefs = context.getSharedPreferences("heychat_deleted_conv_cutoffs", Context.MODE_PRIVATE)
                val localDeletedAtCutoff = prefs.getString(cleanConvId, null)

                val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
                val remoteDeletedAtCutoff = if (currentUserId.isNotBlank()) {
                    runCatching {
                        val list = supabaseClient.from("deleted_conversations")
                            .select {
                                filter {
                                    eq("user_id", currentUserId)
                                    eq("conversation_id", cleanConvId)
                                }
                            }.decodeList<Map<String, String>>()
                        list.firstOrNull()?.get("deleted_at")?.replace("\"", "")?.replace("'", "")?.trim()
                    }.getOrNull()
                } else null

                val effectiveCutoff = localDeletedAtCutoff ?: remoteDeletedAtCutoff
                val cutoffMillis = if (!effectiveCutoff.isNullOrBlank()) parseIsoToMillis(effectiveCutoff) else 0L

                val fetchedMessages = supabaseClient.from("messages")
                    .select {
                        filter {
                            eq("conversation_id", cleanConvId)
                        }
                        order("created_at", Order.ASCENDING)
                    }.decodeList<MessageModel>()

                val filteredMessages = if (cutoffMillis > 0L) {
                    fetchedMessages.filter { msg ->
                        val msgMillis = parseIsoToMillis(msg.createdAt)
                        msgMillis > cutoffMillis
                    }
                } else {
                    fetchedMessages
                }

                if (filteredMessages.isNotEmpty()) {
                    val entities = filteredMessages.map { MessageEntity.fromMessageModel(it) }
                    messageDao.upsertMessages(entities)

                    filteredMessages.forEach { msg ->
                        if (!msg.id.isNullOrBlank() && !msg.mediaUrl.isNullOrBlank()) {
                            downloadAndCacheMediaAsync(msg.id, msg.mediaUrl)
                        }
                    }

                    val messageIds = filteredMessages.mapNotNull { it.id }.filter { it.isNotBlank() }
                    if (messageIds.isNotEmpty()) {
                        runCatching {
                            val remoteReactions = supabaseClient.from("message_reactions")
                                .select {
                                    filter {
                                        isIn("message_id", messageIds)
                                    }
                                }.decodeList<MessageReactionModel>()

                            if (remoteReactions.isNotEmpty()) {
                                val reactionEntities = remoteReactions.map {
                                    MessageReactionEntity(messageId = it.messageId, userId = it.userId, emoji = it.emoji)
                                }
                                reactionEntities.forEach { messageReactionDao.upsertReaction(it) }
                            }
                        }
                    }

                    Log.d("HeyChat_ChatRepository", "Sync fetched ${filteredMessages.size} messages for conv '$cleanConvId' (cutoffMillis: $cutoffMillis)")

                    cleanupOldMessagesLocked()
                } else {
                    Log.d("HeyChat_ChatRepository", "Sync fetched 0 messages for conv '$cleanConvId' (cutoffMillis: $cutoffMillis)")
                }
                Unit
            }.onFailure { throwable ->
                Log.e(TAG, "syncNewMessages FAILED -> ${throwable.message}", throwable)
            }
        }
    }

    private suspend fun ensureConversationInRoom(conversationId: String, currentUserId: String, lastMsgText: String, lastMsgTime: String, msgId: String = "") {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim()
        if (cleanConvId.isBlank() || safeUserId.isBlank()) return

        val displayPreview = if (lastMsgText.isBlank() || lastMsgText.equals("Photo", ignoreCase = true) || lastMsgText.equals("📷 Photo", ignoreCase = true)) "📷 Photo" else lastMsgText

        runCatching {
            deletedConversationDao.clearDeletedMark(cleanConvId)
            val existingConv = conversationDao.getConversationById(cleanConvId).firstOrNull()
            val validMsgId = msgId.ifBlank { existingConv?.lastMessageId ?: "msg_$cleanConvId" }
            if (existingConv != null) {
                conversationDao.updateLastMessagePreview(cleanConvId, displayPreview, lastMsgTime, validMsgId)
            } else {
                val chatListRepo = ChatListRepositoryImpl(context)
                chatListRepo.syncUserConversations(safeUserId)
            }
        }
    }

    private suspend fun getBlockedUserIds(currentUserId: String): Set<String> {
        if (currentUserId.isBlank()) return emptySet()
        val localBlocked = runCatching {
            HeyChatDatabase.getInstance(context).blockedUserDao().getBlockedUserIdsOnce().toSet()
        }.getOrDefault(emptySet())

        if (localBlocked.isNotEmpty()) return localBlocked

        return runCatching {
            val list = supabaseClient.from("blocked_users")
                .select {
                    filter {
                        eq("blocker_id", currentUserId)
                    }
                }.decodeList<BlockedUser>()
            list.map { it.blockedId.replace("\"", "").replace("'", "").trim() }.filter { it.isNotBlank() }.toSet()
        }.getOrDefault(emptySet())
    }

    override suspend fun runSafetyNetSync(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanConvId.isBlank()) return@withContext Result.success(Unit)

        if (!messageMutex.tryLock()) {
            Log.d("HeyChat_Realtime", "runSafetyNetSync SKIPPED -> Mutex locked by in-flight operation for conv: $cleanConvId")
            return@withContext Result.success(Unit)
        }

        try {
            val nowTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val latestIsoTimestamp = messageDao.getLatestCreatedAt(cleanConvId) ?: "1970-01-01T00:00:00Z"
            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
            val blockedIds = getBlockedUserIds(currentUserId)

            // 1. Fetch messages newer than local Room DB latest created_at
            val fetchedNewMessages = supabaseClient.from("messages")
                .select {
                    filter {
                        eq("conversation_id", cleanConvId)
                        gt("created_at", latestIsoTimestamp)
                    }
                    order("created_at", Order.ASCENDING)
                }.decodeList<MessageModel>()

            if (fetchedNewMessages.isNotEmpty()) {
                val existingLocalEntities = messageDao.getMessagesForConversationOnce(cleanConvId)
                val existingIds = existingLocalEntities.map { it.id }.toSet()

                val allowedNewMessages = fetchedNewMessages.filter { !blockedIds.contains(it.senderId) }
                val missedMessages = allowedNewMessages.filter { !existingIds.contains(it.id) }
                missedMessages.forEach { msg ->
                    val ageMs = System.currentTimeMillis() - parseIsoToMillis(msg.createdAt)
                    Log.w(LATENCY_TAG, "DELIVERED BY SAFETY NET id=${msg.id}, age=$ageMs ms")
                    Log.w("HeyChat_Realtime", "[$nowTime] MISSED BY REALTIME id=${msg.id} in conv=$cleanConvId! Recovering into Room DB.")
                }

                val entities = allowedNewMessages.map { MessageEntity.fromMessageModel(it) }
                messageDao.upsertMessages(entities)
                Log.d("HeyChat_Realtime", "[$nowTime] SafetyNet sync SUCCESS -> Recovered ${allowedNewMessages.size} messages (${missedMessages.size} missed by Realtime)")
            } else {
                Log.d("HeyChat_Realtime", "[$nowTime] SafetyNet sync CHECK -> 0 new messages since timestamp '$latestIsoTimestamp'")
            }

            // 2. Refresh status & is_deleted of 30 most recent local messages
            val recentEntities = messageDao.getMessagesForConversationOnce(cleanConvId).take(30)
            val recentIds = recentEntities.mapNotNull { it.id }.filter { it.isNotBlank() }

            if (recentIds.isNotEmpty()) {
                val remoteRecentMessages = supabaseClient.from("messages")
                    .select {
                        filter {
                            isIn("id", recentIds)
                        }
                    }.decodeList<MessageModel>()

                if (remoteRecentMessages.isNotEmpty()) {
                    val updatedEntities = remoteRecentMessages.map { MessageEntity.fromMessageModel(it) }
                    messageDao.upsertMessages(updatedEntities)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("HeyChat_Realtime", "runSafetyNetSync FAILED (ignored) -> ${e.message}")
            Result.failure(e)
        } finally {
            messageMutex.unlock()
        }
    }

    override suspend fun getConversationParticipant(
        conversationId: String,
        currentUserId: String
    ): Result<Profile> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim()

        runCatching {
            var otherUserId = runCatching {
                val cpRows = supabaseClient.from("conversation_participants")
                    .select {
                        filter {
                            eq("conversation_id", cleanConvId)
                        }
                    }.decodeList<ParticipantRow>()
                cpRows.map { it.userId.replace("\"", "").replace("'", "").trim() }.firstOrNull { it != safeUserId }
            }.getOrNull()

            if (otherUserId.isNullOrBlank() || otherUserId == safeUserId) {
                val localConv = conversationDao.getConversationById(cleanConvId).map { it?.otherUserId }.firstOrNull()
                if (!localConv.isNullOrBlank() && localConv != safeUserId) {
                    otherUserId = localConv
                }
            }

            val targetId = otherUserId ?: safeUserId

            // Read Room ProfileDao FIRST for 0ms cached profile
            val profileDao = HeyChatDatabase.getInstance(context).profileDao()
            val cachedProfile = profileDao.getProfile(targetId)?.toProfile()

            // Fetch remote profile from Supabase
            val remoteProfile = runCatching {
                supabaseClient.from("profiles")
                    .select {
                        filter {
                            eq("id", targetId)
                        }
                    }.decodeSingleOrNull<Profile>()
            }.getOrNull()

            if (remoteProfile != null) {
                profileDao.upsertProfile(ProfileEntity.fromProfile(remoteProfile))
            }

            remoteProfile ?: cachedProfile ?: Profile(id = targetId, email = "", fullName = "Chat User")
        }
    }

    override suspend fun getMessages(conversationId: String): Result<List<MessageModel>> {
        syncNewMessages(conversationId)
        return runCatching { emptyList<MessageModel>() }
    }

    override suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String,
        replyToMessageId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val cleanReplyId = replyToMessageId?.replace("\"", "")?.replace("'", "")?.trim()?.ifBlank { null }

        runCatching {
            val sendStartTime = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val msgId = UUID.randomUUID().toString()

            Log.d(LATENCY_TAG, "[SENDER] Send initiated for msgId '$msgId' at timestamp=$sendStartTime")

            val tempMsg = MessageModel(
                id = msgId,
                conversationId = cleanConvId,
                senderId = senderId,
                content = text,
                status = MessageStatus.SENT,
                replyToMessageId = cleanReplyId,
                createdAt = sdf.format(Date())
            )

            // 1. 0ms Instant Local Room DB Write
            messageDao.upsertMessage(MessageEntity.fromMessageModel(tempMsg))
            ensureConversationInRoom(cleanConvId, senderId, tempMsg.text, tempMsg.createdAt ?: "", msgId)

            // 2. Sub-10ms Direct WebSocket Broadcast to receiver's open chat room
            val broadcastPayload = buildJsonObject {
                put("id", msgId)
                put("conversation_id", cleanConvId)
                put("sender_id", senderId)
                put("content", text)
                put("status", "sent")
                put("created_at", tempMsg.createdAt)
                if (!cleanReplyId.isNullOrBlank()) {
                    put("reply_to_message_id", cleanReplyId)
                }
            }
            runCatching {
                activeChannel?.broadcast(
                    event = "new_message_broadcast",
                    message = broadcastPayload
                )
            }

            // 3. Asynchronous Supabase PostgreSQL Insert (OUTSIDE LOCKS, ZERO BLOCKING)
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val insert = MessageInsert(
                        id = msgId,
                        conversationId = cleanConvId,
                        senderId = senderId,
                        content = text,
                        status = "sent",
                        replyToMessageId = cleanReplyId
                    )
                    supabaseClient.from("messages").upsert(insert)
                    Log.d(LATENCY_TAG, "[SENDER] Supabase upsert returned for msgId '$msgId'")
                }.onFailure { throwable ->
                    Log.e(TAG, "CRITICAL_INSERT_FAILURE for '$msgId': ${throwable.message}", throwable)
                }
            }

            Unit
        }
    }

    override suspend fun sendPhotoMessage(
        conversationId: String,
        senderId: String,
        imageBytes: ByteArray,
        replyToMessageId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val cleanReplyId = replyToMessageId?.replace("\"", "")?.replace("'", "")?.trim()?.ifBlank { null }

        messageMutex.withLock {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val msgId = UUID.randomUUID().toString()

            // 1. Save local image bytes to app cache for instant local preview
            val localCacheFile = runCatching {
                val file = File(context.cacheDir, "pending_photo_$msgId.jpg")
                file.writeBytes(imageBytes)
                file
            }.getOrNull()

            val localPreviewPath = localCacheFile?.absolutePath ?: ""

            // 2. Insert optimistic temporary message with localPreviewPath into Room DB
            val tempMsg = MessageModel(
                id = msgId,
                conversationId = cleanConvId,
                senderId = senderId,
                content = "",
                mediaUrl = localPreviewPath,
                status = MessageStatus.SENT,
                replyToMessageId = cleanReplyId,
                createdAt = sdf.format(Date())
            )
            messageDao.upsertMessage(MessageEntity.fromMessageModel(tempMsg))
            ensureConversationInRoom(cleanConvId, senderId, "📷 Photo", tempMsg.createdAt ?: "", msgId)
            Log.d("HeyChat_ChatRepository", "Pending photo row inserted into Room DB for msgId '$msgId' with local preview path '$localPreviewPath'")

            runCatching {
                // 3. Upload compressed JPEG to Supabase Storage "chat-media" bucket
                Log.d("HeyChat_ChatRepository", "Upload started for message '$msgId'")
                val path = "$cleanConvId/$msgId.jpg"
                val bucket = supabaseClient.storage.from("chat-media")
                bucket.upload(path = path, data = imageBytes, upsert = true)
                val publicUrl = bucket.publicUrl(path)
                Log.d("HeyChat_ChatRepository", "Upload finished for message '$msgId' with media_url: '$publicUrl'")

                // 4. Update the SAME Room row immediately with final remote media_url
                val photoMsg = tempMsg.copy(mediaUrl = publicUrl)
                messageDao.upsertMessage(MessageEntity.fromMessageModel(photoMsg))
                Log.d("HeyChat_ChatRepository", "Room row updated for message '$msgId' with final remote media_url: '$publicUrl'")

                // 5. Delete temporary cache file after Room is updated with remote media_url
                localCacheFile?.delete()

                // 6. Direct 0ms WebSocket Broadcast to receiver's open chat room
                val broadcastPayload = buildJsonObject {
                    put("id", msgId)
                    put("conversation_id", cleanConvId)
                    put("sender_id", senderId)
                    put("content", "")
                    put("media_url", publicUrl)
                    put("status", "sent")
                    put("created_at", tempMsg.createdAt)
                    if (!cleanReplyId.isNullOrBlank()) {
                        put("reply_to_message_id", cleanReplyId)
                    }
                }
                runCatching {
                    activeChannel?.broadcast(
                        event = "new_message_broadcast",
                        message = broadcastPayload
                    )
                }

                // 7. Insert row into Supabase PostgreSQL messages table
                val insertPayload = buildJsonObject {
                    put("id", msgId)
                    put("conversation_id", cleanConvId)
                    put("sender_id", senderId)
                    put("content", "")
                    put("media_url", publicUrl)
                    put("status", "sent")
                    if (!cleanReplyId.isNullOrBlank()) {
                        put("reply_to_message_id", cleanReplyId)
                    }
                }
                supabaseClient.from("messages").upsert(insertPayload)
                Log.d("HeyChat_ChatRepository", "sendPhotoMessage SUCCESS -> Message '$msgId' inserted into Supabase with media_url '$publicUrl', replyTo '$cleanReplyId'")
                Unit
            }.onFailure { throwable ->
                Log.e("HeyChat_ChatRepository", "sendPhotoMessage FAILED for msgId '$msgId': ${throwable.message}", throwable)
            }
        }
    }

    override suspend fun sendVoiceMessage(
        conversationId: String,
        senderId: String,
        audioBytes: ByteArray,
        durationSec: Int,
        replyToMessageId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val cleanReplyId = replyToMessageId?.replace("\"", "")?.replace("'", "")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val msgId = UUID.randomUUID().toString().lowercase()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val currentIsoTime = sdf.format(Date())
        val voiceLabel = "🎤 Voice message (${durationSec}s)"

        val localCacheFile = runCatching {
            val cacheDir = File(context.cacheDir, "media_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "voice_$msgId.m4a")
            file.writeBytes(audioBytes)
            file
        }.getOrNull()

        val tempMsg = MessageModel(
            id = msgId,
            conversationId = cleanConvId,
            senderId = senderId,
            content = voiceLabel,
            mediaUrl = localCacheFile?.absolutePath ?: "",
            status = MessageStatus.SENT,
            replyToMessageId = cleanReplyId,
            createdAt = currentIsoTime
        )

        messageMutex.withLock {
            messageDao.upsertMessage(MessageEntity.fromMessageModel(tempMsg))
            ensureConversationInRoom(cleanConvId, senderId, voiceLabel, currentIsoTime, msgId)

            runCatching {
                val path = "$cleanConvId/$msgId.m4a"
                val bucket = supabaseClient.storage.from("chat-media")
                bucket.upload(path = path, data = audioBytes, upsert = true)
                val publicUrl = bucket.publicUrl(path)

                val voiceMsg = tempMsg.copy(mediaUrl = publicUrl)
                messageDao.upsertMessage(MessageEntity.fromMessageModel(voiceMsg))

                val broadcastPayload = buildJsonObject {
                    put("id", msgId)
                    put("conversation_id", cleanConvId)
                    put("sender_id", senderId)
                    put("content", voiceLabel)
                    put("media_url", publicUrl)
                    put("status", "sent")
                    put("created_at", tempMsg.createdAt)
                    if (!cleanReplyId.isNullOrBlank()) {
                        put("reply_to_message_id", cleanReplyId)
                    }
                }
                runCatching {
                    activeChannel?.broadcast(
                        event = "new_message_broadcast",
                        message = broadcastPayload
                    )
                }

                val insertPayload = buildJsonObject {
                    put("id", msgId)
                    put("conversation_id", cleanConvId)
                    put("sender_id", senderId)
                    put("content", voiceLabel)
                    put("media_url", publicUrl)
                    put("status", "sent")
                    if (!cleanReplyId.isNullOrBlank()) {
                        put("reply_to_message_id", cleanReplyId)
                    }
                }
                supabaseClient.from("messages").upsert(insertPayload)
                Log.d("HeyChat_ChatRepository", "sendVoiceMessage SUCCESS -> Voice msg '$msgId' inserted into Supabase with media_url '$publicUrl'")
                Unit
            }.onFailure { throwable ->
                Log.e("HeyChat_ChatRepository", "sendVoiceMessage FAILED for msgId '$msgId': ${throwable.message}", throwable)
            }
        }
    }

    override suspend fun markMessagesAsSeen(
        conversationId: String,
        currentUserId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim()

        runCatching {
            conversationDao.clearUnreadCount(cleanConvId)

            val seenBroadcastPayload = buildJsonObject {
                put("conversation_id", cleanConvId)
                put("reader_id", safeUserId)
            }
            runCatching {
                activeChannel?.broadcast(
                    event = "messages_seen_broadcast",
                    message = seenBroadcastPayload
                )
            }

            val params = buildJsonObject {
                put("p_conversation_id", cleanConvId)
                put("p_user_id", safeUserId)
            }
            supabaseClient.postgrest.rpc(
                function = "mark_messages_as_seen",
                parameters = params
            )
            Unit
        }
    }

    override suspend fun deleteMessageForMe(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = messageId.replace("\"", "").replace("'", "").trim()
        messageMutex.withLock {
            runCatching {
                // 1. Physically delete row from local Room DB immediately (0ms)
                messageDao.deleteMessageById(cleanId)

                // 2. Save into local hidden_messages registry
                val db = HeyChatDatabase.getInstance(context)
                db.hiddenMessageDao().hideMessageLocally(HiddenMessageEntity(cleanId))

                // 3. Call Supabase RPC delete_message_for_me for server-side per-user hidden message persistence
                val params = buildJsonObject {
                    put("p_message_id", cleanId)
                }
                runCatching {
                    supabaseClient.postgrest.rpc(
                        function = "delete_message_for_me",
                        parameters = params
                    )
                }

                Log.d(TAG, "deleteMessageForMe SUCCESS -> Physically deleted message '$cleanId' from Room DB and recorded in Supabase hidden_messages")
                Unit
            }
        }
    }

    override suspend fun deleteMessageForEveryone(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = messageId.replace("\"", "").replace("'", "").trim()
        messageMutex.withLock {
            runCatching {
                // 1. Check if message has media_url and derive Storage path BEFORE clearing media_url
                val existingEntity = messageDao.getMessageById(cleanId)
                val mediaUrl = existingEntity?.mediaUrl ?: ""

                if (!mediaUrl.isNullOrBlank() && mediaUrl.contains("/chat-media/")) {
                    val storagePath = mediaUrl.substringAfter("/chat-media/").trim()
                    if (storagePath.isNotBlank()) {
                        Log.d("HeyChat_ChatRepository", "Derived Storage path for deletion: '$storagePath'")
                        runCatching {
                            supabaseClient.storage.from("chat-media").delete(storagePath)
                            Log.d("HeyChat_ChatRepository", "Storage delete SUCCESS for path '$storagePath'")
                        }.onFailure { throwable ->
                            Log.w("HeyChat_ChatRepository", "Storage delete FAILED for path '$storagePath': ${throwable.message}. Proceeding to mark message as deleted.")
                        }
                    }
                }

                // 2. Update Supabase server database
                val updatePayload = buildJsonObject {
                    put("content", "This message was deleted")
                    put("media_url", "")
                    put("is_deleted", true)
                }
                supabaseClient.from("messages").update(updatePayload) {
                    filter {
                        eq("id", cleanId)
                    }
                }

                // 3. Mark as deleted in local Room DB for sender
                messageDao.markMessageAsDeleted(cleanId)

                // 4. Broadcast Realtime WebSocket event to receiver's open chat screen in 0ms
                activeChannel?.broadcast(
                    event = "message_deleted",
                    message = buildJsonObject {
                        put("id", cleanId)
                    }
                )

                Log.d("HeyChat_ChatRepository", "deleteMessageForEveryone -> Updated message '$cleanId' as is_deleted=true on Supabase and Room")
                Unit
            }.onFailure { throwable ->
                Log.e("HeyChat_ChatRepository", "deleteMessageForEveryone FAILED -> ${throwable.message}", throwable)
            }
        }
    }

    override suspend fun deleteMessagesForMeBulk(messageIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanIds = messageIds.map { it.replace("\"", "").replace("'", "").trim() }.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return@withContext Result.success(Unit)

        messageMutex.withLock {
            runCatching {
                // 1. Bulk delete/hide rows from local Room DB immediately (0ms)
                messageDao.hideMessagesForMe(cleanIds)
                cleanIds.forEach { id -> messageDao.deleteMessageById(id) }

                // 2. Save into local hidden_messages registry
                val db = HeyChatDatabase.getInstance(context)
                val hiddenEntities = cleanIds.map { HiddenMessageEntity(it) }
                db.hiddenMessageDao().hideMessagesLocally(hiddenEntities)

                // 3. Call Supabase RPC for each hidden message ID
                cleanIds.forEach { cleanId ->
                    val params = buildJsonObject {
                        put("p_message_id", cleanId)
                    }
                    runCatching {
                        supabaseClient.postgrest.rpc(
                            function = "delete_message_for_me",
                            parameters = params
                        )
                    }
                }

                Log.d(TAG, "deleteMessagesForMeBulk SUCCESS -> Deleted/hidden ${cleanIds.size} messages from Room DB & Supabase hidden_messages")
                Unit
            }
        }
    }

    override suspend fun deleteMessagesForEveryoneBulk(messageIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanIds = messageIds.map { it.replace("\"", "").replace("'", "").trim() }.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return@withContext Result.success(Unit)

        messageMutex.withLock {
            runCatching {
                cleanIds.forEach { cleanId ->
                    val existingEntity = messageDao.getMessageById(cleanId)
                    val mediaUrl = existingEntity?.mediaUrl ?: ""

                    if (!mediaUrl.isNullOrBlank() && mediaUrl.contains("/chat-media/")) {
                        val storagePath = mediaUrl.substringAfter("/chat-media/").trim()
                        if (storagePath.isNotBlank()) {
                            runCatching {
                                supabaseClient.storage.from("chat-media").delete(storagePath)
                            }
                        }
                    }

                    val broadcastPayload = buildJsonObject {
                        put("id", cleanId)
                    }
                    runCatching {
                        activeChannel?.broadcast(
                            event = "message_deleted",
                            message = broadcastPayload
                        )
                    }

                    messageDao.markMessageAsDeleted(cleanId)

                    val updatePayload = buildJsonObject {
                        put("content", "This message was deleted")
                        put("media_url", "")
                        put("is_deleted", true)
                    }
                    runCatching {
                        supabaseClient.from("messages").update(updatePayload) {
                            filter {
                                eq("id", cleanId)
                            }
                        }
                    }
                }
                Log.d(TAG, "deleteMessagesForEveryoneBulk SUCCESS -> Unsending ${cleanIds.size} messages for everyone")
                Unit
            }
        }
    }

    override fun subscribeToMessages(conversationId: String): Flow<MessageModel> = channelFlow {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        Log.d(REALTIME_TAG, "[$time] subscribeToMessages START -> conversationId: '$cleanConvId'")

        val targetTopic = "realtime:chat_room_$cleanConvId"
        val currentChannel = activeChannel

        val channel = if (currentChannel != null && currentChannel.topic == targetTopic && currentChannel.status.value == RealtimeChannel.Status.SUBSCRIBED) {
            Log.d(REALTIME_TAG, "[$time] subscribeToMessages REUSING active SUBSCRIBED channel '$targetTopic'")
            currentChannel
        } else {
            currentChannel?.let { oldChannel ->
                if (oldChannel.topic != targetTopic) {
                    runCatching {
                        supabaseClient.realtime.removeChannel(oldChannel)
                        Log.d(REALTIME_TAG, "[$time] Removed previous active channel for conv: ${oldChannel.topic}")
                    }
                }
            }

            val session = supabaseClient.auth.currentSessionOrNull()
            if (session != null) {
                runCatching { supabaseClient.realtime.connect() }
            }

            val newChan = supabaseClient.channel("chat_room_$cleanConvId")
            activeChannel = newChan
            newChan
        }

        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter = "conversation_id=eq.$cleanConvId"
        }

        val updateFlow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "messages"
            filter = "conversation_id=eq.$cleanConvId"
        }

        val deleteFlow = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "messages"
            filter = "conversation_id=eq.$cleanConvId"
        }

        val reactionInsertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "message_reactions"
        }

        val reactionDeleteFlow = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "message_reactions"
        }

        val broadcastFlow = channel.broadcastFlow<JsonObject>(event = "message_deleted")
        val messageBroadcastFlow = channel.broadcastFlow<JsonObject>(event = "new_message_broadcast")
        val reactionBroadcastFlow = channel.broadcastFlow<JsonObject>(event = "message_reaction_broadcast")
        val seenBroadcastFlow = channel.broadcastFlow<JsonObject>(event = "messages_seen_broadcast")
        val deliveredBroadcastFlow = channel.broadcastFlow<JsonObject>(event = "messages_delivered_broadcast")
        val deliveredMessageIds = Collections.synchronizedSet(mutableSetOf<String>())

        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: ""
        val cleanCurrentUserId = currentUserId.replace("\"", "").replace("'", "").trim().lowercase()

        launch {
            seenBroadcastFlow.collect { record ->
                val convId = record["conversation_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
                val readerId = record["reader_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""

                if (convId == cleanConvId && readerId.isNotBlank() && readerId != cleanCurrentUserId) {
                    messageMutex.withLock {
                        messageDao.markAllMessagesInConversationAsSeen(cleanConvId)
                    }
                    Log.d(REALTIME_TAG, "SEEN BROADCAST RECEIVED -> Receiver '$readerId' read messages in '$cleanConvId' (●)")
                }
            }
        }

        launch {
            deliveredBroadcastFlow.collect { record ->
                val convId = record["conversation_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
                val receiverId = record["receiver_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""

                if (convId == cleanConvId && receiverId.isNotBlank() && receiverId != cleanCurrentUserId) {
                    messageMutex.withLock {
                        messageDao.markAllMessagesInConversationAsDelivered(cleanConvId)
                    }
                    Log.d(REALTIME_TAG, "DELIVERED BROADCAST RECEIVED -> Receiver '$receiverId' received messages in '$cleanConvId' (◐)")
                }
            }
        }

        launch {
            reactionInsertFlow.collect { action ->
                val record = action.record
                val rMsgId = record["message_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val rUserId = record["user_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val rEmoji = record["emoji"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""

                if (rMsgId.isNotBlank() && rUserId.isNotBlank() && rEmoji.isNotBlank()) {
                    launch(Dispatchers.IO) {
                        messageReactionDao.upsertReaction(
                            MessageReactionEntity(messageId = rMsgId, userId = rUserId, emoji = rEmoji)
                        )
                    }
                }
            }
        }

        launch {
            reactionDeleteFlow.collect { action ->
                val record = action.oldRecord
                val rMsgId = record["message_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val rUserId = record["user_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""

                if (rMsgId.isNotBlank() && rUserId.isNotBlank()) {
                    launch(Dispatchers.IO) {
                        messageReactionDao.deleteReaction(rMsgId, rUserId)
                    }
                }
            }
        }

        // Ensure channel is subscribed AFTER all listeners are registered & maintain 5s health check
        if (channel.status.value != RealtimeChannel.Status.SUBSCRIBED) {
            launch {
                runCatching {
                    supabaseClient.realtime.connect()
                    channel.subscribe()
                    Log.d(REALTIME_TAG, "[$time] Executed channel.subscribe() for 'chat_room_$cleanConvId'")
                }.onFailure { throwable ->
                    Log.e(REALTIME_TAG, "[$time] Failed to subscribe channel 'chat_room_$cleanConvId': ${throwable.message}")
                }
            }
        }

        launch {
            while (isActive) {
                delay(5000L)
                val currentStatus = channel.status.value
                if (currentStatus != RealtimeChannel.Status.SUBSCRIBED) {
                    Log.w(REALTIME_TAG, "[$time] Channel health check -> Status is $currentStatus! Re-subscribing channel 'chat_room_$cleanConvId'...")
                    runCatching {
                        supabaseClient.realtime.connect()
                        channel.subscribe()
                    }
                }
            }
        }

        launch {
            reactionBroadcastFlow.collect { record ->
                val rMsgId = record["message_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val rUserId = record["user_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val rEmoji = record["emoji"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""

                if (rMsgId.isNotBlank() && rUserId.isNotBlank() && rEmoji.isNotBlank()) {
                    launch(Dispatchers.IO) {
                        val existing = messageReactionDao.getReactionsForMessagesOnce(listOf(rMsgId))
                            .firstOrNull { it.userId == rUserId }

                        if (existing != null && existing.emoji == rEmoji) {
                            messageReactionDao.deleteReaction(rMsgId, rUserId)
                        } else {
                            messageReactionDao.upsertReaction(
                                MessageReactionEntity(messageId = rMsgId, userId = rUserId, emoji = rEmoji)
                            )
                        }
                    }
                }
            }
        }

        // Direct 0ms WebSocket Broadcast Listener for sub-10ms peer-to-peer transmission
        launch {
            messageBroadcastFlow.collect { record ->
                val eventReceiveTime = System.currentTimeMillis()
                val id = record["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val msgConvId = record["conversation_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val senderId = record["sender_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val content = record["content"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val createdAt = record["created_at"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val replyToId = record["reply_to_message_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()

                val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
                val blockedIds = getBlockedUserIds(currentUserId)

                if (msgConvId == cleanConvId && id.isNotBlank() && senderId != currentUserId && !blockedIds.contains(senderId)) {
                    if (deliveredMessageIds.add(id)) {
                        val messageModel = MessageModel(
                            id = id,
                            conversationId = msgConvId,
                            senderId = senderId,
                            content = content,
                            status = MessageStatus.SENT,
                            replyToMessageId = replyToId,
                            createdAt = createdAt
                        )

                        trySend(messageModel)

                        launch(Dispatchers.IO) {
                            val entity = MessageEntity.fromMessageModel(messageModel.copy(status = MessageStatus.SEEN))
                            messageDao.upsertMessage(entity)
                            ensureConversationInRoom(msgConvId, currentUserId, messageModel.text, messageModel.createdAt ?: "", id)

                            if (!messageModel.mediaUrl.isNullOrBlank()) {
                                downloadAndCacheMediaAsync(id, messageModel.mediaUrl)
                            }

                            val seenPayload = buildJsonObject {
                                put("conversation_id", cleanConvId)
                                put("reader_id", currentUserId)
                            }
                            runCatching {
                                activeChannel?.broadcast(
                                    event = "messages_seen_broadcast",
                                    message = seenPayload
                                )
                            }
                            markMessagesAsSeen(cleanConvId, currentUserId)
                        }

                        val ageMs = eventReceiveTime - parseIsoToMillis(createdAt)
                        Log.d(LATENCY_TAG, "[DIRECT_BROADCAST] Message '$id' delivered in $ageMs ms via direct WebSocket broadcast!")
                    }
                }
            }
        }

        // 4. Observe Channel Status Changes
        launch {
            var backoffMs = 1000L
            channel.status.collect { status ->
                val nowTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                Log.d("HeyChat_Realtime", "[$nowTime] Channel STATUS CHANGED -> $status for conv: $cleanConvId")
                if (status == RealtimeChannel.Status.SUBSCRIBED) {
                    backoffMs = 1000L
                    Log.d("HeyChat_Realtime", "[$nowTime] Channel SUBSCRIBED successfully -> Running catch-up delta sync")
                    syncNewMessages(cleanConvId)
                } else if (status == RealtimeChannel.Status.UNSUBSCRIBED) {
                    Log.w("HeyChat_Realtime", "[$nowTime] Channel disconnected ($status). Re-subscribing in ${backoffMs}ms...")
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, 15000L)
                    runCatching {
                        supabaseClient.realtime.connect()
                        channel.subscribe()
                    }
                }
            }
        }

        // 5. WebSocket Broadcast listener for instant 0ms unsend
        launch {
            broadcastFlow.collect { payload ->
                val nowTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                val id = payload["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                Log.d(REALTIME_TAG, "[$nowTime] BROADCAST EVENT RECEIVED for message id=$id")
                if (id.isNotBlank()) {
                    messageMutex.withLock {
                        messageDao.markMessageAsDeleted(id)
                    }
                    Log.d(REALTIME_TAG, "[$nowTime] Broadcast INSTANT -> Marked message '$id' as deleted in Room DB")
                }
            }
        }

        // 6. Delete listener
        launch {
            deleteFlow.collect { action ->
                val nowTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                val deletedId = action.oldRecord["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                Log.d(REALTIME_TAG, "[$nowTime] DELETE EVENT RECEIVED for message id=$deletedId")
                if (deletedId.isNotBlank()) {
                    messageMutex.withLock {
                        messageDao.deleteMessageById(deletedId)
                    }
                    Log.d(REALTIME_TAG, "[$nowTime] Realtime DELETE -> Removed message '$deletedId' from Room DB")
                }
            }
        }

        // 7. Update listener (for status delivered/seen, unsend, Delete for Everyone live updates in 0ms)
        launch {
            updateFlow.collect { action ->
                val nowTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                val record = action.record
                val id = record["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val statusStr = record["status"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                val isDeleted = record["is_deleted"]?.toString()?.lowercase() == "true" ||
                    record["is_deleted"]?.toString() == "1" ||
                    record["content"]?.toString()?.contains("deleted", ignoreCase = true) == true

                Log.d(REALTIME_TAG, "[$nowTime] UPDATE EVENT RECEIVED for message id=$id | status=$statusStr | isDeleted=$isDeleted")

                if (id.isNotBlank()) {
                    if (isDeleted) {
                        messageMutex.withLock {
                            messageDao.markMessageAsDeleted(id)
                        }
                        Log.d(REALTIME_TAG, "[$nowTime] Realtime UPDATE INSTANT -> Updated message '$id' to is_deleted=true in local Room DB")
                    } else if (statusStr.isNotBlank()) {
                        messageMutex.withLock {
                            messageDao.updateMessageStatus(id, statusStr.lowercase())
                        }
                        Log.d(REALTIME_TAG, "[$nowTime] Realtime STATUS UPDATE -> Updated message '$id' to status='$statusStr' in local Room DB")
                    }
                }
            }
        }

        // 8. Collect INSERT events, write to Room DB directly from WebSocket payload in 0ms, and emit to Flow
        launch {
            Log.d(LATENCY_TAG, "[COLLECTOR] postgresChangeFlow collector STARTED for conv '$cleanConvId'")
            try {
                insertFlow.collect { action ->
                    val eventReceiveTime = System.currentTimeMillis()
                    val record = action.record

                    val id = record["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                    val msgConvId = record["conversation_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                    val senderId = record["sender_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                    val content = record["content"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                    val createdAt = record["created_at"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
                    val statusStr = record["status"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim() ?: "sent"
                    val replyToId = record["reply_to_message_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "00000000-0000-0000-0000-000000000000" }

                    val ageMs = eventReceiveTime - parseIsoToMillis(createdAt)
                    Log.d(LATENCY_TAG, "[REALTIME] INSERT event arrived for msgId '$id' in conv '$msgConvId' (age=$ageMs ms, timestamp=$eventReceiveTime)")

                    val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
                    val blockedIds = getBlockedUserIds(currentUserId)

                    if (msgConvId == cleanConvId && id.isNotBlank() && !blockedIds.contains(senderId)) {
                        if (deliveredMessageIds.add(id)) {
                            val status = when (statusStr.lowercase()) {
                                "delivered" -> MessageStatus.DELIVERED
                                "seen" -> MessageStatus.SEEN
                                else -> MessageStatus.SENT
                            }

                            val messageModel = MessageModel(
                                id = id,
                                conversationId = msgConvId,
                                senderId = senderId,
                                content = content,
                                status = status,
                                replyToMessageId = replyToId,
                                createdAt = createdAt
                            )

                            trySend(messageModel)

                            launch(Dispatchers.IO) {
                                val finalStatus = if (senderId != currentUserId) MessageStatus.SEEN else status
                                val entity = MessageEntity.fromMessageModel(messageModel.copy(status = finalStatus))
                                messageDao.upsertMessage(entity)
                                ensureConversationInRoom(msgConvId, currentUserId, messageModel.text, messageModel.createdAt ?: "", id)
                                cleanupOldMessagesLocked()

                                if (senderId != currentUserId && currentUserId.isNotBlank()) {
                                    val seenPayload = buildJsonObject {
                                        put("conversation_id", cleanConvId)
                                        put("reader_id", currentUserId)
                                    }
                                    runCatching {
                                        activeChannel?.broadcast(
                                            event = "messages_seen_broadcast",
                                            message = seenPayload
                                        )
                                    }
                                    markMessagesAsSeen(cleanConvId, currentUserId)
                                }
                            }

                            val roomWriteFinishTime = System.currentTimeMillis()
                            Log.d(LATENCY_TAG, "[REALTIME] Message '$id' delivered to UI in ${roomWriteFinishTime - eventReceiveTime}ms")
                        }
                    }
                }
            } finally {
                Log.d(LATENCY_TAG, "[COLLECTOR] postgresChangeFlow collector STOPPED for conv '$cleanConvId'")
            }
        }

        // 9. Subscribe to Channel
        try {
            channel.subscribe()
            Log.d(REALTIME_TAG, "[$time] channel.subscribe() called successfully for conv: $cleanConvId")
        } catch (e: Exception) {
            Log.e(REALTIME_TAG, "[$time] channel.subscribe() FAILED for conv: $cleanConvId", e)
        }

        // 10. CLEANUP on Flow Cancellation (when user leaves chat screen or switches conversations)
        awaitClose {
            val closeTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            Log.d(REALTIME_TAG, "[$closeTime] Channel REMOVED/CLEANED UP on screen close for conv: $cleanConvId")
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    supabaseClient.realtime.removeChannel(channel)
                }
            }
            if (activeChannel == channel) {
                activeChannel = null
            }
        }
    }.flowOn(Dispatchers.IO)
}
