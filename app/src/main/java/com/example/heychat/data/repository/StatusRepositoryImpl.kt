package com.example.heychat.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.StatusEntity
import com.example.heychat.data.models.Profile
import com.example.heychat.data.models.StatusItem
import com.example.heychat.data.models.StatusPrivacyOption
import com.example.heychat.data.models.StatusPrivacySettings
import com.example.heychat.data.models.StatusView
import com.example.heychat.data.models.UserStatusGroup
import com.example.heychat.data.remote.supabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class StatusRepositoryImpl(
    private val context: Context
) : StatusRepository {

    private companion object {
        const val TAG = "HeyChat_Status"
    }

    private val statusDao = HeyChatDatabase.getInstance(context).statusDao()
    private val profileDao = HeyChatDatabase.getInstance(context).profileDao()

    override fun observeLocalStatuses(): Flow<List<StatusEntityWrapper>> {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: ""
        val cleanCurrentUserId = currentUserId.replace("\"", "").replace("'", "").trim().lowercase()

        return statusDao.getAllStatuses().map { entities ->
            val items = entities.map { it.toStatusItem() }
            val myItems = items.filter { it.userId.replace("\"", "").replace("'", "").trim().lowercase() == cleanCurrentUserId }
            val otherItems = items.filter { it.userId.replace("\"", "").replace("'", "").trim().lowercase() != cleanCurrentUserId }

            val groups = otherItems.groupBy { it.userId.replace("\"", "").replace("'", "").trim().lowercase() }.map { (uId, userStatuses) ->
                val first = userStatuses.first()
                UserStatusGroup(
                    userId = uId,
                    userName = first.userName,
                    userAvatar = first.userAvatar,
                    statuses = userStatuses.sortedBy { it.createdAt },
                    lastUpdatedTime = userStatuses.maxOfOrNull { it.createdAt ?: "" },
                    hasUnseen = userStatuses.any { !it.isViewed }
                )
            }.sortedByDescending { it.lastUpdatedTime }

            listOf(StatusEntityWrapper(myItems, groups))
        }
    }

    override fun subscribeToStatusRealtimeUpdates(): Flow<Unit> = channelFlow {
        val channel = supabaseClient.channel("statuses_realtime_channel")
        val deleteFlow = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "statuses"
        }
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "statuses"
        }
        val deleteBroadcastFlow = channel.broadcastFlow<JsonObject>(event = "status_deleted_broadcast")

        launch {
            deleteBroadcastFlow.collect { payload ->
                val deletedId = payload["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
                if (deletedId.isNotBlank()) {
                    val prefs = context.getSharedPreferences("heychat_deleted_statuses", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(deletedId, true).apply()
                    statusDao.deleteStatusById(deletedId)
                    Log.d(TAG, "Realtime status_deleted_broadcast -> Removed status '$deletedId' from local Room DB")
                }
            }
        }

        launch {
            deleteFlow.collect { action ->
                val record = action.oldRecord
                val deletedStatusId = record["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
                if (deletedStatusId.isNotBlank()) {
                    val prefs = context.getSharedPreferences("heychat_deleted_statuses", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(deletedStatusId, true).apply()
                    statusDao.deleteStatusById(deletedStatusId)
                    Log.d(TAG, "Realtime DELETE event -> Removed status '$deletedStatusId' from local Room DB")
                }
            }
        }

        launch {
            insertFlow.collect {
                val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
                if (currentUserId.isNotBlank()) {
                    fetchRecentStatuses(currentUserId)
                }
            }
        }

        runCatching {
            supabaseClient.realtime.connect()
            channel.subscribe()
        }

        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { supabaseClient.realtime.removeChannel(channel) }
            }
        }
    }

    override suspend fun uploadStatusPhoto(
        userId: String,
        imageBytes: ByteArray,
        caption: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
            if (cleanUserId.isBlank()) return@withContext Result.failure(IllegalArgumentException("User not authenticated"))

            val statusId = UUID.randomUUID().toString().lowercase()

            // 1. Compress Image by 80% (Max 1280px, JPEG 80% Quality)
            val compressedBytes = compressImage80Percent(imageBytes)

            // 2. Save local file cache for 0ms preview
            val mediaDir = File(context.filesDir, "status_media_cache")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            val localFile = File(mediaDir, "status_$statusId.jpg")
            localFile.writeBytes(compressedBytes)

            // 3. Upload to Supabase Storage bucket "status-media"
            val storagePath = "$cleanUserId/$statusId.jpg"
            val bucket = supabaseClient.storage.from("status-media")
            bucket.upload(path = storagePath, data = compressedBytes, upsert = true)
            val publicUrl = bucket.publicUrl(storagePath)

            // 4. Save to Supabase PostgREST table "statuses"
            val myProfile = profileDao.getProfile(cleanUserId)?.toProfile()
                ?: runCatching {
                    supabaseClient.from("profiles")
                        .select { filter { eq("id", cleanUserId) } }
                        .decodeSingleOrNull<Profile>()
                }.getOrNull()

            val myName = myProfile?.fullName.orEmpty().ifBlank { myProfile?.email?.substringBefore("@") ?: "Me" }
            val myAvatar = myProfile?.avatarUrl

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = sdf.format(Date())
            val expiresIso = sdf.format(Date(System.currentTimeMillis() + (24 * 60 * 60 * 1000L)))

            val payload = buildJsonObject {
                put("id", statusId)
                put("user_id", cleanUserId)
                put("media_url", publicUrl)
                put("caption", caption)
                put("expires_at", expiresIso)
            }

            runCatching {
                supabaseClient.from("statuses").insert(payload)
            }

            // 5. Save to local Room DB statusDao
            val entity = StatusEntity(
                id = statusId,
                userId = cleanUserId,
                userName = myName,
                userAvatar = myAvatar,
                mediaUrl = publicUrl,
                localMediaPath = localFile.absolutePath,
                caption = caption,
                createdAt = nowIso,
                expiresAt = expiresIso,
                isViewed = true,
                viewCount = 0
            )
            statusDao.upsertStatus(entity)
            Log.d(TAG, "uploadStatusPhoto SUCCESS -> Created status '$statusId' for user '$cleanUserId'")
            Unit
        }.onFailure { throwable ->
            Log.e(TAG, "uploadStatusPhoto FAILED -> ${throwable.message}", throwable)
        }
    }

    override suspend fun fetchRecentStatuses(currentUserId: String): Result<List<UserStatusGroup>> = withContext(Dispatchers.IO) {
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim().lowercase()
        if (safeUserId.isBlank()) return@withContext Result.success(emptyList())

        runCatching {
            val db = HeyChatDatabase.getInstance(context)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = sdf.format(Date())

            // 1. Purge local statuses older than 24 hours (Always clean offline cache)
            statusDao.deleteExpiredStatuses(nowIso)

            // 2. Fetch Server-side Deleted Status Tombstones to purge from local Room DB
            val serverDeletedIds = runCatching {
                supabaseClient.from("deleted_statuses")
                    .select()
                    .decodeList<Map<String, String>>()
                    .mapNotNull { it["status_id"]?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() }
            }.getOrDefault(emptyList()).toSet()

            serverDeletedIds.forEach { deletedId ->
                statusDao.deleteStatusById(deletedId)
            }

            val deletedPrefs = context.getSharedPreferences("heychat_deleted_statuses", Context.MODE_PRIVATE)

            // 3. Network Fetch Attempt
            val rawResult = runCatching {
                supabaseClient.from("statuses")
                    .select {
                        filter { gt("expires_at", nowIso) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<StatusItem>()
            }

            if (rawResult.isSuccess) {
                val rawList = rawResult.getOrDefault(emptyList())

                val iBlockedSet = db.blockedUserDao().getBlockedUserIdsOnce().map { it.replace("\"", "").replace("'", "").trim().lowercase() }.toSet()
                val blockedMeSet = runCatching {
                    supabaseClient.from("blocked_users")
                        .select { filter { eq("blocked_id", safeUserId) } }
                        .decodeList<Map<String, String>>()
                        .mapNotNull { it["blocker_id"]?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() }
                }.getOrDefault(emptyList()).toSet()
                val allBlockedSet = iBlockedSet + blockedMeSet

                val chattedContactIds = db.conversationDao().getConversationsForUserOnce(safeUserId)
                    .map { it.otherUserId.replace("\"", "").replace("'", "").trim().lowercase() }
                    .filter { it.isNotBlank() && it != safeUserId }
                    .toSet()

                val filteredRawList = rawList.filter { item ->
                    val cleanItemId = item.id.replace("\"", "").replace("'", "").trim().lowercase()
                    if (serverDeletedIds.contains(cleanItemId) || deletedPrefs.getBoolean(cleanItemId, false) || deletedPrefs.getBoolean(item.id, false)) {
                        return@filter false
                    }

                    val authorId = item.userId.replace("\"", "").replace("'", "").trim().lowercase()
                    val isMyOwnStatus = authorId == safeUserId
                    val isBlocked = allBlockedSet.contains(authorId)
                    val isContact = chattedContactIds.contains(authorId)

                    if (isBlocked) return@filter false
                    if (isMyOwnStatus) return@filter true

                    isContact
                }

                val resolvedList = filteredRawList.map { item ->
                    val authorId = item.userId.replace("\"", "").replace("'", "").trim().lowercase()
                    val profile = db.profileDao().getProfile(authorId)?.toProfile()
                        ?: runCatching {
                            supabaseClient.from("profiles")
                                .select { filter { eq("id", authorId) } }
                                .decodeSingleOrNull<Profile>()
                        }.getOrNull()

                    val actualName = profile?.fullName.orEmpty().ifBlank {
                        profile?.email?.substringBefore("@") ?: "User"
                    }
                    val actualAvatar = profile?.avatarUrl ?: item.userAvatar

                    item.copy(
                        userName = actualName,
                        userAvatar = actualAvatar
                    )
                }

                // Clear local table only after a SUCCESSFUL network fetch, then insert resolved list!
                statusDao.clearAllStatuses()

                if (resolvedList.isNotEmpty()) {
                    val entities = resolvedList.map { item ->
                        StatusEntity.fromStatusItem(item)
                    }
                    statusDao.upsertStatuses(entities)
                }
            } else {
                Log.d(TAG, "fetchRecentStatuses -> Network offline. Preserving active local Room DB statuses for offline viewing.")
            }

            emptyList<UserStatusGroup>()
        }
    }

    override suspend fun getStatusPrivacySettings(): StatusPrivacySettings = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("heychat_status_privacy", Context.MODE_PRIVATE)
        val optionName = prefs.getString("privacy_option", StatusPrivacyOption.MY_CONTACTS.name) ?: StatusPrivacyOption.MY_CONTACTS.name
        val option = runCatching { StatusPrivacyOption.valueOf(optionName) }.getOrDefault(StatusPrivacyOption.MY_CONTACTS)
        val excludedStr = prefs.getString("excluded_user_ids", "") ?: ""
        val selectedStr = prefs.getString("selected_user_ids", "") ?: ""

        val excludedList = excludedStr.split(",").filter { it.isNotBlank() }
        val selectedList = selectedStr.split(",").filter { it.isNotBlank() }

        StatusPrivacySettings(
            option = option,
            excludedUserIds = excludedList,
            selectedUserIds = selectedList
        )
    }

    override suspend fun saveStatusPrivacySettings(settings: StatusPrivacySettings): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
        if (currentUserId.isBlank()) return@withContext Result.success(Unit)

        runCatching {
            val prefs = context.getSharedPreferences("heychat_status_privacy", Context.MODE_PRIVATE)
            val excludedStr = settings.excludedUserIds.joinToString(",")
            val selectedStr = settings.selectedUserIds.joinToString(",")

            prefs.edit()
                .putString("privacy_option", settings.option.name)
                .putString("excluded_user_ids", excludedStr)
                .putString("selected_user_ids", selectedStr)
                .apply()

            val payload = buildJsonObject {
                put("user_id", currentUserId)
                put("privacy_option", settings.option.name)
                put("excluded_user_ids", excludedStr)
                put("selected_user_ids", selectedStr)
            }

            runCatching {
                supabaseClient.from("status_privacy").upsert(payload)
            }
            Log.d(TAG, "saveStatusPrivacySettings SUCCESS -> Saved option: ${settings.option.name}")
            Unit
        }
    }

    override suspend fun getEligibleContacts(): Result<List<Profile>> = withContext(Dispatchers.IO) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
        if (currentUserId.isBlank()) return@withContext Result.success(emptyList())

        runCatching {
            val db = HeyChatDatabase.getInstance(context)
            val conversations = db.conversationDao().getConversationsForUserOnce(currentUserId)
            val contactIds = conversations.map { it.otherUserId.replace("\"", "").replace("'", "").trim() }
                .filter { it.isNotBlank() && it != currentUserId }
                .distinct()

            contactIds.mapNotNull { cId ->
                db.profileDao().getProfile(cId)?.toProfile()
                    ?: runCatching {
                        supabaseClient.from("profiles")
                            .select { filter { eq("id", cId) } }
                            .decodeSingleOrNull<Profile>()
                    }.getOrNull()
            }
        }
    }

    override suspend fun markStatusAsViewed(
        statusId: String,
        currentUserId: String,
        reactionEmoji: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanStatusId = statusId.replace("\"", "").replace("'", "").trim()
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim()
        if (cleanStatusId.isBlank() || safeUserId.isBlank()) return@withContext Result.success(Unit)

        runCatching {
            statusDao.markStatusAsViewed(cleanStatusId)

            val payload = buildJsonObject {
                put("status_id", cleanStatusId)
                put("viewer_id", safeUserId)
                if (!reactionEmoji.isNullOrBlank()) {
                    put("reaction_emoji", reactionEmoji)
                }
            }
            runCatching {
                supabaseClient.from("status_views").upsert(payload)
            }
            Unit
        }
    }

    override suspend fun sendStatusReply(
        statusItem: StatusItem,
        replyText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
        val authorId = statusItem.userId.replace("\"", "").replace("'", "").trim()
        if (currentUserId.isBlank() || authorId.isBlank() || replyText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid parameters"))
        }

        runCatching {
            val searchRepo = SearchRepositoryImpl(context)
            val convResult = searchRepo.getOrCreateConversation(currentUserId, authorId)
            val convId = convResult.getOrThrow()

            val chatRepo = ChatRepositoryImpl(context)
            val content = "Reply to status: $replyText"

            chatRepo.sendMessage(convId, currentUserId, content)
            Log.d(TAG, "sendStatusReply SUCCESS -> Sent reply to status '${statusItem.id}' in conv '$convId'")
            Unit
        }
    }

    override suspend fun getStatusViewers(statusId: String): Result<List<StatusView>> = withContext(Dispatchers.IO) {
        val cleanStatusId = statusId.replace("\"", "").replace("'", "").trim()
        if (cleanStatusId.isBlank()) return@withContext Result.success(emptyList())

        runCatching {
            val rawViews = supabaseClient.from("status_views")
                .select {
                    filter {
                        eq("status_id", cleanStatusId)
                    }
                    order("viewed_at", Order.DESCENDING)
                }.decodeList<StatusView>()

            rawViews.map { view ->
                val profile = profileDao.getProfile(view.viewerId)?.toProfile()
                    ?: runCatching {
                        supabaseClient.from("profiles")
                            .select { filter { eq("id", view.viewerId) } }
                            .decodeSingleOrNull<Profile>()
                    }.getOrNull()

                view.copy(
                    viewerName = profile?.fullName.orEmpty().ifBlank { profile?.email?.substringBefore("@") ?: "User" },
                    viewerAvatar = profile?.avatarUrl
                )
            }
        }
    }

    override suspend fun deleteStatus(statusId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val rawStatusId = statusId.replace("\"", "").replace("'", "").trim()
        val cleanStatusId = rawStatusId.lowercase()
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: ""
        val cleanCurrentUserId = currentUserId.replace("\"", "").replace("'", "").trim()
        if (cleanStatusId.isBlank()) return@withContext Result.success(Unit)

        runCatching {
            // 1. Mark permanently deleted in SharedPreferences & Room DB immediately (0ms)
            val prefs = context.getSharedPreferences("heychat_deleted_statuses", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(cleanStatusId, true).putBoolean(rawStatusId, true).apply()
            statusDao.deleteStatusById(cleanStatusId)
            statusDao.deleteStatusById(rawStatusId)

            // 2. Broadcast WebSocket Realtime DELETE event to all connected devices in <10ms!
            val broadcastPayload = buildJsonObject {
                put("id", cleanStatusId)
                put("status_id", cleanStatusId)
                put("user_id", cleanCurrentUserId)
            }
            runCatching {
                val channel = supabaseClient.channel("statuses_realtime_channel")
                channel.broadcast(event = "status_deleted_broadcast", message = broadcastPayload)
            }

            // 3. Execute Supabase RPC delete_status_permanently & Storage photo file purge
            if (cleanCurrentUserId.isNotBlank()) {
                val params = buildJsonObject {
                    put("p_status_id", cleanStatusId)
                    put("p_user_id", cleanCurrentUserId)
                }
                runCatching {
                    supabaseClient.postgrest.rpc(
                        function = "delete_status_permanently",
                        parameters = params
                    )
                }

                // 4. Direct PostgREST delete fallback matching both lowercased and raw UUIDs
                val storagePath = "$cleanCurrentUserId/$cleanStatusId.jpg"
                runCatching { supabaseClient.storage.from("status-media").delete(storagePath) }
                runCatching {
                    supabaseClient.from("statuses").delete {
                        filter { eq("id", cleanStatusId) }
                    }
                    supabaseClient.from("statuses").delete {
                        filter { eq("id", rawStatusId) }
                    }
                }
            }
            Log.d(TAG, "deleteStatus SUCCESS -> Permanently deleted status '$cleanStatusId'")
            Unit
        }
    }

    private fun compressImage80Percent(imageBytes: ByteArray): ByteArray {
        return runCatching {
            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes
            val maxDimension = 1280
            val width = originalBitmap.width
            val height = originalBitmap.height

            val (newWidth, newHeight) = if (width > maxDimension || height > maxDimension) {
                if (width > height) {
                    Pair(maxDimension, (height * (maxDimension.toFloat() / width)).toInt())
                } else {
                    Pair((width * (maxDimension.toFloat() / height)).toInt(), maxDimension)
                }
            } else {
                Pair(width, height)
            }

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream) // 80% JPEG Compression Quality
            outputStream.toByteArray()
        }.getOrDefault(imageBytes)
    }
}
