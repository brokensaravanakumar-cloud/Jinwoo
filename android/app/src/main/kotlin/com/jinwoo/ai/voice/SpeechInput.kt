package com.jinwoo.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed interface SpeechOutcome {
    data class Heard(val text: String) : SpeechOutcome
    data object NoSpeech : SpeechOutcome
    data class Failed(val message: String) : SpeechOutcome
}

/**
 * ONE-SHOT command recognition via Android SpeechRecognizer. It is started only after the wake word / mic button,
 * and is never used as an endless background listener. (Depending on the device's speech service it may use the network.)
 */
class SpeechInput(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    suspend fun listenOnce(language: String, onPartial: (String) -> Unit): SpeechOutcome = withContext(Dispatchers.Main) {
        if (!available()) return@withContext SpeechOutcome.Failed("Speech recognition is not available on this device.")
        suspendCancellableCoroutine<SpeechOutcome> { cont ->
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            fun finish(outcome: SpeechOutcome) {
                if (cont.isActive) cont.resume(outcome)
                main.post { runCatching { r.destroy() } }
            }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    finish(if (t.isEmpty()) SpeechOutcome.NoSpeech else SpeechOutcome.Heard(t))
                }
                override fun onError(error: Int) {
                    finish(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechOutcome.NoSpeech
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechOutcome.Failed("Microphone permission is missing.")
                            SpeechRecognizer.ERROR_AUDIO -> SpeechOutcome.Failed("Microphone error. Another app may be using the mic.")
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechOutcome.Failed("The speech service is busy. Try again.")
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                                SpeechOutcome.Failed("The speech service could not be reached.")
                            else -> SpeechOutcome.Failed("Speech recognition error ($error).")
                        }
                    )
                }
            })
            cont.invokeOnCancellation { main.post { runCatching { r.cancel(); r.destroy() } } }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            r.startListening(intent)
        }
    }
}
