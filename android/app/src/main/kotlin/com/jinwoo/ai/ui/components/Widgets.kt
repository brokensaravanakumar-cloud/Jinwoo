package com.jinwoo.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinwoo.ai.ui.theme.TextSoft

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun SwitchRow(title: String, subtitle: String = "", checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TextSoft, fontSize = 12.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, display: (Float) -> String = { "%.2f".format(it) }) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row { Text(title, Modifier.weight(1f)); Text(display(value), color = TextSoft) }
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
fun DropdownRow(title: String, current: String, options: List<String>, onPick: (String) -> Unit, label: (String) -> String = { it }) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(label(current).ifEmpty { "(default)" }, maxLines = 1) }
            DropdownMenu(open, { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(label(o).ifEmpty { "(default)" }) }, onClick = { open = false; onPick(o) }) }
            }
        }
    }
}

@Composable
fun ActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(text) }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        TextButton(onBack) { Text("< BACK") }
        Text(title.uppercase(), letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
    }
}

/** Hand-drawn microphone (the Material "Mic" icon lives in the large icons-extended library, which we avoid). */
@Composable
fun MicIcon(color: Color, modifier: Modifier = Modifier.size(34.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val sw = w * 0.09f
        drawRoundRect(color, Offset(w * 0.36f, h * 0.08f), Size(w * 0.28f, h * 0.5f), androidx.compose.ui.geometry.CornerRadius(w * 0.14f))
        drawArc(color, 0f, 180f, false, Offset(w * 0.22f, h * 0.28f), Size(w * 0.56f, h * 0.45f), style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.73f), Offset(w * 0.5f, h * 0.92f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.92f), Offset(w * 0.66f, h * 0.92f), sw, StrokeCap.Round)
    }
}
