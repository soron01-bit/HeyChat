package com.example.heychat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    fun start(outputFile: File) {
        runCatching {
            stop()
            createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
                recorder = this
            }
            Log.d("HeyChat_Audio", "AudioRecorder START -> File '${outputFile.absolutePath}'")
        }.onFailure { e ->
            Log.e("HeyChat_Audio", "AudioRecorder START FAILED: ${e.message}", e)
        }
    }

    fun getAmplitude(): Int {
        return runCatching {
            recorder?.maxAmplitude ?: 0
        }.getOrDefault(0)
    }

    fun stop() {
        val activeRecorder = recorder ?: return
        runCatching {
            activeRecorder.stop()
            activeRecorder.release()
            recorder = null
            Log.d("HeyChat_Audio", "AudioRecorder STOP SUCCESS")
        }.onFailure {
            runCatching { activeRecorder.release() }
            recorder = null
        }
    }
}
