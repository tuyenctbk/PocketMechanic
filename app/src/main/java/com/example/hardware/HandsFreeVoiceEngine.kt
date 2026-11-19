package com.example.hardware

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class HandsFreePhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class HandsFreeVoiceEngine(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _phase = MutableStateFlow(HandsFreePhase.IDLE)
    val phase: StateFlow<HandsFreePhase> = _phase.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isSpeechRecognizerSupported = MutableStateFlow(
        SpeechRecognizer.isRecognitionAvailable(appContext)
    )
    val isSpeechRecognizerSupported: StateFlow<Boolean> = _isSpeechRecognizerSupported.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private var onFinalTranscriptCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onTtsDoneCallback: (() -> Unit)? = null

    @Volatile
    private var isReleased = false

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = textToSpeech
                if (tts != null) {
                    val result = tts.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.language = Locale.US
                    }
                    ttsReady = true
                }
            }
        }
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _phase.value = HandsFreePhase.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                finishTtsUtterance()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishTtsUtterance()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishTtsUtterance()
            }
        })
    }

    private fun finishTtsUtterance() {
        mainHandler.post {
            if (isReleased) return@post
            _phase.value = HandsFreePhase.IDLE
            val callback = onTtsDoneCallback
            onTtsDoneCallback = null
            callback?.invoke()
        }
    }

    fun startListening(
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        onFinalTranscriptCallback = onTranscript
        onErrorCallback = onError

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _isSpeechRecognizerSupported.value = false
            onError("On-device Speech Recognition service is unavailable; use Tap-to-Speak Audio instead.")
            return
        }

        mainHandler.post {
            try {
                textToSpeech?.stop()
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _phase.value = HandsFreePhase.LISTENING
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        _phase.value = HandsFreePhase.IDLE
                        if (error != SpeechRecognizer.ERROR_CLIENT) {
                            onErrorCallback?.invoke(mapSpeechError(error))
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        if (text.isNotEmpty()) {
                            _phase.value = HandsFreePhase.THINKING
                            onFinalTranscriptCallback?.invoke(text)
                        } else {
                            _phase.value = HandsFreePhase.IDLE
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        _partialTranscript.value = matches?.firstOrNull().orEmpty()
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                _phase.value = HandsFreePhase.LISTENING
                recognizer.startListening(intent)
            } catch (e: Exception) {
                _phase.value = HandsFreePhase.IDLE
                onError(e.localizedMessage ?: "Unable to start speech recognition.")
            }
        }
    }

    fun setThinking() {
        _phase.value = HandsFreePhase.THINKING
    }

    fun speak(text: String, onComplete: () -> Unit) {
        onTtsDoneCallback = onComplete
        if (!ttsReady || textToSpeech == null) {
            _phase.value = HandsFreePhase.IDLE
            onComplete()
            return
        }
        val cleanText = text.replace("*", "").replace("#", "").trim()
        _phase.value = HandsFreePhase.SPEAKING
        val utteranceId = UUID.randomUUID().toString()
        val res = textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (res != TextToSpeech.SUCCESS) {
            _phase.value = HandsFreePhase.IDLE
            onComplete()
        }
    }

    fun stopAll() {
        onFinalTranscriptCallback = null
        onErrorCallback = null
        onTtsDoneCallback = null
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            try {
                textToSpeech?.stop()
            } catch (_: Exception) {
            }
            _phase.value = HandsFreePhase.IDLE
            _partialTranscript.value = ""
        }
    }

    fun release() {
        isReleased = true
        stopAll()
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.shutdown()
            textToSpeech = null
        }
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network unavailable."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — tap or speak again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — listening paused."
        else -> "Speech recognizer paused ($code)."
    }
}
