package com.example.heychat.service

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SessionManager {
    private const val TAG = "HeyChat_Auth"
    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    fun notifySessionExpired() {
        Log.e(TAG, "SessionManager -> notifySessionExpired triggered by server token rejection")
        _sessionExpiredEvent.tryEmit(Unit)
    }
}
