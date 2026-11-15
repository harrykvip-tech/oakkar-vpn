package app.oakkar.vpn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private val SpTeal = Color(0xFF0E7C86)
private val SpGold = Color(0xFFE0A030)
private val SpNavy = Color(0xFF14204A)
private val SpMuted = Color(0xFF5F6F7A)
private val SpLine = Color(0xFFCFE6E3)

/*
 * Download / upload card under the connect button.
 *  - chevrons flow down (download) / up (upload) while data is moving, rest faintly when idle
 *  - the speed number glides smoothly to its new value instead of jumping
 *  - a small live graph shows the last 30 readings
 */
@Composable
fun SpeedCard(down: Long, up: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, SpLine),
        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpeedHalf("Download", down, true, SpTeal, Modifier.weight(1f))
            SpeedHalf("Upload", up, false, SpGold, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeedHalf(label: String, value: Long, isDown: Boolean, color: Color, modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "arrows")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "t")
    val shown by animateFloatAsState(value.toFloat(), tween(600), label = "speed")
    val active = value > 0
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(value) {
        history.add(value.toFloat())
        if (history.size > 30) history.removeAt(0)
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(32.dp)) {
                val w = size.width
                val h = size.height
                val hw = 8.dp.toPx()
                val d = 6.dp.toPx()
                for (i in 0..2) {
                    val ph = (t + i / 3f) % 1f
                    val a = if (active) sin(PI.toFloat() * ph) else if (i == 1) 0.35f else 0f
                    if (a <= 0f) continue
                    val y = if (isDown) h * (0.15f + 0.7f * ph) else h * (0.85f - 0.7f * ph)
                    val yy = if (active) y else h / 2f
                    val s = if (isDown) -1f else 1f // chevron points down for download, up for upload
                    val p = Path().apply {
                        moveTo(w / 2f - hw, yy + s * d / 2f)
                        lineTo(w / 2f, yy - s * d / 2f)
                        lineTo(w / 2f + hw, yy + s * d / 2f)
                    }
                    drawPath(
                        p, (if (active) color else SpMuted).copy(alpha = a),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(fmtBytes(shown.toLong()) + "/s", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = SpNavy)
                Text(label, fontSize = 12.sp, color = SpMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val w = size.width
            val h = size.height
            val pad = 3.dp.toPx()
            drawLine(SpLine, Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())
            val n = history.size
            if (n >= 2) {
                val max = (history.maxOrNull() ?: 1f).coerceAtLeast(1f)
                fun px(i: Int) = i / (n - 1f) * w
                fun py(v: Float) = h - pad - (v / max) * (h - 2f * pad)
                val line = Path().apply {
                    moveTo(px(0), py(history[0]))
                    for (i in 1 until n) lineTo(px(i), py(history[i]))
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(w, h); lineTo(0f, h); close()
                }
                drawPath(fill, color.copy(alpha = 0.15f))
                drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
