package com.jinwoo.ai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.jinwoo.ai.JinWooApp
import com.jinwoo.ai.assistant.AssistantState
import com.jinwoo.ai.assistant.AssistantViewModel
import com.jinwoo.ai.ui.components.MicIcon
import com.jinwoo.ai.ui.components.SystemEmblem
import com.jinwoo.ai.ui.theme.AlertRed
import com.jinwoo.ai.ui.theme.Accents
import com.jinwoo.ai.ui.theme.Navy
import com.jinwoo.ai.ui.theme.ShadowPurple
import com.jinwoo.ai.ui.theme.TextSoft
import com.jinwoo.ai.ui.theme.Void
import com.jinwoo.ai.wake.WakeState

@Composable
fun MainScreen(vm: AssistantViewModel, onSettings: () -> Unit, onProvider: () -> Unit, onWake: () -> Unit) {
    val container = (LocalContext.current.applicationContext as JinWooApp).container
    val ui by vm.ui.collectAsState()
    val s by container.settings.flow.collectAsState()
    val wake by container.wakeBus.state.collectAsState()
    val voiceReady by container.voice.isReady.collectAsState()
    val accent = Accents.color(s.accent)
    var typed by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(ui.lines.size, ui.streaming) {
        val n = ui.lines.size + if (ui.streaming.isNotEmpty()) 1 else 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Void, Navy, Void)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings", tint = accent) }
            }
            SystemEmblem(ui.state, accent, s.particles, ui.activationVisible, Modifier.size(210.dp))
            Text("JIN WOO", fontSize = 30.sp, letterSpacing = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(6.dp))

            val sysStatus = when (ui.state) { AssistantState.Offline -> "OFFLINE"; AssistantState.Error -> "ERROR"; else -> "ONLINE" }
            val wakeText = when {
                !s.wakeWordEnabled -> "DISABLED"
                wake is WakeState.Listening -> "READY"
                wake is WakeState.Paused -> "PAUSED"
                wake is WakeState.Error -> "ERROR"
                else -> "STOPPED - TAP TO SET UP"
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatusLine("SYSTEM STATUS", sysStatus + "  [" + ui.state.name.uppercase() + "]", if (ui.state == AssistantState.Error) AlertRed else accent, null)
                StatusLine("AI PROVIDER", s.provider.uppercase() + (s.activeModel().takeIf { it.isNotBlank() }?.let { " / $it" } ?: ""), accent, onProvider)
                StatusLine("VOICE SYSTEM", if (voiceReady) "ACTIVE" else "INACTIVE", if (voiceReady) accent else AlertRed, null)
                StatusLine("WAKE WORD", wakeText, if (wake is WakeState.Error) AlertRed else accent, onWake)
            }
            if (ui.message.isNotBlank()) {
                Text(ui.message, color = AlertRed, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp).clickable { vm.dismissMessage() })
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ui.lines) { Bubble(it.role == "user", it.text, accent) }
                if (ui.streaming.isNotEmpty()) item { Bubble(false, ui.streaming, accent) }
                if (ui.partial.isNotEmpty()) item { Bubble(true, ui.partial + " ...", accent, faded = true) }
            }

            ui.pending?.let { p ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14102A)), border = BorderStroke(1.dp, ShadowPurple),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("CONFIRMATION REQUIRED", color = ShadowPurple, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text(p.prompt, Modifier.padding(vertical = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ vm.resolvePending(true) }) { Text("CONFIRM") }
                            OutlinedButton({ vm.resolvePending(false) }) { Text("CANCEL") }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    typed, { typed = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Type a command...", color = TextSoft) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.submitText(typed); typed = "" }),
                    shape = RoundedCornerShape(24.dp),
                )
                IconButton({ vm.submitText(typed); typed = "" }, enabled = typed.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = accent)
                }
                Spacer(Modifier.size(6.dp))
                MicButton(ui.state, accent) { vm.onMic() }
            }
        }

        if (ui.activationVisible) ActivationOverlay(accent)
    }
}

@Composable
private fun StatusLine(label: String, value: String, color: Color, onClick: (() -> Unit)?) {
    Row(Modifier.padding(vertical = 1.dp).let { if (onClick != null) it.clickable { onClick() } else it }, verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", color = TextSoft, fontSize = 11.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 11.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Bubble(isUser: Boolean, text: String, accent: Color, faded: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(if (isUser) Color(0xFF16213D) else Color(0xFF0F1A33)).alpha(if (faded) 0.6f else 1f).padding(10.dp).fillMaxWidth(0.82f)
        ) {
            Text(if (isUser) "YOU" else "JIN WOO", color = if (isUser) TextSoft else accent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(text, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MicButton(state: AssistantState, accent: Color, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "mic")
    val glow by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    val active = state == AssistantState.Listening || state == AssistantState.Speaking
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(if (active) accent.copy(alpha = 0.18f + 0.2f * glow) else Color(0xFF0E1830))
            .clickable { onClick() }.padding(14.dp),
        contentAlignment = Alignment.Center,
    ) { MicIcon(if (active) Color.White else accent, Modifier.size(34.dp)) }
}

@Composable
private fun ActivationOverlay(accent: Color) {
    val inf = rememberInfiniteTransition(label = "act")
    val a by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(300.dp))
            Text("JIN WOO", color = accent.copy(alpha = a), fontSize = 18.sp, letterSpacing = 10.sp, fontFamily = FontFamily.Monospace)
            Text("SYSTEM ACTIVATED", color = Color.White.copy(alpha = a), fontSize = 22.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
