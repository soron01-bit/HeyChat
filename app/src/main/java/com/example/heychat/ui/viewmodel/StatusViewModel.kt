package com.example.heychat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heychat.data.models.Profile
import com.example.heychat.data.models.StatusItem
import com.example.heychat.data.models.StatusPrivacySettings
import com.example.heychat.data.models.StatusView
import com.example.heychat.data.models.UserStatusGroup
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.StatusRepository
import com.example.heychat.data.repository.StatusRepositoryImpl
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatusViewModel(
    application: Application
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "HeyChat_StatusVM"
    }

    private val statusRepository: StatusRepository = StatusRepositoryImpl(application)

    private val _myStatuses = MutableStateFlow<List<StatusItem>>(emptyList())
    val myStatuses: StateFlow<List<StatusItem>> = _myStatuses.asStateFlow()

    private val _recentStatusGroups = MutableStateFlow<List<UserStatusGroup>>(emptyList())
    val recentStatusGroups: StateFlow<List<UserStatusGroup>> = _recentStatusGroups.asStateFlow()

    private val _statusViewers = MutableStateFlow<List<StatusView>>(emptyList())
    val statusViewers: StateFlow<List<StatusView>> = _statusViewers.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _privacySettings = MutableStateFlow(StatusPrivacySettings())
    val privacySettings: StateFlow<StatusPrivacySettings> = _privacySettings.asStateFlow()

    private val _eligibleContacts = MutableStateFlow<List<Profile>>(emptyList())
    val eligibleContacts: StateFlow<List<Profile>> = _eligibleContacts.asStateFlow()

    init {
        observeStatuses()
        refreshStatuses()
        loadStatusPrivacySettings()
        loadEligibleContacts()
    }

    private fun observeStatuses() {
        viewModelScope.launch {
            statusRepository.observeLocalStatuses().collect { wrappers ->
                val wrapper = wrappers.firstOrNull()
                if (wrapper != null) {
                    _myStatuses.value = wrapper.myStatuses
                    _recentStatusGroups.value = wrapper.recentStatusGroups
                }
            }
        }

        viewModelScope.launch {
            statusRepository.subscribeToStatusRealtimeUpdates().collect {
                Log.d(TAG, "Realtime status update received -> Refreshing status list")
                refreshStatuses()
            }
        }
    }

    private fun getCurrentUserId(): String {
        return (supabaseClient.auth.currentUserOrNull()?.id
            ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: "").replace("\"", "").replace("'", "").trim().lowercase()
    }

    fun refreshStatuses() {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            statusRepository.fetchRecentStatuses(currentUserId)
        }
    }

    fun uploadStatusPhoto(imageBytes: ByteArray, caption: String = "") {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isBlank()) {
            Log.e(TAG, "uploadStatusPhoto FAILED -> User ID is blank")
            return
        }
        if (imageBytes.isEmpty()) {
            Log.e(TAG, "uploadStatusPhoto FAILED -> Image bytes empty")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isUploading.value = true
            statusRepository.uploadStatusPhoto(currentUserId, imageBytes, caption)
                .onSuccess {
                    Log.d(TAG, "uploadStatusPhoto SUCCESS -> Status published successfully")
                    refreshStatuses()
                }.onFailure { throwable ->
                    Log.e(TAG, "uploadStatusPhoto FAILED -> ${throwable.message}", throwable)
                }
            _isUploading.value = false
        }
    }

    fun markStatusAsViewed(statusId: String, reactionEmoji: String? = null) {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isBlank() || statusId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            statusRepository.markStatusAsViewed(statusId, currentUserId, reactionEmoji)
        }
    }

    fun sendStatusReply(statusItem: StatusItem, replyText: String) {
        if (replyText.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            statusRepository.sendStatusReply(statusItem, replyText)
                .onSuccess {
                    Log.d(TAG, "sendStatusReply SUCCESS -> Status reply sent to chat")
                }
        }
    }

    fun loadStatusViewers(statusId: String) {
        if (statusId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            statusRepository.getStatusViewers(statusId)
                .onSuccess { views ->
                    _statusViewers.value = views
                }
        }
    }

    fun deleteStatus(statusId: String) {
        if (statusId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            statusRepository.deleteStatus(statusId)
                .onSuccess {
                    Log.d(TAG, "deleteStatus SUCCESS -> Deleted status '$statusId'")
                    refreshStatuses()
                }
        }
    }

    fun loadStatusPrivacySettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = statusRepository.getStatusPrivacySettings()
            _privacySettings.value = settings
        }
    }

    fun saveStatusPrivacySettings(settings: StatusPrivacySettings) {
        viewModelScope.launch(Dispatchers.IO) {
            _privacySettings.value = settings
            statusRepository.saveStatusPrivacySettings(settings)
            refreshStatuses()
        }
    }

    fun loadEligibleContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            statusRepository.getEligibleContacts()
                .onSuccess { contacts ->
                    _eligibleContacts.value = contacts
                }
        }
    }
}
