package com.jinwoo.ai.assistant

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jinwoo.ai.JinWooApp
import com.jinwoo.ai.actions.ActionResult
import com.jinwoo.ai.actions.AssistantAction
import com.jinwoo.ai.ai.Persona
import com.jinwoo.ai.data.MessageEntity
import com.jinwoo.ai.net.BackendException
import com.jinwoo.ai.net.ChatMessage
import com.jinwoo.ai.voice.SpeechOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssistantState { Sleeping, Listening, Processing, Speaking, Error, Offline }

data class ChatLine(val role: String, val text: String)

data class PendingAction(val action: AssistantAction, val args: Map<String, String>, val prompt: String)

data class AssistantUiState(
    val state: AssistantState = AssistantState.Sleeping,
    val lines: List<ChatLine> = emptyList(),
    val partial: String = "",
    val streaming: String = "",
    val activationVisible: Boolean = false,
    val pending: PendingAction? = null,
    val message: String = "",
    val needMicPermission: Boolean = false,
)

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as JinWooApp).container
    private val _ui = MutableStateFlow(AssistantUiState())
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()
    private var session: Job? = null

    init {
        viewModelScope.launch {
            if (c.settings.current.memoryEnabled) {
                val saved = c.db.messages().recent(30).reversed().map { ChatLine(it.role, it.content) }
                _ui.update { it.copy(lines = saved) }
            }
        }
        viewModelScope.launch { c.wakeBus.detected.collect { activate(fromWake = true) } }
        viewModelScope.launch { c.historyCleared.collect { _ui.update { it.copy(lines = emptyList()) } } }
    }

    private fun micGranted() = ContextCompat.checkSelfPermission(getApplication<Application>(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun activate(fromWake: Boolean) {
        if (session?.isActive == true) return
        if (!micGranted()) { _ui.update { it.copy(needMicPermission = true) }; return }
        session = viewModelScope.launch { runSession(fromWake) }
    }

    fun onMic() {
        when {
            _ui.value.state == AssistantState.Speaking -> c.voice.stop()
            session?.isActive == true -> stopSession()
            else -> activate(fromWake = false)
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        _ui.update { it.copy(needMicPermission = false, message = if (granted) "" else "Microphone permission denied. You can still type commands.") }
        if (granted) activate(false)
    }

    fun stopSession() { session?.cancel() }

    fun submitText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        session?.cancel()
        session = viewModelScope.launch {
            c.wakeBus.pause()
            try {
                c.voice.stop()
                beginSessionLines()
                handleCommand(t)
            } finally { endUi() }
        }
    }

    fun resolvePending(yes: Boolean) {
        val p = _ui.value.pending ?: return
        _ui.update { it.copy(pending = null) }
        session?.cancel()
        session = viewModelScope.launch {
            c.wakeBus.pause()
            try {
                c.voice.stop()
                if (yes) execute(p.action, p.args) else say("Understood. Cancelled.")
            } finally { endUi() }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = "") }

    private suspend fun runSession(fromWake: Boolean) {
        c.wakeBus.pause()
        try {
            c.voice.stop()
            beginSessionLines()
            val s = c.settings.current
            if (s.activationAnimation) _ui.update { it.copy(activationVisible = true) }
            c.sound.play()
            if (s.activationAnimation) { delay(1900); _ui.update { it.copy(activationVisible = false) } }
            if (fromWake) say("System activated. How may I assist you, Master?")
            var silent = 0
            while (true) {
                setState(AssistantState.Listening)
                val out = c.speechInput.listenOnce(c.settings.current.speechLanguage) { p -> _ui.update { it.copy(partial = p) } }
                _ui.update { it.copy(partial = "") }
                when (out) {
                    is SpeechOutcome.Heard -> { silent = 0; if (handleCommand(out.text)) break }
                    SpeechOutcome.NoSpeech -> { silent++; if (silent >= 2) break }
                    is SpeechOutcome.Failed -> { _ui.update { it.copy(state = AssistantState.Error, message = out.message) }; break }
                }
            }
        } finally { endUi() }
    }

    private fun beginSessionLines() {
        if (!c.settings.current.memoryEnabled) _ui.update { it.copy(lines = emptyList()) }
        _ui.update { it.copy(message = "", streaming = "") }
    }

    private fun endUi() {
        _ui.update { it.copy(state = if (it.state == AssistantState.Error || it.state == AssistantState.Offline) it.state else AssistantState.Sleeping,
            partial = "", streaming = "", activationVisible = false) }
        c.wakeBus.resume()
    }

    private fun setState(s: AssistantState) = _ui.update { it.copy(state = s) }

    private suspend fun handleCommand(text: String): Boolean {
        addLine("user", text)
        _ui.value.pending?.let { p ->
            val t = text.lowercase().trim().trimEnd('.', '!')
            _ui.update { it.copy(pending = null) }
            when {
                t in YES -> return execute(p.action, p.args)
                t in NO -> { say("Understood. Cancelled."); return false }
                else -> Unit
            }
        }
        c.actions.match(text)?.let { (a, args) -> return dispatch(a, args) }
        return askAi(text)
    }

    private suspend fun dispatch(a: AssistantAction, args: Map<String, String>): Boolean {
        if (a.sensitive) {
            val prompt = a.confirmationPrompt(args)
            _ui.update { it.copy(pending = PendingAction(a, args, prompt)) }
            say(prompt)
            return false
        }
        return execute(a, args)
    }

    private suspend fun execute(a: AssistantAction, args: Map<String, String>): Boolean {
        setState(AssistantState.Processing)
        val r: ActionResult = try {
            a.execute(getApplication<Application>(), args)
        } catch (e: CancellationException) { throw e
        } catch (ignored: ActivityNotFoundException) { ActionResult("I found nothing on this phone that can do that, Master.")
        } catch (ignored: SecurityException) { ActionResult("Android did not allow that action, Master.")
        } catch (ignored: Exception) { ActionResult("That action failed, Master.") }
        say(r.speech)
        return r.endSession
    }

    private suspend fun askAi(text: String): Boolean {
        if (!online()) {
            setState(AssistantState.Offline)
            say("I cannot reach the network, Master. Local commands still work.")
            return false
        }
        setState(AssistantState.Processing)
        val s = c.settings.current
        val history = _ui.value.lines.takeLast(20).map { ChatMessage(it.role, it.text) }
        val sb = StringBuilder()
        try {
            c.backend.streamChat(s.provider, s.activeModel(), history, Persona.system(c.actions, s.speechLanguage)).collect { d ->
                sb.append(d)
                val shown = if (Persona.looksLikeAction(sb.toString())) "" else sb.toString()
                _ui.update { it.copy(streaming = shown) }
            }
        } catch (e: BackendException) {
            _ui.update { it.copy(streaming = "", state = AssistantState.Error, message = e.message.orEmpty()) }
            say(friendly(e)); return false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _ui.update { it.copy(streaming = "", state = AssistantState.Error, message = e.message ?: "AI request failed") }
            say("Something went wrong while thinking, Master."); return false
        }
        _ui.update { it.copy(streaming = "") }
        val full = sb.toString().trim()
        if (full.isEmpty()) { say("I received no answer, Master."); return false }
        Persona.parseAction(full)?.let { plan ->
            val action = c.actions.byName(plan.name)
            return if (action != null) dispatch(action, plan.args) else { say("I do not know how to do that yet, Master."); false }
        }
        say(full)
        return false
    }

    private suspend fun say(text: String) {
        addLine("assistant", text)
        if (_ui.value.state != AssistantState.Error && _ui.value.state != AssistantState.Offline) setState(AssistantState.Speaking)
        c.voice.speak(text)
    }

    private suspend fun addLine(role: String, text: String) {
        _ui.update { it.copy(lines = (it.lines + ChatLine(role, text)).takeLast(200)) }
        if (c.settings.current.memoryEnabled) c.db.messages().insert(MessageEntity(role = role, content = text, createdAt = System.currentTimeMillis()))
    }

    private fun online(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun friendly(e: BackendException) = when (e.code) {
        "invalid_api_key" -> "The AI provider rejected the API key stored on the backend, Master."
        "quota_exceeded" -> "The AI provider's quota is exhausted, Master."
        "model_unavailable" -> "That model is unavailable. Choose another in the provider settings, Master."
        "network_error" -> "I cannot reach my backend, Master."
        "unauthorized" -> "The backend rejected my access token, Master."
        "rate_limited" -> "Too many requests, Master. Give me a moment."
        "not_configured", "provider_not_configured" -> "The provider is not configured on the backend, Master."
        else -> "The AI request failed, Master."
    }

    override fun onCleared() { c.voice.stop(); super.onCleared() }

    private companion object {
        val YES = setOf("yes", "yeah", "yep", "confirm", "proceed", "do it", "go ahead", "sure", "ok", "okay", "affirmative")
        val NO = setOf("no", "nope", "cancel", "stop", "don't", "do not", "negative", "never mind")
    }
}
