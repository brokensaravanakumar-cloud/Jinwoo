package com.jinwoo.ai.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinwoo.ai.JinWooApp
import com.jinwoo.ai.assistant.SettingsViewModel
import com.jinwoo.ai.ui.components.ActionButton
import com.jinwoo.ai.ui.components.BackBar
import com.jinwoo.ai.ui.components.DropdownRow
import com.jinwoo.ai.ui.components.SectionTitle
import com.jinwoo.ai.ui.theme.TextSoft

@Composable
fun ProviderScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as JinWooApp).container
    val s by container.settings.flow.collectAsState()
    val models by vm.models.collectAsState()
    val info by vm.info.collectAsState()
    val busy by vm.busy.collectAsState()
    var token by remember { mutableStateOf(vm.token()) }
    val model = s.activeModel()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackBar("AI Provider", onBack)

        SectionTitle("Provider")
        DropdownRow("Provider", s.provider, listOf("gemini", "openai"), { p -> container.settings.update { it.copy(provider = p) }; vm.clearInfo() }) { it.uppercase() }
        Text("API keys live only in the backend's .env file, never in this app.", color = TextSoft, fontSize = 12.sp)

        SectionTitle("Model")
        OutlinedTextField(
            model, { m -> container.settings.update { if (it.provider == "openai") it.copy(openAiModel = m.trim()) else it.copy(geminiModel = m.trim()) } },
            Modifier.fillMaxWidth(), singleLine = true, label = { Text("Model ID (custom allowed; blank = backend default)") },
        )
        ActionButton("Load available models from backend", !busy) { vm.loadModels() }
        if (models.isNotEmpty()) DropdownRow("Pick model", model, models, { m ->
            container.settings.update { if (it.provider == "openai") it.copy(openAiModel = m) else it.copy(geminiModel = m) }
        })

        SectionTitle("Backend")
        OutlinedTextField(
            s.backendUrl, { u -> container.settings.update { it.copy(backendUrl = u.trim()) } },
            Modifier.fillMaxWidth(), singleLine = true, label = { Text("Backend URL (https://...)") },
        )
        OutlinedTextField(
            token, { token = it; vm.setToken(it) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Backend access token (stored encrypted)") }, visualTransformation = PasswordVisualTransformation(),
        )

        SectionTitle("Connection")
        ActionButton("Check backend status", !busy) { vm.serverStatus() }
        ActionButton("Test AI connection", !busy) { vm.testConnection() }
        if (info.isNotBlank()) Text(info, color = TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
