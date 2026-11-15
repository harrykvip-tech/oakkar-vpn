package app.oakkar.vpn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

internal val GTeal = Color(0xFF0E7C86)
internal val GAccent = Color(0xFF4FC4BB)
internal val GGold = Color(0xFFF2B84B)
internal val GMuted = Color(0xFF4A5A66)

/** White "liquid glass" surface: translucent white gradient, bright edge, soft teal shadow. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), strong: Boolean = false): Modifier =
    this
        .shadow(14.dp, shape, ambientColor = GTeal.copy(alpha = 0.25f), spotColor = GTeal.copy(alpha = 0.25f))
        .background(
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (strong) 0.92f else 0.78f),
                    Color.White.copy(alpha = if (strong) 0.58f else 0.38f),
                ),
            ),
            shape,
        )
        .border(1.dp, Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.35f))), shape)

/** White-to-ice-blue background with three soft colour blobs that slowly drift. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val inf = rememberInfiniteTransition(label = "orbs")
    val t by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Box(modifier.background(Brush.linearGradient(listOf(Color.White, Color(0xFFEAF6F6), Color(0xFFDDEFF5))))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun orb(c: Color, x: Float, y: Float, r: Float, phase: Float) {
                val a = (t + phase) * 6.2831855f
                val cx = x + cos(a) * 26.dp.toPx()
                val cy = y + sin(a) * 22.dp.toPx()
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.65f), c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                    r, Offset(cx, cy),
                )
            }
            orb(Color(0xFF7FD6CF), w * 0.10f, h * 0.18f, 200.dp.toPx(), 0f)
            orb(Color(0xFFB9D8FF), w * 0.95f, h * 0.48f, 220.dp.toPx(), 0.33f)
            orb(Color(0xFFFFE3A6), w * 0.30f, h * 0.88f, 170.dp.toPx(), 0.66f)
        }
        content()
    }
}

/** Floating glass bottom bar. */
@Composable
fun GlassNav(tabs: List<Triple<String, ImageVector, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), strong = true).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { (label, icon, i) ->
                val on = selected == i
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (on) GAccent.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, contentDescription = label, tint = if (on) GTeal else GMuted)
                    Text(label, fontSize = 11.sp, color = if (on) GTeal else GMuted)
                }
            }
        }
    }
}
