package com.jinwoo.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Void = Color(0xFF05060B)
val Navy = Color(0xFF0A1226)
val Panel = Color(0xFF0E1830)
val ShadowPurple = Color(0xFF8E4DFF)
val AlertRed = Color(0xFFFF4D6D)
val TextSoft = Color(0xFF9FB4D6)

object Accents {
    val names = listOf("blue", "purple", "crimson")
    fun color(name: String): Color = when (name) {
        "purple" -> Color(0xFFB07CFF)
        "crimson" -> Color(0xFFFF4466)
        else -> Color(0xFF3FA9FF)
    }
}

@Composable
fun JinWooTheme(accent: String, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accents.color(accent), secondary = ShadowPurple, background = Void, surface = Panel,
            onPrimary = Color.Black, onBackground = Color(0xFFDCE8FF), onSurface = Color(0xFFDCE8FF), error = AlertRed,
        ),
        content = content,
    )
}
