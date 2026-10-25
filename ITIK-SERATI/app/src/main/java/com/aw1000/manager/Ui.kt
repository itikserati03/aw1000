package com.aw1000.manager

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

val Bg = Color(0xFF050A1A)
val BgTop = Color(0xFF0A1535)
val CardBg = Color(0xFF0B1328)
val Cyan = Color(0xFF22D3EE)
val Blue = Color(0xFF3B82F6)
val Purple = Color(0xFFA855F7)
val Magenta = Color(0xFFD946EF)
val Green = Color(0xFF22C55E)
val Red = Color(0xFFEF4444)
val Amber = Color(0xFFF59E0B)
val Dim = Color(0xFF8B97B8)
val NeonBrush = Brush.horizontalGradient(listOf(Cyan, Blue, Purple))
const val NA = "Tiada data"

@Composable
fun ItikTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Cyan, secondary = Purple, background = Bg, surface = CardBg,
        onSurface = Color.White, onBackground = Color.White, surfaceVariant = CardBg, onSurfaceVariant = Dim
    ),
    content = content
)

@Composable
fun NeonCard(modifier: Modifier = Modifier, accent: Color = Cyan, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), CardBg)))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), Purple.copy(alpha = 0.35f))), shape)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun Lbl(t: String, c: Color = Dim) = Text(t, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)

@Composable
fun GradText(t: String, size: Int, italic: Boolean = true) = Text(
    t, fontSize = size.sp, fontWeight = FontWeight.ExtraBold,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    style = TextStyle(brush = NeonBrush)
)

@Composable
fun GradButton(text: String, enabled: Boolean = true, loading: Boolean = false, color: List<Color> = listOf(Cyan, Blue, Purple),
               onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(Brush.horizontalGradient(if (enabled) color else listOf(Color(0xFF2A3350), Color(0xFF2A3350))))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Centerval shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (selected) Brush.horizontalGradient(listOf(Cyan, Blue)) else Brush.horizontalGradient(listOf(CardBg, CardBg)))
            .border(1.dp, if (selected) Color.Transparent else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

@Composable
fun InfoRow(label: String, value: String?, valueColor: Color = Color.White) = Row(
    Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween
) {
    Text(label, color = Dim, fontSize = 13.sp)
    Spacer(Modifier.width(12.dp))
    Text(value ?: NA, color = if (value == null) Dim else valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
}

@Composable
fun PulseDot(color: Color) {
    val a by rememberInfiniteTransition(label = "p").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
    )
    Box(Modifier.size(16.dp).clip(CircleShape).background(color.copy(alpha = a)))
}

@Composable
fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    NeonCard(modifier, accent = Blue) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(label, color = Dim, fontSize = 10.sp, maxLines = 1)
        }
    }
}

// ---------------- Logo itik ----------------
private const val BODY = "M10,62 C10,48 30,44 48,46 L50,36 L70,40 C66,50 78,52 86,58 C86,76 64,86 44,84 C24,82 10,74 10,62 Z"
private const val HEAD = "M46,28 A16,16 0 1,1 78,28 A16,16 0 1,1 46,28 Z"
private const val BEAK = "M76,24 L94,30 L76,36 Z"
private const val WAVE = "M4,94 Q16,86 28,94 T52,94 T76,94 T98,94"
private const val WING = "M26,64 C34,56 50,56 62,64 C54,74 38,76 26,64 Z"

@Composable
fun Duck(size: Dp) {
    val body = remember { PathParser().parsePathString(BODY).toPath() }
    val head = remember { PathParser().parsePathString(HEAD).toPath() }
    val beak = remember { PathParser().parsePathString(BEAK).toPath() }
    val wave = remember { PathParser().parsePathString(WAVE).toPath() }
    val wing = remember { PathParser().parsePathString(WING).toPath() }
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 100f
        scale(k, Offset.Zero) {
            drawPath(body, Brush.linearGradient(listOf(Blue, Purple), Offset(10f, 40f), Offset(86f, 86f)))
            drawPath(wing, Color(0x55FFFFFF))
            drawPath(head, Brush.linearGradient(listOf(Cyan, Blue), Offset(46f, 12f), Offset(78f, 44f)))
            drawPath(beak, Brush.linearGradient(listOf(Blue, Magenta), Offset(76f, 24f), Offset(94f, 36f)))
            drawCircle(Bg, 2.6f, Offset(66f, 25f))
            drawPath(wave, Brush.horizontalGradient(listOf(Cyan, Purple), 4f, 98f), style = Stroke(3f, cap = StrokeCap.Round))
        }
    }
}
@Composable
fun TrafficGraph(rx: List<Float>, tx: List<Float>, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val top = niceMax(((rx + tx).maxOrNull()) ?: 0f)
    Canvas(modifier) {
        val left = 34.dp.toPx(); val w = size.width - left; val h = size.height - 4.dp.toPx()
        val ts = TextStyle(color = Dim, fontSize = 9.sp)
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(Color(0x22FFFFFF), Offset(left, y), Offset(size.width, y), 1f)
            val v = top * (3 - i) / 3f
            val lbl = if (v >= 10f) "${v.roundToInt()}" else String.format(java.util.Locale.US, "%.1f", v)
            val l = tm.measure(lbl, ts)
            drawText(tm, lbl, Offset(0f, (y - l.size.height / 2f).coerceIn(0f, size.height - l.size.height)), ts)
        }
        fun line(d: List<Float>, c: Color) {
            if (d.size < 2) return
            val pts = d.mapIndexed { i, v -> Offset(left + w * i / (d.size - 1), h - h * (v / top).coerceIn(0f, 1f)) }
            val p = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]; val b = pts[i]; val mx = (a.x + b.x) / 2
                    cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                }
            }
            val fill = Path().apply { addPath(p); lineTo(pts.last().x, h); lineTo(pts[0].x, h); close() }
            drawPath(fill, Brush.verticalGradient(listOf(c.copy(alpha = 0.25f), Color.Transparent), startY = 0f, endY = h))
            drawPath(p, c, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        line(rx, Cyan); line(tx, Magenta)
    }
}

@Composable
fun BarChart(values: List<Long?>, selected: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val mx = max(1L, values.filterNotNull().maxOrNull() ?: 1L).toFloat()
        val gap = 2.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val x = i * (bw + gap)
            if (v == null) {
                drawRect(Color(0x22FFFFFF), Offset(x, size.height - 2.dp.toPx()), Size(bw, 2.dp.toPx()))
            } else {
                val h = max(3.dp.toPx(), size.height * (v.toFloat() / mx))
                drawRoundRect(
                    Brush.verticalGradient(listOf(if (i == selected) Magenta else Purple, Cyan), startY = size.height - h, endY = size.height),
                    Offset(x, size.height - h), Size(bw, h), CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}

private val TICKS = listOf(0f, 10f, 50f, 100f, 300f, 500f, 1000f)
fun gaugeFrac(v: Float): Float {
    val x = v.coerceIn(0f, 1000f)
    for (i in 0 until TICKS.size - 1) if (x <= TICKS[i + 1]) return (i + (x - TICKS[i]) / (TICKS[i + 1] - TICKS[i])) / (TICKS.size - 1)
    return 1f
}

@Composable
fun SpeedGauge(mbps: Float, big: String, modifier: Modifier = Modifier) {center = c
            )
            if (frac > 0.003f) drawArc(grad, 135f, 270f * frac, false, Offset(pad, pad), arc, style = Stroke(sw, cap = StrokeCap.Round))
            val ts = TextStyle(color = Color.White, fontSize = (w * 0.04f / density * 1f).coerceAtLeast(9f).sp)
            TICKS.forEachIndexed { i, t ->
                val a = (135f + 270f * i / (TICKS.size - 1)) * PI.toFloat() / 180f
                val rr = r - sw * 1.6f
                val s = t.roundToInt().toString(); val l = tm.measure(s, ts)
                drawText(tm, s, Offset(c.x + rr * cos(a) - l.size.width / 2f, c.y + rr * sin(a) - l.size.height / 2f), ts)
            }
            val a = (135f + 270f * frac) * PI.toFloat() / 180f
            val tip = Offset(c.x + r * 0.66f * cos(a), c.y + r * 0.66f * sin(a))
            drawLine(Brush.linearGradient(listOf(Cyan, Magenta), c, tip), c, tip, strokeWidth = w * 0.014f, cap = StrokeCap.Round)
            drawCircle(Bg, w * 0.032f, c); drawCircle(Cyan, w * 0.032f, c, style = Stroke(w * 0.01f))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("Mbps", color = Dim, fontSize = 13.sp)
        }
    }
}
    val frac by animateFloatAsState(gaugeFrac(mbps), tween(350), label = "g")
    val tm = rememberTextMeasurer()
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension; val sw = w * 0.07f; val pad = sw / 2 + w * 0.02f
            val arc = Size(w - 2 * pad, w - 2 * pad); val c = Offset(w / 2, w / 2); val r = arc.width / 2
            drawArc(Color(0x22FFFFFF), 135f, 270f, false, Offset(pad, pad), arc, style = Stroke(sw, cap = StrokeCap.Round))
            val grad = Brush.sweepGradient(
                0f to Magenta,
                0.125f to Magenta,
                0.375f to Cyan,
                0.6f to Blue,
                0.85f to Purple,
                1f to Magenta,
@Composable
fun Logo(size: Dp = 34.dp, textSize: Int = 22) = Row(verticalAlignment = Alignment.CenterVertically) {
    Duck(size); Spacer(Modifier.width(8.dp)); GradText("ITIK SERATI", textSize)
}

// ---------------- Graf ----------------
fun niceMax(v: Float): Float {
    val t = v * 1.1f
    return listOf(1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 300f, 500f, 1000f, 2000f, 5000f).firstOrNull { it >= t } ?: max(t, 1f)
}
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
