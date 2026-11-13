package com.example.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val languageLabel: String,
    val localeTag: String,
    val requiresNetwork: Boolean
)

class TextToSpeechController(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false

    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val voices: StateFlow<List<TtsVoiceOption>> = _voices.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                _isReady.value = true
                _initError.value = null
                loadVoices()
            } else {
                initialized = false
                _isReady.value = false
                _initError.value = "Android TextToSpeech engine is unavailable on this device."
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    private fun loadVoices() {
        val engine = tts ?: return
        val allVoices: Set<Voice> = runCatching { engine.voices }.getOrNull().orEmpty()
        val filtered = allVoices
            .filter { v ->
                val lang = v.locale.language.lowercase(Locale.US)
                lang == "en" || lang == "hi"
            }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.locale.language }.thenBy { it.name })
            .map { v ->
                val langName = if (v.locale.language == "hi") "Hindi" else "English (${v.locale.country.ifBlank { "Global" }})"
                val cleanName = v.name
                    .removePrefix("en-")
                    .removePrefix("hi-")
                    .replace('-', ' ')
                    .replaceFirstChar { it.uppercase() }
                TtsVoiceOption(
                    id = v.name,
                    displayName = cleanName.take(28),
                    languageLabel = langName,
                    localeTag = v.locale.toLanguageTag(),
                    requiresNetwork = v.isNetworkConnectionRequired
                )
            }

        if (filtered.isEmpty()) {
            // Fallback locale options when engine does not enumerate voice objects
            _voices.value = listOf(
                TtsVoiceOption(
                    id = "default_en",
                    displayName = "System English",
                    languageLabel = "English (US)",
                    localeTag = "en-US",
                    requiresNetwork = false
                ),
                TtsVoiceOption(
                    id = "default_hi",
                    displayName = "System Hindi",
                    languageLabel = "Hindi (IN)",
                    localeTag = "hi-IN",
                    requiresNetwork = false
                )
            )
        } else {
            _voices.value = filtered
        }
    }

    fun speakPreview(text: String, voiceOption: TtsVoiceOption?, speed: Float, pitch: Float) {
        val engine = tts ?: return
        if (!initialized || text.isBlank()) return
        applyVoiceAndParams(engine, voiceOption, speed, pitch)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview_${UUID.randomUUID()}")
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    suspend fun synthesizeToWavFile(
        text: String,
        voiceOption: TtsVoiceOption?,
        speed: Float,
        pitch: Float,
        outputFile: File
    ): File = suspendCancellableCoroutine { cont ->
        val engine = tts
        if (engine == null || !initialized) {
            cont.resumeWithException(IllegalStateException("TTS engine is not ready."))
            return@suspendCancellableCoroutine
        }
        val utteranceId = "synth_${UUID.randomUUID()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(id: String?) {
                _isSpeaking.value = false
                if (id == utteranceId && cont.isActive) {
                    cont.resume(outputFile)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                _isSpeaking.value = false
                if (id == utteranceId && cont.isActive) {
                    cont.resumeWithException(IllegalStateException("TextToSpeech failed to synthesize audio file."))
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                _isSpeaking.value = false
                if (id == utteranceId && cont.isActive) {
                    cont.resumeWithException(IllegalStateException("TTS synthesis error code $errorCode."))
                }
            }
        })

        applyVoiceAndParams(engine, voiceOption, speed, pitch)
        val res = engine.synthesizeToFile(text, null, outputFile, utteranceId)
        if (res != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resumeWithException(IllegalStateException("TTS synthesizeToFile rejected request."))
        }
    }

    private fun applyVoiceAndParams(
        engine: TextToSpeech,
        voiceOption: TtsVoiceOption?,
        speed: Float,
        pitch: Float
    ) {
        engine.setSpeechRate(speed.coerceIn(0.4f, 2.2f))
        engine.setPitch(pitch.coerceIn(0.4f, 2.2f))
        if (voiceOption != null) {
            val matchedVoice = runCatching { engine.voices }.getOrNull()
                ?.firstOrNull { it.name == voiceOption.id }
            if (matchedVoice != null) {
                engine.voice = matchedVoice
            } else {
                engine.language = Locale.forLanguageTag(voiceOption.localeTag)
            }
        }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}

class LiveSpeechController(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        languageTag: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isAvailable()) {
            onError("No speech recognition service is installed on this device.")
            return
        }
        stopListening()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognition network unavailable. Try an offline language pack or manual edit."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No clear speech detected. Please speak closer to the mic."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out."
                    else -> "Speech recognition error ($error)."
                }
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onFinalResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartialResult(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.startListening(intent)
    }

    fun stopListening() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
    }
}
