package com.example.heychat.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.ProfileEntity
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.AuthRepository
import com.example.heychat.data.repository.AuthRepositoryImpl
import com.example.heychat.service.FcmTokenManager
import com.example.heychat.service.PresenceManager
import com.example.heychat.service.SessionManager
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasswordStrength {
    NONE, WEAK, MEDIUM, STRONG
}

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data class SignedUpUnverified(val email: String) : AuthState
    data class PasswordResetSent(val email: String) : AuthState
    data class ProfileSetupRequired(val userId: String, val email: String) : AuthState
    data class Authenticated(val profile: Profile) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel(
    application: Application
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "HeyChat_Auth"
    }

    private val authRepository: AuthRepository = AuthRepositoryImpl(application)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _resendTimer = MutableStateFlow(0)
    val resendTimer: StateFlow<Int> = _resendTimer.asStateFlow()

    private var currentEmail: String = ""
    private var currentUserId: String = ""
    private var timerJob: Job? = null

    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar: StateFlow<Boolean> = _isUploadingAvatar.asStateFlow()

    private val _avatarUploadMessage = MutableStateFlow<String?>(null)
    val avatarUploadMessage: StateFlow<String?> = _avatarUploadMessage.asStateFlow()

    fun clearAvatarUploadMessage() {
        _avatarUploadMessage.value = null
    }

    init {
        checkExistingSession()
        viewModelScope.launch {
            SessionManager.sessionExpiredEvent.collect {
                Log.e(TAG, "AuthViewModel received sessionExpiredEvent -> Clearing cached session and navigating to Login")
                val prefs = getApplication<Application>().getSharedPreferences("heychat_session_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("last_logged_in_user_id").apply()
                _authState.value = AuthState.Idle
            }
        }
    }

    private fun checkExistingSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("heychat_session_prefs", Context.MODE_PRIVATE)
            val lastKnownUserId = prefs.getString("last_logged_in_user_id", null)?.replace("\"", "")?.replace("'", "")?.trim()

            Log.d(TAG, "checkExistingSession STARTED -> cachedUserId: '$lastKnownUserId'")

            // 1. OFFLINE-FIRST: Read Room ProfileDao FIRST
            if (!lastKnownUserId.isNullOrBlank()) {
                val profileDao = HeyChatDatabase.getInstance(getApplication()).profileDao()
                val cachedEntity = profileDao.getProfile(lastKnownUserId)
                val cachedProfile = cachedEntity?.toProfile() ?: Profile(
                    id = lastKnownUserId,
                    email = "",
                    fullName = "User"
                )

                Log.d(TAG, "Offline-first check -> Found cached userId '$lastKnownUserId' with profile '${cachedProfile.fullName}'. Setting AuthState.Authenticated immediately.")
                _authState.value = AuthState.Authenticated(cachedProfile)
            } else {
                Log.d(TAG, "Offline-first check -> No cached userId found in storage. Staying on AuthState.Loading while SDK initializes.")
            }

            // 2. Drive remaining session lifecycle from Supabase sessionStatus
            launch {
                supabaseClient.auth.sessionStatus.collect { status ->
                    val statusTypeName = when (status) {
                        is SessionStatus.Authenticated -> "Authenticated"
                        is SessionStatus.NotAuthenticated -> "NotAuthenticated"
                        is SessionStatus.NetworkError -> "NetworkError"
                        else -> status.javaClass.simpleName
                    }

                    val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
                        ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
                        ?: ""

                    Log.d(TAG, "Supabase SessionStatus changed -> $statusTypeName (userId: '$currentUserId')")

                    when (status) {
                        is SessionStatus.Authenticated -> {
                            if (currentUserId.isNotBlank()) {
                                prefs.edit().putString("last_logged_in_user_id", currentUserId).apply()
                                FcmTokenManager.syncFcmTokenWithSupabase()
                                PresenceManager.onAppStart()
                                checkOrSetupProfile(currentUserId)
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            delay(350L)
                            val recheckUser = supabaseClient.auth.currentUserOrNull()?.id
                                ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
                                ?: ""
                            val activeCachedId = prefs.getString("last_logged_in_user_id", null)

                            if (recheckUser.isBlank() && activeCachedId.isNullOrBlank()) {
                                Log.d(TAG, "SessionStatus -> Confirmed NotAuthenticated after 350ms grace period. Moving to AuthState.Idle (Login).")
                                _authState.value = AuthState.Idle
                            } else if (recheckUser.isNotBlank()) {
                                prefs.edit().putString("last_logged_in_user_id", recheckUser).apply()
                                checkOrSetupProfile(recheckUser)
                            } else {
                                Log.d(TAG, "SessionStatus -> NotAuthenticated but cached session exists ($activeCachedId). Staying Authenticated offline.")
                            }
                        }
                        is SessionStatus.NetworkError -> {
                            Log.w(TAG, "SessionStatus -> NetworkError. Remaining authenticated offline.")
                        }
                        else -> {
                            // LoadingFromStorage / Initializing -> Stay on Loading or keep Authenticated if step 1 applied.
                        }
                    }
                }
            }
        }
    }

    fun refreshSessionOnResume() {
        viewModelScope.launch(Dispatchers.IO) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            Log.d(TAG, "[$time] App resumed -> Token refresh check STARTED")

            val currentSession = supabaseClient.auth.currentSessionOrNull()
            val user = supabaseClient.auth.currentUserOrNull() ?: currentSession?.user

            Log.d(TAG, "[$time] Session check -> sessionExists: ${currentSession != null}, user: '${user?.id}'")

            runCatching {
                supabaseClient.auth.refreshCurrentSession()
            }.onSuccess {
                val newTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                Log.d(TAG, "[$newTime] Refresh result -> SUCCESS! Token refreshed successfully for user '${user?.id}'")
                Log.d(TAG, "[$newTime] Final decision -> STAY LOGGED IN (Session active)")
                FcmTokenManager.syncFcmTokenWithSupabase()
                PresenceManager.onAppStart()
            }.onFailure { throwable ->
                val newTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                val errorMsg = throwable.message ?: ""
                val isExplicitServerRejection = errorMsg.contains("invalid_grant", ignoreCase = true) ||
                    errorMsg.contains("refresh_token_not_found", ignoreCase = true) ||
                    errorMsg.contains("invalid refresh token", ignoreCase = true) ||
                    errorMsg.contains("Already revoked", ignoreCase = true)

                if (isExplicitServerRejection) {
                    Log.e(TAG, "[$newTime] Refresh result -> SERVER EXPLICITLY REJECTED REFRESH TOKEN ($errorMsg)")
                    val prefs = getApplication<Application>().getSharedPreferences("heychat_session_prefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("last_logged_in_user_id").apply()
                    _authState.value = AuthState.Idle
                    Log.d(TAG, "[$newTime] Final decision -> SHOW LOGIN SCREEN (Token revoked/invalid on server)")
                } else {
                    Log.w(TAG, "[$newTime] Refresh result -> Network or transient error ($errorMsg). IGNORED.")
                    Log.d(TAG, "[$newTime] Final decision -> STAY LOGGED IN (Preserving authenticated state)")
                }
            }
        }
    }

    fun signUp(email: String, password: String, confirmPassword: String) {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters long.")
            return
        }
        if (password != confirmPassword) {
            _authState.value = AuthState.Error("Passwords do not match.")
            return
        }

        currentEmail = cleanEmail
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signUp(cleanEmail, password)
            result.onSuccess { userId ->
                if (userId != null) {
                    currentUserId = userId
                }
                _authState.value = AuthState.SignedUpUnverified(cleanEmail)
                startResendTimer(45)
            }.onFailure { throwable ->
                _authState.value = AuthState.Error(parseErrorMessage(throwable))
            }
        }
    }

    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            _authState.value = AuthState.Error("Please enter your password.")
            return
        }

        currentEmail = cleanEmail
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signIn(cleanEmail, password)
            result.onSuccess { userId ->
                currentUserId = userId
                if (authRepository.isEmailVerified()) {
                    checkOrSetupProfile(userId)
                } else {
                    _authState.value = AuthState.SignedUpUnverified(cleanEmail)
                }
            }.onFailure { throwable ->
                _authState.value = AuthState.Error(parseErrorMessage(throwable))
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.sendPasswordReset(cleanEmail)
            result.onSuccess {
                _authState.value = AuthState.PasswordResetSent(cleanEmail)
            }.onFailure { throwable ->
                _authState.value = AuthState.Error(parseErrorMessage(throwable))
            }
        }
    }

    fun resendVerificationEmail() {
        if (_resendTimer.value > 0 || currentEmail.isBlank()) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.resendVerificationEmail(currentEmail)
            result.onSuccess {
                _authState.value = AuthState.SignedUpUnverified(currentEmail)
                startResendTimer(45)
            }.onFailure { throwable ->
                _authState.value = AuthState.Error(parseErrorMessage(throwable))
            }
        }
    }

    fun checkVerificationAndContinue() {
        val userId = currentUserId.ifBlank { authRepository.getCurrentUserId() ?: "" }
        if (userId.isBlank()) {
            _authState.value = AuthState.Idle
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            if (authRepository.isEmailVerified()) {
                checkOrSetupProfile(userId)
            } else {
                _authState.value = AuthState.Error("Email is not verified yet. Please click the link in your email inbox.")
            }
        }
    }

    private fun checkOrSetupProfile(userId: String) {
        val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
        viewModelScope.launch(Dispatchers.IO) {
            FcmTokenManager.syncFcmTokenWithSupabase()
            Log.d(TAG, "checkOrSetupProfile STARTED for userId: '$cleanUserId'")

            val profileDao = HeyChatDatabase.getInstance(getApplication()).profileDao()
            val localEntity = profileDao.getProfile(cleanUserId)
            val localProfile = localEntity?.toProfile()

            // Fetch remote profile from Supabase
            val remoteProfile = runCatching {
                val list = supabaseClient.from("profiles")
                    .select {
                        filter {
                            eq("id", cleanUserId)
                        }
                    }.decodeList<Profile>()
                list.firstOrNull()
            }.getOrNull()

            if (remoteProfile != null && !remoteProfile.fullName.isNullOrBlank()) {
                profileDao.upsertProfile(ProfileEntity.fromProfile(remoteProfile))
                Log.d(TAG, "checkOrSetupProfile SUCCESS -> Valid profile found on server: '${remoteProfile.fullName}'")
                _authState.value = AuthState.Authenticated(remoteProfile)
                PresenceManager.onAppStart()
            } else if (localProfile != null && !localProfile.fullName.isNullOrBlank()) {
                Log.d(TAG, "checkOrSetupProfile SUCCESS -> Valid local profile found in Room: '${localProfile.fullName}'")
                _authState.value = AuthState.Authenticated(localProfile)
                PresenceManager.onAppStart()
            } else {
                val authEmail = supabaseClient.auth.currentUserOrNull()?.email ?: currentEmail
                Log.d(TAG, "checkOrSetupProfile -> No valid profile found. Navigating to PROFILE_SETUP for userId '$cleanUserId'")
                _authState.value = AuthState.ProfileSetupRequired(cleanUserId, authEmail)
            }
        }
    }

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isUsernameChecking = MutableStateFlow(false)
    val isUsernameChecking: StateFlow<Boolean> = _isUsernameChecking.asStateFlow()

    private val _isUsernameAvailable = MutableStateFlow<Boolean?>(null)
    val isUsernameAvailable: StateFlow<Boolean?> = _isUsernameAvailable.asStateFlow()

    private val _usernameError = MutableStateFlow<String?>(null)
    val usernameError: StateFlow<String?> = _usernameError.asStateFlow()

    fun onUsernameChanged(input: String) {
        val clean = input.replace("@", "").trim().lowercase()
        _username.value = clean

        if (clean.isBlank()) {
            _isUsernameAvailable.value = null
            _usernameError.value = null
            return
        }

        if (clean.length < 3) {
            _isUsernameAvailable.value = false
            _usernameError.value = "Username must be at least 3 characters."
            return
        }

        if (!clean.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            _isUsernameAvailable.value = false
            _usernameError.value = "Only lowercase letters, numbers, and underscores allowed."
            return
        }

        viewModelScope.launch {
            _isUsernameChecking.value = true
            _usernameError.value = null
            val result = authRepository.checkUsernameAvailable(clean)
            _isUsernameChecking.value = false
            result.onSuccess { available ->
                _isUsernameAvailable.value = available
                if (!available) {
                    _usernameError.value = "Username @$clean is already taken."
                }
            }.onFailure {
                _isUsernameAvailable.value = false
                _usernameError.value = "Error checking username."
            }
        }
    }

    fun completeProfileSetup(fullName: String, username: String, about: String) {
        if (fullName.isBlank()) {
            _authState.value = AuthState.Error("Name cannot be empty.")
            return
        }

        val cleanUsername = username.replace("@", "").trim().lowercase()
        if (cleanUsername.length < 3) {
            _authState.value = AuthState.Error("Username must be at least 3 characters.")
            return
        }

        val authUser = supabaseClient.auth.currentUserOrNull()
        val userId = currentUserId.ifBlank { authUser?.id ?: authRepository.getCurrentUserId() ?: "" }
        val userEmail = authUser?.email?.ifBlank { currentEmail } ?: currentEmail

        val newProfile = Profile(
            id = userId,
            email = userEmail,
            fullName = fullName.trim(),
            username = cleanUsername,
            about = about.ifBlank { "Hey there! I am using Hey Chat 💬" }
        )

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.upsertProfile(newProfile)
            result.onSuccess {
                FcmTokenManager.syncFcmTokenWithSupabase()
                _authState.value = AuthState.Authenticated(newProfile)
                PresenceManager.onAppStart()
            }.onFailure { throwable ->
                _authState.value = AuthState.Error("Failed to save profile: ${parseErrorMessage(throwable)}")
            }
        }
    }

    fun uploadAvatar(userId: String, imageBytes: ByteArray) {
        val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
        if (cleanUserId.isBlank()) return

        viewModelScope.launch {
            _isUploadingAvatar.value = true
            _avatarUploadMessage.value = null

            val result = authRepository.uploadAvatar(cleanUserId, imageBytes)
            result.onSuccess { newAvatarUrl ->
                _isUploadingAvatar.value = false
                _avatarUploadMessage.value = "Profile photo updated successfully!"
                Log.d(TAG, "uploadAvatar ViewModel SUCCESS -> $newAvatarUrl")

                val currentProfile = (_authState.value as? AuthState.Authenticated)?.profile
                if (currentProfile != null) {
                    _authState.value = AuthState.Authenticated(currentProfile.copy(avatarUrl = newAvatarUrl))
                }
            }.onFailure { throwable ->
                _isUploadingAvatar.value = false
                val errorMsg = parseErrorMessage(throwable)
                _avatarUploadMessage.value = "Failed to upload photo: $errorMsg"
                Log.e(TAG, "uploadAvatar ViewModel FAILED -> $errorMsg", throwable)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("heychat_session_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("last_logged_in_user_id").apply()
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.NONE
        if (password.length < 6) return PasswordStrength.WEAK

        var hasLetters = false
        var hasDigits = false
        var hasSpecial = false

        for (char in password) {
            when {
                char.isLetter() -> hasLetters = true
                char.isDigit() -> hasDigits = true
                else -> hasSpecial = true
            }
        }

        return when {
            password.length >= 8 && hasLetters && hasDigits && hasSpecial -> PasswordStrength.STRONG
            password.length >= 6 && hasLetters && hasDigits -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun startResendTimer(seconds: Int) {
        timerJob?.cancel()
        _resendTimer.value = seconds
        timerJob = viewModelScope.launch {
            while (_resendTimer.value > 0) {
                delay(1000)
                _resendTimer.value -= 1
            }
        }
    }

    private fun parseErrorMessage(throwable: Throwable): String {
        val msg = throwable.message ?: "An unexpected error occurred"
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("UnknownHostException", ignoreCase = true) ->
                "Invalid Supabase Project URL or no internet connection. Please copy your real Project URL from Supabase Dashboard > Settings > API into SupabaseClient.kt."
            msg.contains("User already registered", ignoreCase = true) -> "An account with this email already exists."
            msg.contains("Invalid login credentials", ignoreCase = true) -> "Incorrect email or password."
            msg.contains("Email not confirmed", ignoreCase = true) -> "Email not verified. Please check your inbox."
            msg.contains("rate limit", ignoreCase = true) -> "Too many requests. Please wait a few minutes."
            msg.contains("Password should be at least", ignoreCase = true) -> "Password must be at least 6 characters long."
            else -> msg
        }
    }
}
