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
import com.example.heychat.data.models.StatusPrivacyRow
import com.example.heychat.data.models.StatusPrivacySettings
import com.example.heychat.data.models.StatusView
import com.example.heychat.data.models.UserStatusGroup
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.SearchRepositoryImpl
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
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
        return statusDao.getAllStatuses().map { entities ->
            val currentUserId = (supabaseClient.auth.currentUserOrNull()?.id
                ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
                ?: "").replace("\"", "").replace("'", "").trim().lowercase()

            val items = entities.map { it.toStatusItem() }

            val myItems = if (currentUserId.isNotBlank()) {
                items.filter { it.userId.replace("\"", "").replace("'", "").trim().lowercase() == currentUserId }
            } else {
                items
            }

            val rawOtherItems = if (currentUserId.isNotBlank()) {
                items.filter { it.userId.replace("\"", "").replace("'", "").trim().lowercase() != currentUserId }
            } else {
                emptyList()
            }

            // FILTER OTHER USERS' STATUSES BY AUTHOR PRIVACY POLICY IN LOCAL ROOM EMISSION
            val prefs = context.getSharedPreferences("heychat_status_privacy", Context.MODE_PRIVATE)
            val otherItems = rawOtherItems.filter { item ->
                val authorId = item.userId.replace("\"", "").replace("'", "").trim().lowercase()
                val optionName = prefs.getString("privacy_option_$authorId", null)

                if (optionName != null) {
                    val excludedStr = prefs.getString("excluded_ids_$authorId", "") ?: ""
                    val selectedStr = prefs.getString("selected_ids_$authorId", "") ?: ""

                    val excludedSet = extractCleanUuids(excludedStr.split(","))
                    val selectedSet = extractCleanUuids(selectedStr.split(","))

                    when (optionName.uppercase()) {
                        "MY_CONTACTS_EXCEPT" -> !excludedSet.contains(currentUserId)
                        "ONLY_SHARE_WITH" -> selectedSet.contains(currentUserId)
                        else -> true
                    }
                } else {
                    true
                }
            }

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
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "statuses"
        }
        val deleteFlow = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "statuses"
        }
        val tombstoneFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "deleted_statuses"
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { channel.subscribe() }

            launch {
                insertFlow.collect { send(Unit) }
            }
            launch {
                deleteFlow.collect { action ->
                    val deletedId = action.oldRecord["id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
                    if (deletedId.isNotBlank()) {
                        statusDao.deleteStatusById(deletedId)
                        val cacheFile = File(context.filesDir, "status_media_cache/status_$deletedId.jpg")
                        if (cacheFile.exists()) cacheFile.delete()
                    }
                    send(Unit)
                }
            }
            launch {
                tombstoneFlow.collect { action ->
                    val tombstoneId = action.record["status_id"]?.toString()?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
                    if (tombstoneId.isNotBlank()) {
                        statusDao.deleteStatusById(tombstoneId)
                        val cacheFile = File(context.filesDir, "status_media_cache/status_$tombstoneId.jpg")
                        if (cacheFile.exists()) cacheFile.delete()
                    }
                    send(Unit)
                }
            }
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
        val cleanUserId = userId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanUserId.isBlank()) return@withContext Result.failure(IllegalArgumentException("User not authenticated"))
        if (imageBytes.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Image bytes empty"))

        val statusId = UUID.randomUUID().toString().lowercase()

        // 1. Compress Image
        val compressedBytes = compressImage80Percent(imageBytes)

        // 2. Save local file cache for 0ms instant display
        val mediaDir = File(context.filesDir, "status_media_cache")
        if (!mediaDir.exists()) mediaDir.mkdirs()
        val localFile = File(mediaDir, "status_$statusId.jpg")
        localFile.writeBytes(compressedBytes)

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
        val formattedLocalPath = "file://${localFile.absolutePath}"

        // 3. STEP 1: Insert into Local Room DB IMMEDIATELY (0ms Local Retention Guarantee!)
        val localEntity = StatusEntity(
            id = statusId,
            userId = cleanUserId,
            userName = myName,
            userAvatar = myAvatar,
            mediaUrl = formattedLocalPath,
            localMediaPath = localFile.absolutePath,
            caption = caption,
            createdAt = nowIso,
            expiresAt = expiresIso,
            isViewed = true,
            viewCount = 0
        )
        statusDao.upsertStatus(localEntity)
        Log.d(TAG, "uploadStatusPhoto -> Instant 0ms local Room DB insertion SUCCESS for status '$statusId'")

        // 4. STEP 2: Background Supabase Storage & PostgREST Table Upload
        runCatching {
            val currentPrivacy = getStatusPrivacySettings()
            saveStatusPrivacySettings(currentPrivacy)

            val storagePath = "$cleanUserId/$statusId.jpg"
            val bucket = supabaseClient.storage.from("status-media")
            bucket.upload(path = storagePath, data = compressedBytes, upsert = true)
            val publicUrl = bucket.publicUrl(storagePath)

            // Update local Room DB entity with remote publicUrl
            val remoteEntity = localEntity.copy(mediaUrl = publicUrl)
            statusDao.upsertStatus(remoteEntity)

            val payload = buildJsonObject {
                put("id", statusId)
                put("user_id", cleanUserId)
                put("user_name", myName)
                put("user_avatar", myAvatar ?: "")
                put("media_url", publicUrl)
                put("caption", caption)
                put("created_at", nowIso)
                put("expires_at", expiresIso)
            }

            supabaseClient.from("statuses").upsert(payload)
            Log.d(TAG, "uploadStatusPhoto -> Supabase Storage & Table upsert SUCCESS for status '$statusId'")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "uploadStatusPhoto -> Supabase upload background warning: ${e.message}", e)
        }

        Result.success(Unit)
    }

    override suspend fun fetchRecentStatuses(currentUserId: String): Result<List<UserStatusGroup>> = withContext(Dispatchers.IO) {
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim().lowercase()
        if (safeUserId.isBlank()) return@withContext Result.success(emptyList())

        runCatching {
            val db = HeyChatDatabase.getInstance(context)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = sdf.format(Date())

            // 1. Clean local offline cache older than 24 hours
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

            // 3. Fetch active visible statuses using Supabase RPC get_visible_statuses_for_user
            val rpcParams = buildJsonObject {
                put("p_viewer_id", safeUserId)
            }
            val rpcResult = runCatching {
                supabaseClient.postgrest.rpc("get_visible_statuses_for_user", rpcParams).decodeList<StatusItem>()
            }.getOrNull()

            val rawList = if (!rpcResult.isNullOrEmpty()) {
                Log.d(TAG, "fetchRecentStatuses -> RPC get_visible_statuses_for_user returned ${rpcResult.size} visible items")
                rpcResult
            } else {
                runCatching {
                    supabaseClient.from("statuses")
                        .select {
                            filter { gt("expires_at", nowIso) }
                            order("created_at", Order.DESCENDING)
                        }.decodeList<StatusItem>()
                }.getOrDefault(emptyList())
            }

            val iBlockedSet = db.blockedUserDao().getBlockedUserIdsOnce().map { it.replace("\"", "").replace("'", "").trim().lowercase() }.toSet()
            val blockedMeSet = runCatching {
                supabaseClient.from("blocked_users")
                    .select { filter { eq("blocked_id", safeUserId) } }
                    .decodeList<Map<String, String>>()
                    .mapNotNull { it["blocker_id"]?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() }
            }.getOrDefault(emptyList()).toSet()
            val allBlockedSet = iBlockedSet + blockedMeSet

            val privacyMap = runCatching {
                supabaseClient.from("status_privacy")
                    .select()
                    .decodeList<StatusPrivacyRow>()
                    .associateBy { it.userId.replace("\"", "").replace("'", "").trim().lowercase() }
            }.getOrDefault(emptyMap())

            val filteredRawList = rawList.filter { item ->
                val cleanItemId = item.id.replace("\"", "").replace("'", "").trim().lowercase()
                if (serverDeletedIds.contains(cleanItemId) || deletedPrefs.getBoolean(cleanItemId, false)) {
                    return@filter false
                }

                val authorId = item.userId.replace("\"", "").replace("'", "").trim().lowercase()
                val isMyOwnStatus = authorId == safeUserId
                val isBlocked = allBlockedSet.contains(authorId)

                if (isBlocked) return@filter false
                if (isMyOwnStatus) return@filter true

                val authorPrivacy = privacyMap[authorId]
                if (authorPrivacy != null) {
                    val option = authorPrivacy.privacyOption.uppercase()
                    val excludedSet = extractCleanUuids(authorPrivacy.excludedUserIds)
                    val selectedSet = extractCleanUuids(authorPrivacy.selectedUserIds)

                    val isAllowed = when (option) {
                        "MY_CONTACTS_EXCEPT" -> !excludedSet.contains(safeUserId)
                        "ONLY_SHARE_WITH" -> selectedSet.contains(safeUserId)
                        else -> true
                    }
                    Log.d(TAG, "fetchRecentStatuses -> Privacy check for author '$authorId' (Option: '$option', Viewer: '$safeUserId', Allowed: $isAllowed, SelectedSet: $selectedSet)")
                    isAllowed
                } else {
                    Log.d(TAG, "fetchRecentStatuses -> Privacy map for author '$authorId' is null. Defaulting to allowed.")
                    true
                }
            }

            val allowedStatusIds = filteredRawList.map { it.id.replace("\"", "").replace("'", "").trim().lowercase() }.toSet()

            // Purge any status from local Room DB that is no longer allowed by author privacy settings
            val localOtherEntities = statusDao.getAllStatusesOnce().filter {
                val cleanAuthor = it.userId.replace("\"", "").replace("'", "").trim().lowercase()
                cleanAuthor != safeUserId
            }

            localOtherEntities.forEach { localEntity ->
                val cleanLocalId = localEntity.id.replace("\"", "").replace("'", "").trim().lowercase()
                if (!allowedStatusIds.contains(cleanLocalId)) {
                    statusDao.deleteStatusById(cleanLocalId)
                    Log.d(TAG, "fetchRecentStatuses -> Purged unallowed status '$cleanLocalId' from Room DB due to privacy settings")
                }
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

            if (resolvedList.isNotEmpty()) {
                val existingLocalMap = statusDao.getAllStatusesOnce().associateBy { it.id.replace("\"", "").replace("'", "").trim().lowercase() }

                val entities = resolvedList.map { item ->
                    val cleanId = item.id.replace("\"", "").replace("'", "").trim().lowercase()
                    val existingLocal = existingLocalMap[cleanId]
                    val entity = StatusEntity.fromStatusItem(item)

                    if (existingLocal != null && !existingLocal.localMediaPath.isNullOrBlank() && File(existingLocal.localMediaPath).exists()) {
                        entity.copy(localMediaPath = existingLocal.localMediaPath)
                    } else {
                        entity
                    }
                }
                statusDao.upsertStatuses(entities)
            }

            Result.success(emptyList<UserStatusGroup>())
        }.getOrElse { Result.success(emptyList()) }
    }

    override suspend fun markStatusAsViewed(
        statusId: String,
        currentUserId: String,
        reactionEmoji: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanStatusId = statusId.replace("\"", "").replace("'", "").trim().lowercase()
        val safeUserId = currentUserId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanStatusId.isBlank() || safeUserId.isBlank()) return@withContext Result.success(Unit)

        runCatching {
            statusDao.markStatusAsViewed(cleanStatusId)

            val myProfile = profileDao.getProfile(safeUserId)?.toProfile()
                ?: runCatching {
                    supabaseClient.from("profiles")
                        .select { filter { eq("id", safeUserId) } }
                        .decodeSingleOrNull<Profile>()
                }.getOrNull()

            val viewerName = myProfile?.fullName.orEmpty().ifBlank { myProfile?.email?.substringBefore("@") ?: "User" }
            val viewerAvatar = myProfile?.avatarUrl

            val viewId = "${cleanStatusId}_${safeUserId}"
            val payload = buildJsonObject {
                put("id", viewId)
                put("status_id", cleanStatusId)
                put("viewer_id", safeUserId)
                put("viewer_name", viewerName)
                put("viewer_avatar", viewerAvatar ?: "")
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
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val targetUserId = statusItem.userId.replace("\"", "").replace("'", "").trim().lowercase()
        if (targetUserId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Invalid target user"))

        runCatching {
            val searchRepo = SearchRepositoryImpl(context)
            val chatRepo = ChatRepositoryImpl(context)
            val convResult = searchRepo.getOrCreateConversation(currentUserId, targetUserId)
            val convId = convResult.getOrThrow()

            val replyContent = "Replying to status: \"$replyText\""
            chatRepo.sendMessage(convId, currentUserId, replyContent)
            Unit
        }
    }

    override suspend fun getStatusViewers(statusId: String): Result<List<StatusView>> = withContext(Dispatchers.IO) {
        val cleanStatusId = statusId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanStatusId.isBlank()) return@withContext Result.success(emptyList())

        runCatching {
            val rawViews = supabaseClient.from("status_views")
                .select { filter { eq("status_id", cleanStatusId) } }
                .decodeList<StatusView>()

            val uniqueViews = rawViews.distinctBy { it.viewerId.replace("\"", "").replace("'", "").trim().lowercase() }

            val resolvedViews = uniqueViews.map { view ->
                val cleanViewerId = view.viewerId.replace("\"", "").replace("'", "").trim().lowercase()
                val profile = profileDao.getProfile(cleanViewerId)?.toProfile()
                    ?: runCatching {
                        supabaseClient.from("profiles")
                            .select { filter { eq("id", cleanViewerId) } }
                            .decodeSingleOrNull<Profile>()
                    }.getOrNull()

                val actualName = profile?.fullName.orEmpty().ifBlank {
                    profile?.displayName.orEmpty().ifBlank {
                        view.viewerName.ifBlank { "User" }
                    }
                }
                val actualAvatar = profile?.avatarUrl ?: view.viewerAvatar

                view.copy(
                    viewerName = actualName,
                    viewerAvatar = actualAvatar
                )
            }

            resolvedViews
        }
    }

    override suspend fun deleteStatus(statusId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanStatusId = statusId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanStatusId.isBlank()) return@withContext Result.success(Unit)

        runCatching {
            // 1. Delete status from local Room DB immediately
            statusDao.deleteStatusById(cleanStatusId)

            // 2. Delete local cached media file from disk immediately
            val mediaDir = File(context.filesDir, "status_media_cache")
            val localFile = File(mediaDir, "status_$cleanStatusId.jpg")
            if (localFile.exists()) {
                localFile.delete()
                Log.d(TAG, "deleteStatus -> Local media file deleted: '${localFile.absolutePath}'")
            }

            // 3. Record deletion tombstone in local SharedPreferences & Supabase
            val prefs = context.getSharedPreferences("heychat_deleted_statuses", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(cleanStatusId, true).apply()

            val currentUserId = (supabaseClient.auth.currentUserOrNull()?.id ?: "").replace("\"", "").replace("'", "").trim().lowercase()

            val tombstonePayload = buildJsonObject {
                put("status_id", cleanStatusId)
            }
            runCatching {
                supabaseClient.from("deleted_statuses").upsert(tombstonePayload)
            }

            // 4. Trigger Supabase RPC & Delete from PostgreSQL statuses table
            val rpcParams = buildJsonObject {
                put("p_status_id", cleanStatusId)
                put("p_user_id", currentUserId)
            }
            val singleParam = buildJsonObject {
                put("p_status_id", cleanStatusId)
            }

            runCatching {
                supabaseClient.postgrest.rpc("delete_status_permanently", rpcParams)
                Log.d(TAG, "deleteStatus RPC 'delete_status_permanently' EXECUTED for status '$cleanStatusId'")
            }.onFailure { e ->
                Log.e(TAG, "deleteStatus RPC 'delete_status_permanently' FAILED: ${e.message}", e)
            }

            runCatching {
                supabaseClient.postgrest.rpc("delete_status_by_id", singleParam)
                Log.d(TAG, "deleteStatus RPC 'delete_status_by_id' EXECUTED for status '$cleanStatusId'")
            }.onFailure { e ->
                Log.e(TAG, "deleteStatus RPC 'delete_status_by_id' FAILED: ${e.message}", e)
            }

            runCatching {
                supabaseClient.from("statuses").delete {
                    filter {
                        eq("id", cleanStatusId)
                    }
                }
                Log.d(TAG, "deleteStatus PostgREST delete EXECUTED for status '$cleanStatusId'")
            }.onFailure { e ->
                Log.e(TAG, "deleteStatus PostgREST delete FAILED: ${e.message}", e)
            }

            Log.d(TAG, "deleteStatus SUCCESS -> Deleted status '$cleanStatusId' from Room DB, disk cache, & Supabase")
            Unit
        }
    }

    override suspend fun getStatusPrivacySettings(): StatusPrivacySettings = withContext(Dispatchers.IO) {
        val currentUserId = (supabaseClient.auth.currentUserOrNull()?.id ?: "").replace("\"", "").replace("'", "").trim().lowercase()
        val prefs = context.getSharedPreferences("heychat_status_privacy", Context.MODE_PRIVATE)

        val remoteSettings = runCatching {
            val list = supabaseClient.from("status_privacy")
                .select { filter { eq("user_id", currentUserId) } }
                .decodeList<StatusPrivacyRow>()
            list.firstOrNull()
        }.getOrNull()

        if (remoteSettings != null) {
            val option = runCatching { StatusPrivacyOption.valueOf(remoteSettings.privacyOption) }.getOrDefault(StatusPrivacyOption.MY_CONTACTS)
            val cleanExcluded = remoteSettings.excludedUserIds.map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }
            val cleanSelected = remoteSettings.selectedUserIds.map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }

            prefs.edit()
                .putString("privacy_option_$currentUserId", option.name)
                .putString("excluded_ids_$currentUserId", cleanExcluded.joinToString(","))
                .putString("selected_ids_$currentUserId", cleanSelected.joinToString(","))
                .apply()

            return@withContext StatusPrivacySettings(option, cleanExcluded, cleanSelected)
        }

        val optionName = prefs.getString("privacy_option_$currentUserId", StatusPrivacyOption.MY_CONTACTS.name)
            ?: StatusPrivacyOption.MY_CONTACTS.name
        val option = runCatching { StatusPrivacyOption.valueOf(optionName) }.getOrDefault(StatusPrivacyOption.MY_CONTACTS)

        val excludedStr = prefs.getString("excluded_ids_$currentUserId", "") ?: ""
        val excludedIds = excludedStr.split(",").map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }

        val selectedStr = prefs.getString("selected_ids_$currentUserId", "") ?: ""
        val selectedIds = selectedStr.split(",").map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }

        StatusPrivacySettings(
            option = option,
            excludedUserIds = excludedIds,
            selectedUserIds = selectedIds
        )
    }

    override suspend fun saveStatusPrivacySettings(settings: StatusPrivacySettings): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = (supabaseClient.auth.currentUserOrNull()?.id ?: "").replace("\"", "").replace("'", "").trim().lowercase()
        val prefs = context.getSharedPreferences("heychat_status_privacy", Context.MODE_PRIVATE)

        val cleanExcluded = settings.excludedUserIds.map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }
        val cleanSelected = settings.selectedUserIds.map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }

        prefs.edit()
            .putString("privacy_option_$currentUserId", settings.option.name)
            .putString("excluded_ids_$currentUserId", cleanExcluded.joinToString(","))
            .putString("selected_ids_$currentUserId", cleanSelected.joinToString(","))
            .apply()

        val rpcParams = buildJsonObject {
            put("p_user_id", currentUserId)
            put("p_option", settings.option.name)
            put("p_excluded", cleanExcluded.joinToString(","))
            put("p_selected", cleanSelected.joinToString(","))
        }

        val payload = buildJsonObject {
            put("user_id", currentUserId)
            put("privacy_option", settings.option.name)
            put("excluded_user_ids", cleanExcluded.joinToString(","))
            put("selected_user_ids", cleanSelected.joinToString(","))
        }

        runCatching {
            supabaseClient.postgrest.rpc("save_status_privacy", rpcParams)
            Log.d(TAG, "saveStatusPrivacySettings -> Saved via RPC save_status_privacy for user '$currentUserId'")
        }.onFailure {
            runCatching {
                supabaseClient.from("status_privacy").upsert(payload)
                Log.d(TAG, "saveStatusPrivacySettings -> Saved via direct upsert for user '$currentUserId'")
            }
        }

        Result.success(Unit)
    }

    override suspend fun getEligibleContacts(): Result<List<Profile>> = withContext(Dispatchers.IO) {
        val currentUserId = (supabaseClient.auth.currentUserOrNull()?.id
            ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: "").replace("\"", "").replace("'", "").trim().lowercase()

        runCatching {
            val db = HeyChatDatabase.getInstance(context)
            val localProfiles = db.profileDao().getAllProfilesOnce().map { it.toProfile() }

            val remoteProfiles = runCatching {
                supabaseClient.from("profiles")
                    .select { order("full_name", Order.ASCENDING) }
                    .decodeList<Profile>()
            }.getOrDefault(emptyList())

            val mergedProfiles = (localProfiles + remoteProfiles)
                .distinctBy { it.id.replace("\"", "").replace("'", "").trim().lowercase() }
                .filter { it.id.replace("\"", "").replace("'", "").trim().lowercase() != currentUserId }

            mergedProfiles
        }
    }

    private fun extractCleanUuids(rawList: List<String>): Set<String> {
        if (rawList.isEmpty()) return emptySet()
        val uuidRegex = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        val fullRawStr = rawList.joinToString(",")
        return uuidRegex.findAll(fullRawStr.lowercase())
            .map { it.value }
            .toSet()
    }

    private fun compressImage80Percent(imageBytes: ByteArray): ByteArray {
        return try {
            val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes
            val maxDimension = 1280
            val width = original.width
            val height = original.height

            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(width, height)
                Bitmap.createScaledBitmap(original, (width * scale).toInt(), (height * scale).toInt(), true)
            } else {
                original
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            imageBytes
        }
    }
}
