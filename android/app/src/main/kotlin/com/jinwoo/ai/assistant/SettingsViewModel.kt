package com.jinwoo.ai.assistant

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jinwoo.ai.JinWooApp
import com.jinwoo.ai.data.SecureStore
import com.jinwoo.ai.net.BackendException
import com.jinwoo.ai.voice.VoiceInfo
import com.jinwoo.ai.wake.WakeStatus
import com.jinwoo.ai.wake.WakeWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = (app as JinWooApp).container

    private val _wake = MutableStateFlow<WakeStatus>(WakeWord.status(app))
    val wakeStatus: StateFlow<WakeStatus> = _wake.asStateFlow()
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()
    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()
    private val _info = MutableStateFlow("")
    val info: StateFlow<String> = _info.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun token(): String = c.secure.get(SecureStore.TOKEN)
    fun setToken(t: String) = c.secure.put(SecureStore.TOKEN, t.trim())
    fun refreshWake() { _wake.value = WakeWord.status(app) }
    fun refreshVoices() { _voices.value = c.voice.voices() }
    fun clearInfo() { _info.value = "" }

    private fun describe(e: Throwable) = when (e) {
        is BackendException -> "${e.code}: ${e.message}"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { _info.value = "FAILED - ${describe(e)}" }
        finally { _busy.value = false }
    }

    fun loadModels() = launchBusy {
        val p = c.settings.current.provider
        _models.value = c.backend.models(p)
        _info.value = "Loaded ${_models.value.size} models from $p."
    }

    fun testConnection() = launchBusy {
        val s = c.settings.current
        _info.value = "Testing ${s.provider}..."
        val reply = c.backend.test(s.provider, s.activeModel())
        _info.value = "CONNECTED - ${s.provider} replied: ${reply.take(120)}"
    }

    fun serverStatus() = launchBusy {
        val st = c.backend.status()
        _info.value = "Backend online. Providers: " + st.optJSONObject("providers")?.toString()
    }

    fun testVoice() = viewModelScope.launch { c.voice.speak("System online. I am Jin Woo. Awaiting your command, Master.") }
    fun stopVoice() = c.voice.stop()
    fun playSound() = c.sound.play(force = true)

    fun clearHistory() = viewModelScope.launch {
        c.db.messages().clear(); c.historyCleared.tryEmit(Unit); _info.value = "Conversation history cleared."
    }

    fun importSound(uri: Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        c.settings.update { it.copy(customSoundUri = uri.toString()) }
        _info.value = "Custom activation sound selected."
    }

    fun resetSound() { c.settings.update { it.copy(customSoundUri = "") }; _info.value = "Default activation sound restored." }

    /** Copies model files (.onnx / .txt) from a folder the user picked into the app's private storage. */
    fun importWakeModels(treeUri: Uri) = launchBusy {
        val dir = WakeWord.dir(app)
        val copied = withContext(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(app, treeUri) ?: return@withContext 0
            var n = 0
            for (f in tree.listFiles()) {
                val name = f.name ?: continue
                if (!f.isFile || !(name.endsWith(".onnx") || name.endsWith(".txt"))) continue
                app.contentResolver.openInputStream(f.uri)?.use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
                n++
            }
            n
        }
        refreshWake()
        _info.value = "Imported $copied files. " + when (val s = _wake.value) {
            is WakeStatus.Ready -> "Wake word is ready to enable."
            is WakeStatus.EngineMissing -> "Models found, but the sherpa-onnx engine is not built into this APK (see WAKE_WORD_SETUP.md)."
            is WakeStatus.ModelsMissing -> "Still missing: ${s.missing.joinToString("; ")}"
        }
    }
}
