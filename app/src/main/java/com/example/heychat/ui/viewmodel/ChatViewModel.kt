package com.example.heychat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.models.MessageModel
import com.example.heychat.data.models.MessageReactionModel
import com.example.heychat.data.models.MessageStatus
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.ChatListRepository
import com.example.heychat.data.repository.ChatListRepositoryImpl
import com.example.heychat.data.repository.ChatRepository
import com.example.heychat.data.repository.ChatRepositoryImpl
import com.example.heychat.data.repository.SearchRepositoryImpl
import com.example.heychat.data.repository.SettingsRepositoryImpl
import com.example.heychat.service.PresenceManager
import io.github.jan.supabase.gotrue.auth
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatViewModel(
    application: Application
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "HeyChat_ChatViewModel"
        const val POLL_TAG = "HeyChat_Poll"
        const val REALTIME_TAG = "HeyChat_Realtime"
    }

    private val chatRepository: ChatRepository = ChatRepositoryImpl(application)
    private val chatListRepository: ChatListRepository = ChatListRepositoryImpl(application)

    private val _participantProfile = MutableStateFlow<Profile?>(null)
    val participantProfile: StateFlow<Profile?> = _participantProfile.asStateFlow()

    val isParticipantOnline: StateFlow<Boolean> = combine(
        _participantProfile,
        PresenceManager.onlineUsers
    ) { profile, onlineSet ->
        val participantId = profile?.id?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
        val isOnlineInRealtime = participantId.isNotBlank() && onlineSet.contains(participantId)
        val isOnlineInDatabase = profile?.isOnline == true
        val isOnline = isOnlineInRealtime || isOnlineInDatabase
        Log.d("HeyChat_Presence", "Header decision for otherUserId=$participantId: Realtime=$isOnlineInRealtime, DB=$isOnlineInDatabase => finalIsOnline=$isOnline")
        isOnline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
    val messages: StateFlow<List<MessageModel>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<MessageModel?>(null)
    val replyingToMessage: StateFlow<MessageModel?> = _replyingToMessage.asStateFlow()

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    fun checkBlockedStatus(otherUserId: String) {
        val cleanOtherId = otherUserId.replace("\"", "").replace("'", "").trim()
        if (cleanOtherId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val db = HeyChatDatabase.getInstance(getApplication())
            val isLocallyBlocked = db.blockedUserDao().isUserBlocked(cleanOtherId)
            _isBlocked.value = isLocallyBlocked

            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return@launch
            val settingsRepo = SettingsRepositoryImpl(getApplication())
            val result = settingsRepo.getBlockedUsers(currentUserId)
            result.onSuccess { list ->
                val isUserBlocked = list.any { it.blockedId == cleanOtherId || it.blockedEmail == cleanOtherId }
                _isBlocked.value = isUserBlocked
            }
        }
    }

    fun blockUser(otherUserId: String) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return
        val cleanOtherId = otherUserId.replace("\"", "").replace("'", "").trim()
        if (cleanOtherId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val settingsRepo = SettingsRepositoryImpl(getApplication())
            settingsRepo.blockUser(currentUserId, cleanOtherId)
            _isBlocked.value = true
        }
    }

    fun unblockUser(otherUserId: String) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return
        val cleanOtherId = otherUserId.replace("\"", "").replace("'", "").trim()
        if (cleanOtherId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val settingsRepo = SettingsRepositoryImpl(getApplication())
            settingsRepo.unblockUser(currentUserId, cleanOtherId)
            _isBlocked.value = false
        }
    }

    private var activeConversationId: String = ""

    fun setReplyingToMessage(message: MessageModel?) {
        _replyingToMessage.value = message
    }

    fun setInitialParticipant(name: String?, avatarUrl: String?, userId: String?) {
        val cleanUserId = userId?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
        if (cleanUserId.isNotBlank()) {
            checkBlockedStatus(cleanUserId)
        }
        if (!name.isNullOrBlank()) {
            _participantProfile.value = Profile(
                id = cleanUserId,
                email = "",
                fullName = name,
                avatarUrl = avatarUrl
            )
        }
    }

    private val _messageReactions = MutableStateFlow<Map<String, List<MessageReactionModel>>>(emptyMap())
    val messageReactions: StateFlow<Map<String, List<MessageReactionModel>>> = _messageReactions.asStateFlow()

    fun toggleReaction(messageId: String, emoji: String) {
        val cleanMsgId = messageId.replace("\"", "").replace("'", "").trim()
        if (cleanMsgId.isBlank() || emoji.isBlank()) return

        viewModelScope.launch {
            chatRepository.toggleReaction(cleanMsgId, emoji)
        }
    }

    private fun parseIsoToMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            val cleanIso = isoString.replace("Z", "").substringBefore(".")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(cleanIso)?.time ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }

    private var reactionsJob: Job? = null

    private fun observeReactions(msgList: List<MessageModel>) {
        reactionsJob?.cancel()
        val ids = msgList.mapNotNull { it.id }.filter { it.isNotBlank() }
        if (ids.isEmpty()) {
            _messageReactions.value = emptyMap()
            return
        }

        reactionsJob = viewModelScope.launch {
            chatRepository.observeReactionsForMessages(ids).collect { reactionMap ->
                _messageReactions.value = reactionMap
                Log.d(REALTIME_TAG, "Reactions UI state updated for ${ids.size} messages (reaction groups=${reactionMap.size})")
            }
        }
    }

    private var activeRoomSyncJob: Job? = null

    fun loadChat(conversationId: String) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        if (cleanConvId.isBlank()) return

        if (cleanConvId != activeConversationId) {
            activeConversationId = cleanConvId
            // Observe Room DB messages Flow for instant offline rendering
            viewModelScope.launch {
                chatRepository.observeMessages(cleanConvId).collect { roomList ->
                    val currentRamList = _messages.value
                    val roomMap = roomList.associateBy { it.id }
                    val updatedRam = currentRamList.map { ramMsg -> roomMap[ramMsg.id] ?: ramMsg }
                    val merged = (updatedRam + roomList).distinctBy { it.id }
                    val sorted = merged.sortedByDescending { parseIsoToMillis(it.createdAt) }
                    _messages.value = ArrayList(sorted)
                    observeReactions(sorted)
                }
            }

            // Observe Room DB conversation entity Flow for instant header name & avatar
            viewModelScope.launch {
                chatListRepository.observeConversationById(cleanConvId).collect { conv ->
                    if (conv != null && !conv.otherUserName.isNullOrBlank()) {
                        _participantProfile.value = Profile(
                            id = conv.otherUserId,
                            email = "",
                            fullName = conv.otherUserName,
                            avatarUrl = conv.otherUserAvatar
                        )
                    }
                }
            }
        }

        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
        _isLoading.value = false

        // Mark messages as seen IMMEDIATELY upon opening chat room!
        markAsSeen(cleanConvId)

        // 1. Launch Realtime subscription IMMEDIATELY with sub-10ms direct RAM UI state update!
        viewModelScope.launch {
            Log.d(REALTIME_TAG, "ChatViewModel launching Realtime subscription for '$cleanConvId'")
            chatRepository.subscribeToMessages(cleanConvId).collect { newMessage ->
                if (!newMessage.id.isNullOrBlank()) {
                    val currentList = _messages.value
                    val updatedList = (listOf(newMessage) + currentList.filterNot { it.id == newMessage.id })
                    val sorted = updatedList.sortedByDescending { parseIsoToMillis(it.createdAt) }
                    _messages.value = ArrayList(sorted)
                    Log.d(REALTIME_TAG, "Instant <10ms RAM UI update for incoming msg '${newMessage.id}'")
                }
            }
        }

        // 2. Launch participant profile fetch in its own independent coroutine
        viewModelScope.launch {
            chatRepository.getConversationParticipant(cleanConvId, currentUserId)
                .onSuccess { profile ->
                    _participantProfile.value = profile
                    checkBlockedStatus(profile.id)
                }
        }

        // 3. Launch continuous 1-second active room refresh loop while in chat room!
        activeRoomSyncJob?.cancel()
        activeRoomSyncJob = viewModelScope.launch {
            while (isActive) {
                val otherId = _participantProfile.value?.id ?: ""
                if (otherId.isNotBlank()) {
                    val db = HeyChatDatabase.getInstance(getApplication())
                    val isLocallyBlocked = db.blockedUserDao().isUserBlocked(otherId)
                    if (isLocallyBlocked) {
                        delay(2000L)
                        continue
                    }
                }
                chatRepository.syncNewMessages(cleanConvId)
                markAsSeen(cleanConvId)
                delay(1000L) // Continuous 1-second background refresh while chatting!
            }
        }
    }



    fun catchUpSync(conversationId: String) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        if (cleanConvId.isBlank()) return
        viewModelScope.launch {
            chatRepository.syncNewMessages(cleanConvId)
            markAsSeen(cleanConvId)
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: run {
            _sendError.value = "Error: Not authenticated. Please log in again."
            return
        }

        if (text.isBlank() || cleanConvId.isBlank()) {
            _sendError.value = "Error: Invalid message text or conversation ID."
            return
        }

        val cleanText = text.trim()
        val replyId = _replyingToMessage.value?.id
        _replyingToMessage.value = null

        Log.d("HeyChat_Latency", "[UI_TAP] Send button tapped for text message in conv '$cleanConvId' at timestamp=${System.currentTimeMillis()}")

        viewModelScope.launch {
            _sendError.value = null
            val result = chatRepository.sendMessage(cleanConvId, currentUserId, cleanText, replyId)
            result.onFailure { throwable ->
                val errorMsg = throwable.message ?: ""
                val isBlockedError = errorMsg.contains("42501", ignoreCase = true) ||
                    errorMsg.contains("row-level security", ignoreCase = true) ||
                    errorMsg.contains("policy", ignoreCase = true)

                if (isBlockedError) {
                    Log.d(TAG, "Message sent by blocked user -> Suppressed RLS error to keep block stealthy (Single tick 🗸)")
                } else {
                    val parsed = parseMessageError(throwable)
                    Log.e(TAG, "Failed to insert message into Supabase: $parsed", throwable)
                    _sendError.value = parsed
                }
            }
        }
    }

    fun sendPhotoMessage(conversationId: String, imageBytes: ByteArray, replyToMessageId: String? = null) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        val cleanReplyId = replyToMessageId?.replace("\"", "")?.replace("'", "")?.trim()?.ifBlank { null }
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: run {
            _sendError.value = "Error: Not authenticated. Please log in again."
            return
        }

        if (cleanConvId.isBlank() || imageBytes.isEmpty()) return

        _replyingToMessage.value = null

        viewModelScope.launch {
            _sendError.value = null
            val result = chatRepository.sendPhotoMessage(cleanConvId, currentUserId, imageBytes, cleanReplyId)
            result.onFailure { throwable ->
                val errorMsg = throwable.message ?: ""
                val isBlockedError = errorMsg.contains("42501", ignoreCase = true) ||
                    errorMsg.contains("row-level security", ignoreCase = true) ||
                    errorMsg.contains("policy", ignoreCase = true)

                if (isBlockedError) {
                    Log.d(TAG, "Photo sent by blocked user -> Suppressed RLS error to keep block stealthy (Single tick 🗸)")
                } else {
                    val parsed = parseMessageError(throwable)
                    Log.e(TAG, "Failed to send photo message: $parsed", throwable)
                    _sendError.value = "Failed to send photo: $parsed"
                }
            }
        }
    }

    fun sendVoiceMessage(conversationId: String, audioBytes: ByteArray, durationSec: Int) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: run {
            _sendError.value = "Error: Not authenticated. Please log in again."
            return
        }

        if (cleanConvId.isBlank() || audioBytes.isEmpty()) return

        viewModelScope.launch {
            _sendError.value = null
            val replyId = replyingToMessage.value?.id
            _replyingToMessage.value = null

            val result = chatRepository.sendVoiceMessage(cleanConvId, currentUserId, audioBytes, durationSec, replyId)
            result.onFailure { throwable ->
                val parsed = parseMessageError(throwable)
                Log.e(TAG, "Failed to send voice message: $parsed", throwable)
                _sendError.value = "Failed to send voice note: $parsed"
            }
        }
    }

    fun deleteMessageForMe(messageId: String) {
        val cleanMsgId = messageId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanMsgId.isBlank()) return

        // Direct <10ms RAM UI state update (Message vanishes instantly on Frame 1!)
        val currentList = _messages.value
        _messages.value = currentList.filterNot {
            val msgId = it.id?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
            msgId == cleanMsgId
        }

        viewModelScope.launch {
            chatRepository.deleteMessageForMe(cleanMsgId)
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        val cleanMsgId = messageId.replace("\"", "").replace("'", "").trim()
        if (cleanMsgId.isBlank()) return
        viewModelScope.launch {
            _sendError.value = null
            val result = chatRepository.deleteMessageForEveryone(cleanMsgId)
            result.onFailure { throwable ->
                val parsed = parseMessageError(throwable)
                Log.e(TAG, "Failed to delete message for everyone: $parsed", throwable)
                _sendError.value = "Failed to unsend message: $parsed"
            }
        }
    }

    fun deleteMessagesForMeBulk(messageIds: List<String>) {
        val cleanIds = messageIds.map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        if (cleanIds.isEmpty()) return

        // Direct <10ms RAM UI state update (Messages vanish instantly on Frame 1!)
        val currentList = _messages.value
        _messages.value = currentList.filterNot { msg ->
            val msgId = msg.id?.replace("\"", "")?.replace("'", "")?.trim()?.lowercase() ?: ""
            cleanIds.contains(msgId)
        }

        viewModelScope.launch {
            chatRepository.deleteMessagesForMeBulk(cleanIds.toList())
        }
    }

    fun deleteMessagesForEveryoneBulk(messageIds: List<String>) {
        val cleanIds = messageIds.map { it.replace("\"", "").replace("'", "").trim() }.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return
        viewModelScope.launch {
            _sendError.value = null
            val result = chatRepository.deleteMessagesForEveryoneBulk(cleanIds)
            result.onFailure { throwable ->
                val parsed = parseMessageError(throwable)
                Log.e(TAG, "Failed to unsend messages for everyone: $parsed", throwable)
                _sendError.value = "Failed to unsend messages: $parsed"
            }
        }
    }

    fun forwardMessages(targetContactIds: List<String>, messagesToForward: List<MessageModel>) {
        if (targetContactIds.isEmpty() || messagesToForward.isEmpty()) return
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return

        viewModelScope.launch(Dispatchers.IO) {
            targetContactIds.forEach { contactId ->
                runCatching {
                    val searchRepo = SearchRepositoryImpl(getApplication())
                    val convResult = searchRepo.getOrCreateConversation(currentUserId, contactId)
                    val convId = convResult.getOrNull()
                    if (!convId.isNullOrBlank()) {
                        messagesToForward.forEach { msg ->
                            val rawMediaUrl = msg.mediaUrl.orEmpty().trim()
                            val isPhoto = rawMediaUrl.isNotBlank() && !msg.isDeleted

                            if (isPhoto) {
                                val localFile = File(rawMediaUrl)
                                if (localFile.exists() && localFile.length() > 0) {
                                    val bytes = localFile.readBytes()
                                    chatRepository.sendPhotoMessage(convId, currentUserId, bytes)
                                } else {
                                    if (msg.content.isNotBlank() && !msg.content.contains("media_cache")) {
                                        chatRepository.sendMessage(convId, currentUserId, msg.content)
                                    }
                                }
                            } else if (msg.content.isNotBlank() && !msg.isDeleted && !msg.content.contains("media_cache")) {
                                chatRepository.sendMessage(convId, currentUserId, msg.content)
                            }
                        }
                    }
                }
            }
        }
    }

    fun clearError() {
        _sendError.value = null
    }

    fun runSafetyNetSync(conversationId: String) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        if (cleanConvId.isBlank()) return
        viewModelScope.launch {
            chatRepository.runSafetyNetSync(cleanConvId)
        }
    }

    fun markAsSeen(conversationId: String) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: return
        val cleanCurrentUserId = currentUserId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanConvId.isBlank()) return

        // Update RAM UI state ONLY for messages sent by the other user!
        val currentList = _messages.value
        if (currentList.any { it.senderId != cleanCurrentUserId && it.status != MessageStatus.SEEN }) {
            _messages.value = currentList.map { msg ->
                if (msg.senderId != cleanCurrentUserId) msg.copy(status = MessageStatus.SEEN) else msg
            }
        }

        viewModelScope.launch {
            chatRepository.markMessagesAsSeen(cleanConvId, cleanCurrentUserId)
        }
    }

    private fun parseMessageError(throwable: Throwable): String {
        val msg = throwable.message ?: "Database insert error"
        return when {
            msg.contains("42501", ignoreCase = true) || msg.contains("row-level security", ignoreCase = true) || msg.contains("policy", ignoreCase = true) ->
                "Database RLS Permission Error: Please run the RLS SQL script in Supabase SQL Editor."
            msg.contains("23503", ignoreCase = true) || msg.contains("foreign key", ignoreCase = true) ->
                "Invalid conversation or user ID."
            else -> msg.lines().firstOrNull() ?: msg
        }
    }
}
