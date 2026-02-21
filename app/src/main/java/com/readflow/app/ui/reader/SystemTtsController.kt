package com.readflow.app.ui.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

enum class TtsPlaybackState {
    IDLE,
    SPEAKING,
    PAUSED,
    ERROR,
}

class SystemTtsController(
    context: Context,
    private val onStateChanged: (TtsPlaybackState) -> Unit,
    private val onProgress: (Int) -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingSpeak: Pair<String, Int>? = null
    private var currentContent: String = ""
    private var currentPosition: Int = 0
    private var pausedPosition: Int = 0
    private var speakingBase: Int = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                onStateChanged(TtsPlaybackState.ERROR)
                return@TextToSpeech
            }
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onStateChanged(TtsPlaybackState.SPEAKING)
                }

                override fun onDone(utteranceId: String?) {
                    currentPosition = (currentContent.length - 1).coerceAtLeast(0)
                    pausedPosition = currentPosition
                    onProgress(currentPosition)
                    onStateChanged(TtsPlaybackState.IDLE)
                }

                override fun onError(utteranceId: String?) {
                    onStateChanged(TtsPlaybackState.ERROR)
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int,
                ) {
                    val absolute = (speakingBase + start).coerceIn(0, currentContent.length.coerceAtLeast(1) - 1)
                    currentPosition = absolute
                    pausedPosition = absolute
                    onProgress(absolute)
                }
            })
            pendingSpeak?.let {
                speak(it.first, it.second)
                pendingSpeak = null
            }
        }
    }

    fun setRate(value: Float) {
        tts?.setSpeechRate(value.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(value: Float) {
        tts?.setPitch(value.coerceIn(0.5f, 2.0f))
    }

    fun speak(content: String, startPosition: Int) {
        if (!ready) {
            pendingSpeak = content to startPosition
            return
        }
        currentContent = content
        speakingBase = startPosition.coerceIn(0, content.length.coerceAtLeast(1) - 1)
        pausedPosition = speakingBase
        val utterance = content.substring(speakingBase)
        if (utterance.isBlank()) {
            onStateChanged(TtsPlaybackState.IDLE)
            return
        }
        val utteranceId = "rf-${System.nanoTime()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts?.speak(utterance, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun pause() {
        if (!ready) return
        tts?.stop()
        onStateChanged(TtsPlaybackState.PAUSED)
    }

    fun resume() {
        if (!ready || currentContent.isBlank()) return
        speak(currentContent, pausedPosition)
    }

    fun stop() {
        tts?.stop()
        onStateChanged(TtsPlaybackState.IDLE)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
