package com.jinwoo.ai.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jinwoo.ai.JinWooApp
import com.jinwoo.ai.assistant.AssistantViewModel
import com.jinwoo.ai.assistant.SettingsViewModel
import com.jinwoo.ai.ui.theme.JinWooTheme
import com.jinwoo.ai.ui.theme.Void

@Composable
fun JinWooRoot(assistant: AssistantViewModel, settingsVm: SettingsViewModel) {
    val container = (LocalContext.current.applicationContext as JinWooApp).container
    val s by container.settings.flow.collectAsState()
    val ui by assistant.ui.collectAsState()
    var screen by rememberSaveable { mutableStateOf("main") }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { assistant.onMicPermissionResult(it) }
    LaunchedEffect(ui.needMicPermission) { if (ui.needMicPermission) micPermission.launch(Manifest.permission.RECORD_AUDIO) }

    JinWooTheme(s.accent) {
        BackHandler(screen != "main") { screen = "main" }
        Box(Modifier.fillMaxSize().background(Void)) {
            when (screen) {
                "settings" -> SettingsScreen(settingsVm, onBack = { screen = "main" }, onProvider = { screen = "provider" }, onWake = { screen = "wake" })
                "provider" -> ProviderScreen(settingsVm, onBack = { screen = "settings" })
                "wake" -> WakeWordScreen(settingsVm, onBack = { screen = "main" })
                else -> MainScreen(assistant, onSettings = { screen = "settings" }, onProvider = { screen = "provider" }, onWake = { screen = "wake" })
            }
        }
    }
}
