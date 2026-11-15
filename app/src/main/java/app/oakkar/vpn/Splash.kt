package app.oakkar.vpn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/*
 * Launch animation (3.8 s, all vector, no image files). Tap anywhere to skip.
 *  0.0-1.0s  it is dark; a boy walks in from the left and stops
 *  1.0-1.8s  he raises a flashlight; a big circle of light grows and reveals the logo card
 *  1.8-2.2s  the lights come on (whole logo visible), he lowers the flashlight
 *  2.3-3.5s  he walks away to the right, in front of the logo
 *  3.4-3.8s  fade into Home
 * Change DURATION / FADE_START to make it shorter or longer.
 */

private const val DURATION = 3.8f    // total splash time (seconds)
private const val FADE_START = 3.4f  // fade-out begins here
private const val PIf = PI.toFloat()

private val Night = Color(0xFF020D0F)
private val Dark = Color(0xFF062F34)
private val Teal = Color(0xFF0E7C86)
private val Acc = Color(0xFF4FC4BB)
private val Gold = Color(0xFFF2B84B)
private val Mint = Color(0xFFEFF9F8)
private val Navy = Color(0xFF14204A)
private val Muted = Color(0xFF5F6F7A)
private val Skin = Color(0xFFF2C9A0)
private val Hair = Color(0xFF2B1B14)
private val Shirt = Color(0xFFF7F7F2)
private val Pants = Color(0xFF3B5BA9)
private val Light = Color(0xFFFFECAA)

private fun clamp(x: Float, a: Float = 0f, b: Float = 1f) = x.coerceIn(a, b)
private fun easeOut(t: Float) = 1f - (1f - t).pow(3)

@Composable
fun Splash(onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(DURATION, tween((DURATION * 1000).toInt(), easing = LinearEasing))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - clamp((t.value - FADE_START) / (DURATION - FADE_START)) }
            .background(Night)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawScene(t.value) }
    }
}

private fun DrawScope.drawScene(t: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h / 2
    val cardW = minOf(w * 0.72f, 300.dp.toPx())
    val cardH = cardW * 0.62f
    val u = cardH * 0.5f / 10f
    val ground = cy + cardH * 0.53f

    // lights come on: background goes from near-black to the app's dark teal
    val lo = easeOut(clamp((t - 1.8f) / 0.4f))
    drawRect(lerp(Night, Dark, lo))

    // big circle of light that reveals the logo
    val r = cardW * 0.85f * easeOut(clamp((t - 1.0f) / 0.6f)) * (1f + 0.015f * sin(t * 9f))
    if (r > 1f && lo < 0.99f) {
        val circle = Path().apply { addOval(Rect(center = Offset(cx, cy), radius = r)) }
        clipPath(circle) { drawCard(cx, cy, cardW, cardH, 1f - lo) }
        drawCircle(
            brush = Brush.radialGradient(
                0f to Light.copy(alpha = 0.20f),
                0.27f to Light.copy(alpha = 0.20f),
                0.9f to Light.copy(alpha = 0.10f),
                1f to Light.copy(alpha = 0f),
                center = Offset(cx, cy),
                radius = r * 1.12f,
            ),
            radius = r * 1.12f,
            center = Offset(cx, cy),
            alpha = 1f - lo,
        )
    }
    if (lo > 0.005f) drawCard(cx, cy, cardW, cardH, lo)

    // the boy: walks in, stops and shines, then walks away in front of the logo
    val bx = when {
        t < 1.0f -> {
            val p = clamp(t / 1.0f)
            -0.12f * w + 0.24f * w * (1f - (1f - p) * (1f - p))
        }
        t < 2.3f -> 0.12f * w
        else -> 0.12f * w + 1.0f * w * clamp((t - 2.3f) / 1.2f)
    }
    val amp = if (t < 1.5f) 1f - easeOut(clamp((t - 0.85f) / 0.15f)) else easeOut(clamp((t - 2.3f) / 0.1f))
    val phase = minOf(t, 1.0f) * 12f + (t - 2.3f).coerceIn(0f, 1.2f) * 12f
    val up = easeOut(clamp((t - 1.0f) / 0.25f)) * (1f - easeOut(clamp((t - 2.05f) / 0.25f)))
    val torch = drawBoy(bx, ground, u, phase, amp, up, cx, cy, 0.8f + 0.2f * lo)

    // thin cone from the flashlight towards the logo
    if (up > 0.05f) {
        val dist = hypot(cx - torch.x, cy - torch.y)
        val cone = Path().apply {
            moveTo(torch.x, torch.y)
            lineTo(torch.x + cos(torch.theta - 0.2f) * dist, torch.y + sin(torch.theta - 0.2f) * dist)
            lineTo(torch.x + cos(torch.theta + 0.2f) * dist, torch.y + sin(torch.theta + 0.2f) * dist)
            close()
        }
        drawPath(
            cone,
            brush = Brush.radialGradient(
                0f to Light.copy(alpha = 0.5f),
                1f to Light.copy(alpha = 0.06f),
                center = Offset(torch.x, torch.y),
                radius = dist,
            ),
            alpha = up * (1f - lo),
        )
    }
}

/** The logo card: shield + name. Drawn on the canvas so the circle of light can clip it. */
private fun DrawScope.drawCard(cx: Float, cy: Float, cardW: Float, cardH: Float, al: Float) {
    val tl = Offset(cx - cardW / 2, cy - cardH / 2)
    val radius = CornerRadius(cardH * 0.1f)
    drawRoundRect(Color.Black, tl + Offset(0f, cardH * 0.04f), Size(cardW, cardH), radius, alpha = 0.25f * al)
    drawRoundRect(Mint, tl, Size(cardW, cardH), radius, alpha = al)

    val s = cardH * 0.17f
    val sy = cy - cardH * 0.2f
    drawPowerLogo(Offset(cx, sy), s / 34f, Teal.copy(alpha = al), Gold.copy(alpha = al))

    val fs = 22f * cardW / 259f
    val y1 = cy - cardH / 2 + cardH * 0.52f + fs
    val y2 = y1 + 19f * cardW / 259f
    drawIntoCanvas { c ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.textAlign = android.graphics.Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = fs
        p.color = Navy.copy(alpha = al).toArgb()
        c.nativeCanvas.drawText("Oakkar VPN", cx, y1, p)
        p.typeface = android.graphics.Typeface.DEFAULT
        p.textSize = 13f * cardW / 259f
        p.color = Muted.copy(alpha = al).toArgb()
        c.nativeCanvas.drawText("လုံခြုံစွာ ချိတ်ဆက်ပါ", cx, y2, p)
    }
}

private class Torch(val x: Float, val y: Float, val theta: Float)

/** Draws the boy with his feet on [ground]; returns where the flashlight head is and which way it points. */
private fun DrawScope.drawBoy(
    x: Float, ground: Float, u: Float, phase: Float, amp: Float, up: Float,
    tx: Float, ty: Float, al: Float,
): Torch {
    val legLen = 3.4f * u
    val bob = abs(sin(phase)) * 0.25f * u * amp
    val hipY = ground - legLen - bob
    val shY = hipY - 3.2f * u
    val headY = shY - 1.5f * u
    val swing = sin(phase) * 0.65f * amp
    val armSwing = sin(phase) * 0.6f * amp
    val sh = Offset(x, shY + 0.5f * u)
    val armLen = 2.8f * u

    fun leg(a: Float) {
        val foot = Offset(x + sin(a) * legLen, hipY + cos(a) * legLen)
        drawLine(Pants, Offset(x, hipY), foot, strokeWidth = u, cap = StrokeCap.Round, alpha = al)
        drawOval(Acc, Offset(foot.x - 0.5f * u, foot.y - 0.3f * u), Size(1.6f * u, 0.7f * u), alpha = al)
    }

    // back leg + back arm
    leg(-swing)
    val ba = -armSwing
    drawLine(Skin, sh, Offset(sh.x + sin(ba) * armLen, sh.y + cos(ba) * armLen), strokeWidth = 0.9f * u, cap = StrokeCap.Round, alpha = al)

    // torso + head
    drawRoundRect(Shirt, Offset(x - 1.35f * u, shY), Size(2.7f * u, 3.3f * u), CornerRadius(0.7f * u), alpha = al)
    val head = Offset(x + 0.15f * u, headY)
    drawCircle(Skin, 1.45f * u, head, alpha = al)
    drawArc(Hair, 180f, 180f, true, Offset(head.x - 1.5f * u, head.y - 1.5f * u), Size(3f * u, 3f * u), alpha = al)
    drawCircle(Hair, 0.5f * u, Offset(x - 0.9f * u, headY - 0.9f * u), alpha = al)
    drawCircle(Navy, 0.17f * u, Offset(x + 0.85f * u, headY + 0.1f * u), alpha = al)

    // front leg + front arm holding the flashlight
    leg(swing)
    val fa = armSwing * (1f - up) + 1.75f * up
    val hand = Offset(sh.x + sin(fa) * armLen, sh.y + cos(fa) * armLen)
    drawLine(Skin, sh, hand, strokeWidth = 0.9f * u, cap = StrokeCap.Round, alpha = al)
    val ang = if (up > 0.01f) atan2(ty - hand.y, tx - hand.x) * up + 1.2f * (1f - up) else 1.2f
    rotate(degrees = ang * 180f / PIf, pivot = hand) {
        drawRoundRect(Color(0xFF6B7280), Offset(hand.x - 0.3f * u, hand.y - 0.4f * u), Size(1.7f * u, 0.8f * u), CornerRadius(0.2f * u), alpha = al)
        drawRoundRect(Gold, Offset(hand.x + 1.3f * u, hand.y - 0.6f * u), Size(0.8f * u, 1.2f * u), CornerRadius(0.2f * u), alpha = al)
    }
    return Torch(hand.x + cos(ang) * 2.1f * u, hand.y + sin(ang) * 2.1f * u, ang)
}
