package com.example.heychat.util

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AudioPlayerManager {

    private const val TAG = "HeyChat_AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentlyPlayingUrl = MutableStateFlow<String?>(null)
    val currentlyPlayingUrl: StateFlow<String?> = _currentlyPlayingUrl.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun playOrPause(url: String) {
        if (_currentlyPlayingUrl.value == url && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()

        _currentlyPlayingUrl.value = url
        _isPlaying.value = true

        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    _durationMs.value = mp.duration
                    _isPlaying.value = true
                    startProgressTracker()
                    Log.d(TAG, "AudioPlayer STARTED for url '$url', duration: ${mp.duration}ms")
                }
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "AudioPlayer ERROR: what=$what extra=$extra")
                    stop()
                    true
                }
            }
            mediaPlayer = player
        }.onFailure { e ->
            Log.e(TAG, "AudioPlayer START FAILED: ${e.message}", e)
            stop()
        }
    }

    fun pause() {
        runCatching {
            mediaPlayer?.pause()
            _isPlaying.value = false
            progressJob?.cancel()
            Log.d(TAG, "AudioPlayer PAUSED")
        }
    }

    fun resume() {
        runCatching {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracker()
            Log.d(TAG, "AudioPlayer RESUMED")
        }
    }

    fun seekTo(positionMs: Int) {
        runCatching {
            mediaPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null

        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null

        _currentlyPlayingUrl.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0
        _durationMs.value = 0
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                _currentPositionMs.value = mediaPlayer?.currentPosition ?: 0
                delay(100L)
            }
        }
    }
}
