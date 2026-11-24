package com.jinwoo.ai.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.jinwoo.ai.ui.components.SectionTitle
import com.jinwoo.ai.ui.components.SliderRow
import com.jinwoo.ai.ui.components.SwitchRow
import com.jinwoo.ai.ui.theme.AlertRed
import com.jinwoo.ai.ui.theme.TextSoft
import com.jinwoo.ai.wake.WakeState
import com.jinwoo.ai.wake.WakeStatus
import com.jinwoo.ai.wake.WakeWordService

@Composable
fun WakeWordScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = (ctx.applicationContext as JinWooApp).container
    val s by container.settings.flow.collectAsState()
    val status by vm.wakeStatus.collectAsState()
    val state by container.wakeBus.state.collectAsState()
    val info by vm.info.collectAsState()
    val busy by vm.busy.collectAsState()
    LaunchedEffect(Unit) { vm.refreshWake() }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.importWakeModels(uri) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) {
            container.settings.update { it.copy(wakeWordEnabled = true) }
            WakeWordService.start(ctx)          // user-initiated: the app is visible and the user just toggled the switch
        }
    }

    fun toggle(on: Boolean) {
        if (!on) { container.settings.update { it.copy(wakeWordEnabled = false) }; WakeWordService.stop(ctx); return }
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        perms.launch(needed)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackBar("Wake word", onBack)
        Text("Phrase: JIN WOO", color = TextSoft, modifier = Modifier.padding(top = 8.dp))

        SectionTitle("Engine & model status")
        when (val st = status) {
            is WakeStatus.Ready -> Text("READY - engine and model files found.")
            is WakeStatus.EngineMissing -> Text("ENGINE MISSING - this APK was built without the sherpa-onnx library. " +
                "Wake-word detection is NOT active. Follow docs/WAKE_WORD_SETUP.md (step 1), rebuild, then import the model files. " +
                "You can still use the microphone button.", color = AlertRed)
            is WakeStatus.ModelsMissing -> Text("MODEL FILES MISSING - Wake-word detection is NOT active.\nMissing: " + st.missing.joinToString("; "), color = AlertRed)
        }
        ActionButton("Import model folder (encoder/decoder/joiner .onnx, tokens.txt, keywords.txt)", !busy) { pickFolder.launch(null) }
        ActionButton("Re-check status") { vm.refreshWake() }

        SectionTitle("Listening")
        val ready = status is WakeStatus.Ready
        SwitchRow("Listen for \"Jin Woo\" in the background", if (ready) "Shows a persistent notification with a Stop action." else "Unavailable until engine and models are ready.",
            s.wakeWordEnabled) { v -> if (ready || !v) toggle(v) }
        Text("Service: " + when (val w = state) {
            WakeState.Stopped -> "stopped"; WakeState.Listening -> "listening"; WakeState.Paused -> "paused (microphone in use by the assistant)"
            is WakeState.Error -> "error - ${w.message}"
        }, color = if (state is WakeState.Error) AlertRed else TextSoft, fontSize = 13.sp)
        if (s.wakeWordEnabled && state is WakeState.Stopped && ready) ActionButton("Restart listening service") { toggle(true) }
        SliderRow("Sensitivity (higher = triggers more easily, more false alarms)", s.wakeSensitivity, 0f..1f, { v -> container.settings.update { it.copy(wakeSensitivity = v) } })
        Text("Sensitivity applies the next time listening starts.", color = TextSoft, fontSize = 12.sp)
        SwitchRow("Notification when heard in background", "", s.wakeAlertNotification) { v -> container.settings.update { it.copy(wakeAlertNotification = v) } }

        SectionTitle("Battery & reliability")
        Text("Some phones stop background services to save battery. If listening stops by itself, exempt JIN WOO AI from battery optimisation.", color = TextSoft, fontSize = 13.sp)
        ActionButton("Open battery optimisation settings") { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        if (info.isNotBlank()) Text(info, color = TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    }
}
