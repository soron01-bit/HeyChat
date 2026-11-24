package com.example.heychat.ui.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heychat.data.local.AppTheme
import com.example.heychat.data.local.SettingsDataStore
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.ProfileEntity
import com.example.heychat.data.models.BlockedUser
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.SettingsRepository
import com.example.heychat.data.repository.SettingsRepositoryImpl
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Idle : SettingsUiState
    data object Loading : SettingsUiState
    data class Success(val message: String) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl()
    private val profileDao = HeyChatDatabase.getInstance(application).profileDao()

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers.asStateFlow()

    val currentTheme = dataStore.themeFlow
    val currentWallpaper = dataStore.wallpaperFlow
    val notifMessage = dataStore.notifMessageFlow
    val notifGroup = dataStore.notifGroupFlow
    val notifCall = dataStore.notifCallFlow
    val notifSound = dataStore.notifSoundFlow
    val notifVibrate = dataStore.notifVibrateFlow

    val profileFlow: Flow<Profile?> = flow {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
        if (userId.isNotBlank()) {
            profileDao.getProfileFlow(userId).collect { entity ->
                emit(entity?.toProfile())
            }
        }
    }.flowOn(Dispatchers.IO)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.setTheme(theme)
            _uiState.value = SettingsUiState.Success("Theme updated to ${theme.name}")
        }
    }

    fun setWallpaper(wallpaper: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.setWallpaper(wallpaper)
            _uiState.value = SettingsUiState.Success("Wallpaper updated")
        }
    }

    fun setNotifSetting(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.setNotifSetting(key, value)
        }
    }

    private val _usernameError = MutableStateFlow<String?>(null)
    val usernameError: StateFlow<String?> = _usernameError.asStateFlow()

    fun checkUsernameAvailable(username: String, currentUsername: String?, onResult: (Boolean, String?) -> Unit) {
        val clean = username.replace("@", "").trim().lowercase()
        val currentClean = currentUsername?.replace("@", "")?.trim()?.lowercase() ?: ""

        if (clean.isNotBlank() && clean == currentClean) {
            onResult(true, null)
            return
        }

        if (clean.length < 3) {
            onResult(false, "Username must be at least 3 characters.")
            return
        }

        if (!clean.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            onResult(false, "Only lowercase letters, numbers, and underscores allowed.")
            return
        }

        viewModelScope.launch {
            val result = settingsRepository.checkUsernameAvailable(clean)
            result.onSuccess { available ->
                if (available) {
                    onResult(true, null)
                } else {
                    onResult(false, "Username @$clean is already taken.")
                }
            }.onFailure {
                onResult(false, "Error checking username.")
            }
        }
    }

    fun updateProfile(userId: String, fullName: String, username: String?, about: String, newAvatarBytes: ByteArray?) {
        val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
        val cleanUsername = username?.replace("@", "")?.trim()?.lowercase()?.ifBlank { null }
        val authUser = supabaseClient.auth.currentUserOrNull()
        val userEmail = authUser?.email ?: ""

        if (cleanUserId.isBlank()) {
            _uiState.value = SettingsUiState.Error("Invalid user session.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SettingsUiState.Loading
            var avatarUrl: String? = null

            if (newAvatarBytes != null) {
                val uploadResult = settingsRepository.uploadAvatar(cleanUserId, newAvatarBytes)
                uploadResult.onSuccess { url -> avatarUrl = url }
                    .onFailure { throwable ->
                        _uiState.value = SettingsUiState.Error(parseSettingsError(throwable))
                        return@launch
                    }
            }

            val existingProfile = profileDao.getProfile(cleanUserId)?.toProfile()
            val updatedProfile = Profile(
                id = cleanUserId,
                email = userEmail.ifBlank { existingProfile?.email ?: "" },
                fullName = fullName.trim(),
                username = cleanUsername ?: existingProfile?.username,
                about = about.trim(),
                avatarUrl = avatarUrl ?: existingProfile?.avatarUrl
            )

            val result = settingsRepository.updateProfile(updatedProfile)
            result.onSuccess {
                profileDao.upsertProfile(ProfileEntity.fromProfile(updatedProfile))
                _uiState.value = SettingsUiState.Success("Profile updated successfully!")
            }.onFailure { throwable ->
                _uiState.value = SettingsUiState.Error("Failed to update profile: ${throwable.message}")
            }
        }
    }

    fun changePassword(newPassword: String) {
        if (newPassword.length < 6) {
            _uiState.value = SettingsUiState.Error("Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SettingsUiState.Loading
            val result = settingsRepository.updatePassword(newPassword)
            result.onSuccess {
                _uiState.value = SettingsUiState.Success("Password changed successfully!")
            }.onFailure { throwable ->
                _uiState.value = SettingsUiState.Error("Failed to change password: ${throwable.message}")
            }
        }
    }

    fun changeEmail(newEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SettingsUiState.Loading
            val result = settingsRepository.updateEmail(newEmail.trim())
            result.onSuccess {
                _uiState.value = SettingsUiState.Success("Confirmation link sent to $newEmail. Please check your inbox.")
            }.onFailure { throwable ->
                _uiState.value = SettingsUiState.Error("Failed to update email: ${throwable.message}")
            }
        }
    }

    fun updatePrivacySettings(currentProfile: Profile, showLastSeen: Boolean, disappearingDuration: String, twoStep: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SettingsUiState.Loading
            val updated = currentProfile.copy(
                showLastSeen = showLastSeen,
                disappearingMessagesDuration = disappearingDuration,
                twoStepEnabled = twoStep
            )
            val result = settingsRepository.updateProfile(updated)
            result.onSuccess {
                profileDao.upsertProfile(ProfileEntity.fromProfile(updated))
                _uiState.value = SettingsUiState.Success("Privacy settings saved.")
            }.onFailure { throwable ->
                _uiState.value = SettingsUiState.Error("Failed to save settings: ${throwable.message}")
            }
        }
    }

    fun fetchBlockedUsers(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = settingsRepository.getBlockedUsers(userId)
            result.onSuccess { list ->
                _blockedUsers.value = list
            }
        }
    }

    fun blockUser(blockerId: String, blockedId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SettingsUiState.Loading
            val result = settingsRepository.blockUser(blockerId, blockedId)
            result.onSuccess {
                _uiState.value = SettingsUiState.Success("User blocked successfully.")
                fetchBlockedUsers(blockerId)
            }.onFailure { throwable ->
                _uiState.value = SettingsUiState.Error("Failed to block user: ${throwable.message}")
            }
        }
    }

    fun unblockUser(blockerId: String, blockedId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = settingsRepository.unblockUser(blockerId, blockedId)
            result.onSuccess {
                _uiState.value = SettingsUiState.Success("User unblocked.")
                fetchBlockedUsers(blockerId)
            }
        }
    }

    fun clearChatHistory(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SettingsUiState.Loading
            val result = settingsRepository.clearChatHistory(userId)
            result.onSuccess {
                _uiState.value = SettingsUiState.Success("Chat history cleared.")
            }.onFailure { throwable ->
                _uiState.value = SettingsUiState.Error("Failed to clear chat history: ${throwable.message}")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = SettingsUiState.Idle
    }

    private fun parseSettingsError(throwable: Throwable): String {
        val msg = throwable.message ?: "An unexpected error occurred"
        return when {
            msg.contains("row-level security", ignoreCase = true) || msg.contains("42501", ignoreCase = true) || msg.contains("policy", ignoreCase = true) ->
                "Supabase Storage RLS Permission Error: Please run the Storage RLS SQL script in Supabase SQL Editor."
            else -> msg.lines().firstOrNull() ?: msg
        }
    }
}
