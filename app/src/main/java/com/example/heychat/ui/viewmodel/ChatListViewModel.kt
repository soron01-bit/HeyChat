package com.example.heychat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heychat.BuildConfig
import com.example.heychat.data.Chat
import com.example.heychat.data.Message
import com.example.heychat.data.MessageStatus
import com.example.heychat.data.User
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.models.AppConfig
import com.example.heychat.data.models.UserConversation
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.ChatListRepository
import com.example.heychat.data.repository.ChatListRepositoryImpl
import com.example.heychat.data.repository.SettingsRepositoryImpl
import com.example.heychat.service.PresenceManager
import com.example.heychat.ui.components.formatMessageTime
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ChatListViewModel(
    application: Application
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "HeyChat_ChatList"
        const val UPDATE_TAG = "HeyChat_Update"
    }

    private val chatListRepository: ChatListRepository = ChatListRepositoryImpl(application)

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _appUpdateConfig = MutableStateFlow<AppConfig?>(null)
    val appUpdateConfig: StateFlow<AppConfig?> = _appUpdateConfig.asStateFlow()

    init {
        observeRoomConversations()
        loadUserConversations()
        checkForAppUpdates()
    }

    private fun checkForAppUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(3000L) {
                    val list = supabaseClient.from("app_config")
                        .select()
                        .decodeList<AppConfig>()

                    val config = list.firstOrNull()
                    if (config != null && BuildConfig.VERSION_CODE < config.latestVersionCode) {
                        Log.d(UPDATE_TAG, "App update available! Current: ${BuildConfig.VERSION_CODE}, Latest: ${config.latestVersionCode}, Force: ${config.forceUpdate}")
                        _appUpdateConfig.value = config
                    }
                }
            }.onFailure { throwable ->
                Log.w(UPDATE_TAG, "Failed to check for app updates: ${throwable.message}")
            }
        }
    }

    fun dismissUpdateDialog() {
        _appUpdateConfig.value = null
    }

    fun clearUnreadOnChatOpened(conversationId: String) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        if (cleanConvId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val conversationDao = HeyChatDatabase.getInstance(getApplication()).conversationDao()
            conversationDao.clearUnreadCount(cleanConvId)
            Log.d(TAG, "clearUnreadOnChatOpened -> Cleared unread count in Room for conversation '$cleanConvId'")
        }
    }

    fun toggleMuteConversation(conversationId: String, isMuted: Boolean) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        if (cleanConvId.isBlank()) return
        viewModelScope.launch {
            chatListRepository.toggleMuteConversation(cleanConvId, isMuted)
        }
    }

    fun deleteConversation(conversationId: String) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        if (cleanConvId.isBlank()) return
        viewModelScope.launch {
            chatListRepository.deleteConversation(cleanConvId)
        }
    }

    fun blockUser(otherUserId: String) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return
        val cleanOtherId = otherUserId.replace("\"", "").replace("'", "").trim()

        viewModelScope.launch(Dispatchers.IO) {
            val settingsRepo = SettingsRepositoryImpl()
            settingsRepo.blockUser(currentUserId, cleanOtherId)
            Log.d(TAG, "blockUser SUCCESS -> Blocked '$cleanOtherId'")
        }
    }

    fun unblockUser(otherUserId: String) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return
        val cleanOtherId = otherUserId.replace("\"", "").replace("'", "").trim()

        viewModelScope.launch(Dispatchers.IO) {
            val settingsRepo = SettingsRepositoryImpl()
            settingsRepo.unblockUser(currentUserId, cleanOtherId)
            Log.d(TAG, "unblockUser SUCCESS -> Unblocked '$cleanOtherId'")
        }
    }

    private fun observeRoomConversations() {
        viewModelScope.launch {
            combine(
                chatListRepository.observeConversations(),
                PresenceManager.onlineUsers
            ) { conversations, onlineSet ->
                val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
                    ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
                    ?: ""
                conversations.map { it.toChatModel(currentUserId, onlineSet) }
            }.collect { mappedChats ->
                _chats.value = mappedChats
            }
        }
    }

    fun loadUserConversations() {
        viewModelScope.launch {
            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
                ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
                ?: run {
                    Log.w(TAG, "loadUserConversations ABORTED -> Auth session not ready")
                    _isLoading.value = false
                    return@launch
                }

            Log.d(TAG, "loadUserConversations EXECUTING for currentUserId: '$currentUserId'")
            _isLoading.value = true

            chatListRepository.syncUserConversations(currentUserId)
            _isLoading.value = false

            // 1. Activate Supabase Realtime WebSocket Listener for instant Chat List updates!
            launch {
                chatListRepository.subscribeToUserChatUpdates(currentUserId)
                    .collect {
                        Log.d(TAG, "Realtime Chat List trigger received -> Syncing conversations into Room")
                        chatListRepository.syncUserConversations(currentUserId)
                    }
            }

            // 2. High-speed 3-second background polling loop while sitting on Chat List
            launch {
                while (isActive) {
                    delay(3000L)
                    chatListRepository.syncUserConversations(currentUserId)
                }
            }
        }
    }

    private fun UserConversation.toChatModel(
        currentUserId: String,
        onlineSet: Set<String>
    ): Chat {
        val cleanOtherId = otherUserId.replace("\"", "").replace("'", "").trim()
        val isOnline = cleanOtherId.isNotBlank() && onlineSet.contains(cleanOtherId)

        val isMe = lastMessageSenderId == currentUserId
        val msgStatus = when (lastMessageStatus?.lowercase()) {
            "delivered" -> MessageStatus.DELIVERED
            "seen" -> MessageStatus.READ
            else -> MessageStatus.SENT
        }

        return Chat(
            id = conversationId,
            contact = User(
                id = otherUserId,
                name = cleanOtherUserName,
                phoneNumber = "",
                status = "Hey there! I am using Hey Chat 💬",
                avatarUrl = otherUserAvatar,
                isOnline = isOnline
            ),
            lastMessage = Message(
                id = lastMessageId ?: conversationId,
                senderId = lastMessageSenderId ?: "",
                text = lastMessageText,
                timestamp = formatMessageTime(lastMessageTime),
                isFromMe = isMe,
                status = msgStatus
            ),
            unreadCount = unreadCount.toInt(),
            isMuted = isMuted,
            isFavorite = isFavorite
        )
    }

    fun toggleFavoriteConversation(conversationId: String, isFavorite: Boolean) {
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim().lowercase()
        if (cleanConvId.isBlank()) return
        viewModelScope.launch {
            chatListRepository.toggleFavoriteConversation(cleanConvId, isFavorite)
        }
    }

    fun toggleFavoriteConversationsBulk(conversationIds: List<String>, isFavorite: Boolean) {
        val cleanIds = conversationIds.map { it.replace("\"", "").replace("'", "").trim().lowercase() }.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return
        viewModelScope.launch {
            chatListRepository.toggleFavoriteConversationsBulk(cleanIds, isFavorite)
        }
    }
}
