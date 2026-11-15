package app.oakkar.vpn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val BtnTeal = Color(0xFF0E7C86)
private val BtnAccent = Color(0xFF4FC4BB)
private val BtnGold = Color(0xFFF2B84B)
private val BtnLine = Color(0xFFCFE6E3)
private val BtnSoft = Color(0xFFD9F1EE)
private val PIf = PI.toFloat()

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun overshoot(t: Float): Float { val u = t - 1f; return 1f + 2.70158f * u * u * u + 1.70158f * u * u }

/*
 * Big round connect button (replaces the plain white circle on Home).
 *  Disconnected : soft ring breathes in and out, grey globe floating
 *  Connecting   : teal light arc + gold dot orbit the circle, globe spins fast and blinks
 *  Connected    : ripple rings keep spreading out, circle turns mint, globe turns teal and keeps spinning slowly
 *  Connecting -> Connected (1 s, one time): phone buzzes, two shock-wave rings burst out,
 *                14 gold/teal sparks fly outward
 */
@Composable
fun ConnectButton(status: Status, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "connect")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")

    val connected = status == Status.Connected
    val connecting = status == Status.Connecting

    // one-shot "connected" celebration: 0 -> 1 over 1 s (starts finished so nothing plays on first draw)
    val burst = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    var prev by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        val justConnected = status == Status.Connected && prev == Status.Connecting
        prev = status
        if (justConnected) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1000, easing = LinearEasing))
        }
    }

    val pop by animateFloatAsState(
        if (connected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pop",
    )
    val lockColor by animateColorAsState(if (connected) BtnTeal else Color.Gray, tween(300), label = "lock")
    val fill by animateColorAsState(if (connected) BtnSoft else Color.White, tween(400), label = "fill")
    val edge by animateColorAsState(if (connected) BtnTeal else BtnLine, tween(300), label = "edge")

    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = 110.dp.toPx()

            val q = burst.value
            if (q < 1f) {
                val e = 1f - (1f - q) * (1f - q) * (1f - q)
                drawCircle(
                    BtnTeal.copy(alpha = 0.8f * (1f - q)), base + e * 70.dp.toPx(), c,
                    style = Stroke(width = (1f + 5f * (1f - q)).dp.toPx()),
                )
                val q2 = clamp01((q - 0.15f) / 0.85f)
                val e2 = 1f - (1f - q2) * (1f - q2) * (1f - q2)
                if (q2 > 0f) drawCircle(
                    BtnAccent.copy(alpha = 0.7f * (1f - q2)), base + e2 * 50.dp.toPx(), c,
                    style = Stroke(width = (1f + 3f * (1f - q2)).dp.toPx()),
                )
                for (i in 0 until 14) {
                    val a = (i * 360f / 14f + 8f) * PIf / 180f
                    val d = base + e * (36.dp.toPx() + (i % 3) * 14.dp.toPx())
                    drawCircle(
                        (if (i % 2 == 0) BtnGold else BtnAccent).copy(alpha = 1f - q),
                        (1.5f + 5f * (1f - q)).dp.toPx(), Offset(c.x + cos(a) * d, c.y + sin(a) * d),
                    )
                }
            }
            when {
                connected -> for (k in 0..2) {
                    val q = (t + k / 3f) % 1f
                    drawCircle(
                        BtnAccent.copy(alpha = 0.5f * (1f - q)), radius = base + q * 28.dp.toPx(), center = c,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
                connecting -> {
                    val r = base + 8.dp.toPx()
                    drawCircle(BtnAccent.copy(alpha = 0.18f), r, c, style = Stroke(width = 2.dp.toPx()))
                    drawArc(
                        BtnTeal, startAngle = spin, sweepAngle = 100f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(2f * r, 2f * r),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    )
                    val a = ((spin + 180f) * PI / 180.0).toFloat()
                    drawCircle(BtnGold, 4.dp.toPx(), Offset(c.x + cos(a) * r, c.y + sin(a) * r))
                }
                else -> {
                    val b = sin(t * 2f * PI.toFloat()) * 0.5f + 0.5f
                    drawCircle(
                        BtnLine.copy(alpha = 0.9f - 0.6f * b), radius = base + 6.dp.toPx() + b * 8.dp.toPx(), center = c,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, edge, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // globe: still + floating (off), spins fast and blinks (connecting), spins slowly in teal (connected)
            Canvas(Modifier.size(96.dp)) {
                val r = 34.dp.toPx()
                val sw = 4.dp.toPx()
                val a = if (connecting) 0.6f + 0.4f * (sin(spin * PIf / 90f) * 0.5f + 0.5f) else 1f
                val col = lockColor.copy(alpha = a)
                val bob = if (connected) 0f else sin(t * 2f * PIf) * 3.dp.toPx()
                val phase = when {
                    connecting -> spin * PIf / 360f
                    connected -> t * PIf
                    else -> 0.6f
                }
                val k = if (connecting) 0.9f else 0.9f + 0.1f * pop
                val cx = center.x
                val cy = center.y + bob
                val st = Stroke(width = sw, cap = StrokeCap.Round)
                scale(k, k, center) {
                    drawCircle(col, r, Offset(cx, cy), style = st)
                    drawLine(col, Offset(cx - r, cy), Offset(cx + r, cy), sw, StrokeCap.Round)
                    for (sign in listOf(-1f, 1f)) {
                        val y = cy + sign * r * 0.5f
                        val half = sqrt(r * r - (r * 0.5f) * (r * 0.5f))
                        drawLine(col, Offset(cx - half, y), Offset(cx + half, y), sw * 0.75f, StrokeCap.Round)
                    }
                    for (i in 0..2) {
                        val rx = r * abs(cos(phase + i * PIf / 3f))
                        if (rx > 1.5.dp.toPx()) {
                            drawOval(col, Offset(cx - rx, cy - r), Size(2f * rx, 2f * r), style = Stroke(width = sw * 0.75f))
                        }
                    }
                }
            }
        }
    }
}
