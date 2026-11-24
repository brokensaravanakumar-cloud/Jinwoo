package com.jinwoo.ai.wake

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Implemented by the optional sherpa-onnx engine (src/sherpa). Receives 16 kHz mono PCM16 chunks. */
interface WakeWordEngine {
    /** Returns true when the wake phrase was detected in this chunk. */
    fun process(pcm: ShortArray, length: Int): Boolean
    fun close()
}

sealed interface WakeState {
    data object Stopped : WakeState
    data object Listening : WakeState
    data object Paused : WakeState
    data class Error(val message: String) : WakeState
}

enum class WakeControl { Pause, Resume }

/** In-process message bus between the UI/assistant and the foreground listening service. */
class WakeBus {
    val state = MutableStateFlow<WakeState>(WakeState.Stopped)
    val detected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val control = MutableSharedFlow<WakeControl>(extraBufferCapacity = 8)

    private fun active() = state.value.let { it is WakeState.Listening || it is WakeState.Paused }
    /** Release the microphone so SpeechRecognizer can use it. */
    fun pause() { if (active()) control.tryEmit(WakeControl.Pause) }
    fun resume() { if (active()) control.tryEmit(WakeControl.Resume) }
}

data class WakeModelFiles(val encoder: File, val decoder: File, val joiner: File, val tokens: File, val keywords: File)

sealed interface WakeStatus {
    data object EngineMissing : WakeStatus
    data class ModelsMissing(val missing: List<String>) : WakeStatus
    data class Ready(val files: WakeModelFiles) : WakeStatus
}

object WakeWord {
    private const val ENGINE_CLASS = "com.jinwoo.ai.wake.SherpaWakeWordEngine"

    fun dir(ctx: Context) = File(ctx.filesDir, "wakeword").apply { mkdirs() }

    fun engineAvailable(): Boolean = runCatching { Class.forName(ENGINE_CLASS) }.isSuccess

    /** Honest status: never reports Ready unless the engine class AND all model files really exist. */
    fun status(ctx: Context): WakeStatus {
        val missing = mutableListOf<String>()
        val d = dir(ctx)
        val files = d.listFiles().orEmpty().filter { it.isFile }
        val model = pickModel(files)
        if (model == null) missing += "encoder*.onnx, decoder*.onnx, joiner*.onnx (matching set)"
        val tokens = files.firstOrNull { it.name == "tokens.txt" }
        if (tokens == null) missing += "tokens.txt"
        val kw = files.firstOrNull { it.name == "keywords.txt" && it.length() > 0 }
        if (kw == null) missing += "keywords.txt (contains the tokenised phrase JIN WOO)"
        if (!engineAvailable()) return WakeStatus.EngineMissing
        if (missing.isNotEmpty() || model == null || tokens == null || kw == null) return WakeStatus.ModelsMissing(missing)
        return WakeStatus.Ready(WakeModelFiles(model.first, model.second, model.third, tokens, kw))
    }

    /** Pick encoder/decoder/joiner from the same variant (all int8, or all non-int8). */
    fun pickModel(files: List<File>): Triple<File, File, File>? {
        fun pick(prefix: String, int8: Boolean) = files.firstOrNull {
            it.name.startsWith(prefix) && it.name.endsWith(".onnx") && it.name.contains("int8") == int8
        }
        for (int8 in listOf(true, false)) {
            val e = pick("encoder", int8); val dd = pick("decoder", int8); val j = pick("joiner", int8)
            if (e != null && dd != null && j != null) return Triple(e, dd, j)
        }
        return null
    }

    fun createEngine(ctx: Context, sensitivity: Float): WakeWordEngine? {
        val st = status(ctx) as? WakeStatus.Ready ?: return null
        val f = st.files
        return runCatching {
            Class.forName(ENGINE_CLASS).getConstructor(
                String::class.java, String::class.java, String::class.java, String::class.java, String::class.java,
                Float::class.javaPrimitiveType,
            ).newInstance(f.encoder.path, f.decoder.path, f.joiner.path, f.tokens.path, f.keywords.path, sensitivity) as WakeWordEngine
        }.getOrNull()
    }
}
