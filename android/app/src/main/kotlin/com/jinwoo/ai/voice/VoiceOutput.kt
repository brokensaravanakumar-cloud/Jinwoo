package com.jinwoo.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jinwoo.ai.data.AppSettings
import com.jinwoo.ai.data.SettingsRepository
import com.jinwoo.ai.net.BackendClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class VoiceInfo(val name: String, val locale: String, val maybeMale: Boolean)

/**
 * Jin Woo's voice. Uses the device TextToSpeech engine tuned deep/slow by default (pitch 0.75, speed 0.9),
 * or optionally an external TTS voice produced by the backend (/v1/tts). Quality of the on-device voice depends on
 * the TTS engine installed (Google Speech Services gives the best result).
 */
class VoiceOutput(
    private val ctx: Context,
    private val settings: SettingsRepository,
    private val backend: BackendClient,
) {
    private lateinit var engine: TextToSpeech
    private val ready = CompletableDeferred<Boolean>()
    private val _ready = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _ready.asStateFlow()
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    @Volatile private var done: CompletableDeferred<Unit>? = null
    @Volatile private var extDone: CompletableDeferred<Boolean>? = null
    private var player: MediaPlayer? = null
    private val ids = AtomicLong()

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    init {
        engine = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.setAudioAttributes(attrs)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId?.startsWith("last_") == true) done?.complete(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { done?.complete(Unit) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { done?.complete(Unit) }
                })
                _ready.value = true
                ready.complete(true)
            } else ready.complete(false)
        }
    }

    fun voices(): List<VoiceInfo> = runCatching {
        if (!_ready.value) emptyList() else engine.voices.orEmpty()
            .map { VoiceInfo(it.name, it.locale.toLanguageTag(), it.name.contains("male", true) && !it.name.contains("female", true)) }
            .sortedWith(compareBy({ it.locale }, { it.name }))
    }.getOrDefault(emptyList())

    private fun apply(s: AppSettings) {
        engine.language = Locale.forLanguageTag(s.speechLanguage)
        if (s.voiceName.isNotBlank()) engine.voices?.firstOrNull { it.name == s.voiceName }?.let { engine.voice = it }
        engine.setPitch(s.pitch.coerceIn(0.3f, 2f))
        engine.setSpeechRate(s.speed.coerceIn(0.3f, 2f))
    }

    /** Suspends until speech finishes or [stop] is called. Cancelling the caller also stops speech. */
    suspend fun speak(raw: String) {
        val text = clean(raw)
        if (text.isEmpty()) return
        stop()
        val s = settings.current
        if (s.externalTts) {
            val bytes = runCatching { backend.tts(text.take(1500)) }.getOrNull()
            if (bytes != null && playBytes(bytes, s.voiceVolume)) return
        }
        if (!ready.await()) return
        apply(s)
        val d = CompletableDeferred<Unit>().also { done = it }
        val base = ids.incrementAndGet()
        val chunks = chunk(text, (runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000) - 200).coerceAtLeast(500))
        _speaking.value = true
        try {
            chunks.forEachIndexed { i, c ->
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, s.voiceVolume.coerceIn(0f, 1f)) }
                val id = if (i == chunks.lastIndex) "last_$base" else "u_${base}_$i"
                engine.speak(c, TextToSpeech.QUEUE_ADD, params, id)
            }
            d.await()
        } finally {
            if (!d.isCompleted) engine.stop()
            _speaking.value = false
        }
    }

    fun stop() {
        if (_ready.value) engine.stop()
        done?.complete(Unit)
        extDone?.complete(true)
    }

    private suspend fun playBytes(bytes: ByteArray, volume: Float): Boolean {
        val file = File(ctx.cacheDir, "tts_ext.mp3")
        val finished = CompletableDeferred<Boolean>()
        val mp = MediaPlayer()
        try {
            file.writeBytes(bytes)
            mp.setAudioAttributes(attrs)
            mp.setDataSource(file.path)
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener { finished.complete(true) }
            mp.setOnErrorListener { _, _, _ -> finished.complete(false); true }
            mp.prepare()
            player = mp; extDone = finished
            _speaking.value = true
            mp.start()
            return finished.await()
        } catch (ignored: Exception) {
            return false
        } finally {
            runCatching { mp.release() }
            player = null; _speaking.value = false
        }
    }

    private fun clean(s: String): String = s
        .replace(Regex("https?://\\S+"), " link ")
        .replace(Regex("[*#`>_~|]+"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun chunk(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>(); var cur = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
            if (cur.length + sentence.length + 1 > max && cur.isNotEmpty()) { out += cur.toString(); cur = StringBuilder() }
            if (sentence.length > max) sentence.chunked(max).forEach { out += it } else cur.append(sentence).append(' ')
        }
        if (cur.isNotBlank()) out += cur.toString().trim()
        return out
    }

    fun shutdown() { stop(); if (_ready.value) engine.shutdown() }
}
