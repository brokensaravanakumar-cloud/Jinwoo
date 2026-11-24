package com.jinwoo.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinwoo.ai.JinWooApp
import com.jinwoo.ai.assistant.SettingsViewModel
import com.jinwoo.ai.ui.components.ActionButton
import com.jinwoo.ai.ui.components.BackBar
import com.jinwoo.ai.ui.components.DropdownRow
import com.jinwoo.ai.ui.components.SectionTitle
import com.jinwoo.ai.ui.components.SliderRow
import com.jinwoo.ai.ui.components.SwitchRow
import com.jinwoo.ai.ui.theme.Accents
import com.jinwoo.ai.ui.theme.TextSoft

private val LANGUAGES = listOf("en-US", "en-GB", "en-IN", "hi-IN", "ta-IN", "te-IN", "ml-IN", "kn-IN", "es-ES", "fr-FR", "de-DE", "ja-JP")

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onProvider: () -> Unit, onWake: () -> Unit) {
    val container = (LocalContext.current.applicationContext as JinWooApp).container
    val s by container.settings.flow.collectAsState()
    val voices by vm.voices.collectAsState()
    val info by vm.info.collectAsState()
    val busy by vm.busy.collectAsState()
    fun upd(b: (com.jinwoo.ai.data.AppSettings) -> com.jinwoo.ai.data.AppSettings) = container.settings.update(b)

    LaunchedEffect(Unit) { vm.refreshVoices() }
    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSound(uri) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackBar("Settings", onBack)

        SectionTitle("AI")
        ActionButton("AI provider & model: ${s.provider.uppercase()} ${s.activeModel()}  >") { onProvider() }
        ActionButton("Test AI connection", !busy) { vm.testConnection() }

        SectionTitle("Wake word & listening")
        ActionButton("Wake word setup: ${if (s.wakeWordEnabled) "ENABLED" else "DISABLED"}  >") { onWake() }
        SwitchRow("Notification when heard in background", "Android blocks background launches, so a tap-to-open notification is used.", s.wakeAlertNotification) { v -> upd { it.copy(wakeAlertNotification = v) } }

        SectionTitle("Voice")
        val matching = voices.filter { it.locale.startsWith(s.speechLanguage.substringBefore('-')) }.ifEmpty { voices }
        DropdownRow("Voice", s.voiceName, listOf("") + matching.map { it.name }, { v -> upd { it.copy(voiceName = v) } },
            label = { n -> if (n.isEmpty()) "" else n.take(26) + if (voices.firstOrNull { it.name == n }?.maybeMale == true) " (male)" else "" })
        SliderRow("Pitch (lower = deeper)", s.pitch, 0.4f..1.5f, { v -> upd { it.copy(pitch = v) } })
        SliderRow("Speed", s.speed, 0.5f..1.5f, { v -> upd { it.copy(speed = v) } })
        SliderRow("Volume", s.voiceVolume, 0f..1f, { v -> upd { it.copy(voiceVolume = v) } })
        SwitchRow("External TTS voice (via backend)", "Uses the backend's OpenAI TTS voice if configured; falls back to the phone voice.", s.externalTts) { v -> upd { it.copy(externalTts = v) } }
        ActionButton("Speak test") { vm.testVoice() }
        ActionButton("Stop speaking") { vm.stopVoice() }
        Text("Tip: install Google Speech Services and download a deep male English voice for the best Shadow Monarch tone.", color = TextSoft, fontSize = 12.sp)

        SectionTitle("Activation")
        SwitchRow("Activation animation", "", s.activationAnimation) { v -> upd { it.copy(activationAnimation = v) } }
        SwitchRow("Activation sound", "", s.activationSound) { v -> upd { it.copy(activationSound = v) } }
        SliderRow("Sound volume", s.soundVolume, 0f..1f, { v -> upd { it.copy(soundVolume = v) } })
        ActionButton("Import my own activation / theme sound") { pickSound.launch(arrayOf("audio/*")) }
        if (s.customSoundUri.isNotBlank()) ActionButton("Use default sound again") { vm.resetSound() }
        ActionButton("Preview activation sound") { vm.playSound() }

        SectionTitle("Language")
        DropdownRow("Speech language", s.speechLanguage, LANGUAGES, { l -> upd { it.copy(speechLanguage = l) } })
        OutlinedTextField(s.speechLanguage, { l -> upd { it.copy(speechLanguage = l.trim()) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Custom BCP-47 tag (e.g. ta-IN)") })

        SectionTitle("Theme")
        DropdownRow("Accent colour", s.accent, Accents.names, { a -> upd { it.copy(accent = a) } })
        SwitchRow("Shadow particles", "", s.particles) { v -> upd { it.copy(particles = v) } }

        SectionTitle("Memory & privacy")
        SwitchRow("Conversation memory", "Stores the chat on this phone and sends recent turns to the AI for context.", s.memoryEnabled) { v -> upd { it.copy(memoryEnabled = v) } }
        ActionButton("Clear conversation history") { vm.clearHistory() }
        Text("Wake-word audio is processed on this device and never uploaded. Your spoken command is transcribed by Android's speech service, " +
            "and the text is sent to your own backend and chosen AI provider. Calls and messages always ask for confirmation first.", color = TextSoft, fontSize = 12.sp)

        if (info.isNotBlank()) Text(info, color = TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    }
}
