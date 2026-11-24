package com.jinwoo.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.jinwoo.ai.assistant.AssistantState
import com.jinwoo.ai.ui.theme.AlertRed
import com.jinwoo.ai.ui.theme.ShadowPurple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val x: Float, val y: Float, val speed: Float, val size: Float, val drift: Float)

/** Original animated "System eye" emblem. State changes colour and tempo; [burst] plays the activation shock-wave. */
@Composable
fun SystemEmblem(state: AssistantState, accent: Color, particles: Boolean, burst: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "emblem")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "rot")
    val time by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "time")
    val beat = when (state) { AssistantState.Listening -> 850; AssistantState.Speaking -> 480; AssistantState.Processing -> 650; else -> 2400 }
    val pulse by inf.animateFloat(0.88f, 1.08f, infiniteRepeatable(tween(beat, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val shock by animateFloatAsState(if (burst) 1f else 0f, if (burst) tween(1700) else snap(), label = "shock")
    val seeds = remember { List(46) { Particle(Random.nextFloat(), Random.nextFloat(), 0.25f + Random.nextFloat() * 0.75f, 1.2f + Random.nextFloat() * 3f, Random.nextFloat() * 6.28f) } }

    val color = when (state) {
        AssistantState.Error -> AlertRed
        AssistantState.Processing -> ShadowPurple
        AssistantState.Speaking -> Color.White.copy(alpha = 0.92f).compositeOver(accent, 0.45f)
        AssistantState.Offline, AssistantState.Sleeping -> accent.copy(alpha = 0.55f)
        else -> accent
    }

    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        // aura
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * pulse), ShadowPurple.copy(alpha = 0.18f), Color.Transparent), c, r * 1.05f), r * 1.05f, c)
        // rings
        drawCircle(color.copy(alpha = 0.35f), r * 0.92f, c, style = Stroke(2f))
        rotate(rot, c) {
            drawCircle(color.copy(alpha = 0.8f), r * 0.80f, c, style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 18f))))
        }
        rotate(-rot * 1.6f, c) {
            drawCircle(ShadowPurple.copy(alpha = 0.7f), r * 0.66f, c, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f))))
        }
        // angular rune ticks
        for (i in 0 until 12) {
            val a = (i * 30f + rot * 0.3f) * (PI.toFloat() / 180f)
            drawLine(color.copy(alpha = 0.5f), c + Offset(cos(a), sin(a)) * (r * 0.95f), c + Offset(cos(a), sin(a)) * (r * 0.99f), 3f)
        }
        // eye
        val w = r * 0.62f; val h = r * 0.34f * (if (state == AssistantState.Sleeping) 0.55f else pulse)
        val eye = Path().apply {
            moveTo(c.x - w, c.y); quadraticBezierTo(c.x, c.y - h * 1.9f, c.x + w, c.y)
            quadraticBezierTo(c.x, c.y + h * 1.9f, c.x - w, c.y); close()
        }
        drawPath(eye, Brush.radialGradient(listOf(Color(0xFF0A1A33), Color(0xFF05060B)), c, w))
        drawPath(eye, color, style = Stroke(4f))
        val iris = r * 0.2f * pulse
        drawCircle(Brush.radialGradient(listOf(Color.White, color, color.copy(alpha = 0.2f)), c, iris * 1.6f), iris, c)
        val slit = Path().apply { moveTo(c.x, c.y - iris * 0.9f); lineTo(c.x + iris * 0.22f, c.y); lineTo(c.x, c.y + iris * 0.9f); lineTo(c.x - iris * 0.22f, c.y); close() }
        drawPath(slit, Color(0xFF05060B))
        // shadow particles
        if (particles) seeds.forEach { p ->
            val py = ((p.y - time * p.speed) % 1f + 1f) % 1f
            val px = p.x + sin(time * 6.28f + p.drift) * 0.02f
            drawCircle(ShadowPurple.copy(alpha = 0.15f + 0.55f * (1f - py)), p.size, Offset(px * size.width, py * size.height))
        }
        // activation shock-wave
        if (shock > 0.001f) {
            drawCircle(color.copy(alpha = (1f - shock) * 0.9f), r * (0.2f + shock * 1.6f), c, style = Stroke(10f * (1f - shock) + 2f))
            drawCircle(ShadowPurple.copy(alpha = (1f - shock) * 0.6f), r * (0.1f + shock * 1.1f), c, style = Stroke(6f))
        }
    }
}

private fun Color.compositeOver(background: Color, amount: Float): Color =
    Color(red * amount + background.red * (1 - amount), green * amount + background.green * (1 - amount),
        blue * amount + background.blue * (1 - amount), 1f)
