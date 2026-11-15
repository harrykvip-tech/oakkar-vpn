package app.oakkar.vpn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val BtnTeal = Color(0xFF0E7C86)
private val BtnAccent = Color(0xFF4FC4BB)
private val BtnGold = Color(0xFFF2B84B)
private val PIf = PI.toFloat()

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** The Oakkar logo (Power Ring): open ring with a gold bar. [k] = pixels per logo unit (logo is 100 units wide). */
internal fun DrawScope.drawPowerLogo(c: Offset, k: Float, ring: Color, line: Color) {
    val r = 34f * k
    val sw = 9f * k
    drawArc(
        ring, startAngle = 305f, sweepAngle = 290f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(2f * r, 2f * r),
        style = Stroke(width = sw, cap = StrokeCap.Round),
    )
    drawLine(line, Offset(c.x, c.y - 40f * k), Offset(c.x, c.y - 2f * k), strokeWidth = sw, cap = StrokeCap.Round)
}

/*
 * Liquid-glass connect button.
 *  Disconnected : glass disc, soft ring breathes, grey-teal logo gently pulses
 *  Connecting   : teal arc + gold dot orbit the disc, logo pulses faster
 *  Connected    : ripple rings spread out, disc glows teal, logo lights up
 *  Connecting -> Connected (1 s, once): phone buzzes, two shock-wave rings and 14 sparks burst out
 *  A light streak slides across the glass all the time.
 */
@Composable
fun ConnectButton(status: Status, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "connect")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val shine by inf.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "shine")

    val connected = status == Status.Connected
    val connecting = status == Status.Connecting

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

    val glow by animateFloatAsState(if (connected) 1f else 0f, tween(500), label = "glow")

    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = 105.dp.toPx()

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
                    val p = (t + k / 3f) % 1f
                    drawCircle(
                        BtnAccent.copy(alpha = 0.5f * (1f - p)), radius = base + p * 28.dp.toPx(), center = c,
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
                    val b = sin(t * 2f * PIf) * 0.5f + 0.5f
                    drawCircle(
                        Color.White.copy(alpha = 0.9f - 0.5f * b), radius = base + 4.dp.toPx() + b * 8.dp.toPx(), center = c,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(210.dp)
                .glass(CircleShape, strong = true)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val w = size.width

                // teal glow inside the glass when connected
                if (glow > 0.01f) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(BtnAccent.copy(alpha = 0.45f * glow), Color.Transparent),
                            center = c, radius = w * 0.46f,
                        ),
                        radius = w * 0.46f, center = c,
                    )
                }

                // light streak sliding across the glass
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent),
                        start = Offset(w * shine - w * 0.25f, 0f),
                        end = Offset(w * shine + w * 0.25f, size.height),
                    ),
                )

                // logo
                val pulse = if (connecting) 1f + 0.08f * sin(spin * PIf / 180f) else 1f + 0.05f * sin(t * 2f * PIf)
                val k = 1.2.dp.toPx() * pulse
                drawPowerLogo(
                    c, k,
                    ring = lerp(Color(0xFF9BB3B6), BtnTeal, glow),
                    line = lerp(Color(0xFFD9C79A), BtnGold, glow),
                )
            }
        }
    }
}
