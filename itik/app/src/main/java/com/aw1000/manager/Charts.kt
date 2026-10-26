package com.aw1000.manager

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun DrawScope.smoothPath(vals: List<Float>, maxV: Float, cap: Int, close: Boolean): Path {
    val p = Path()
    if (vals.isEmpty()) return p
    val step = size.width / (cap - 1).coerceAtLeast(1)
    fun x(i: Int) = size.width - (vals.size - 1 - i) * step
    fun y(v: Float) = size.height - (v / maxV).coerceIn(0f, 1f) * size.height
    p.moveTo(x(0), y(vals[0]))
    for (i in 1 until vals.size) {
        val cx = (x(i - 1) + x(i)) / 2f
        p.cubicTo(cx, y(vals[i - 1]), cx, y(vals[i]), x(i), y(vals[i]))
    }
    if (close) { p.lineTo(x(vals.size - 1), size.height); p.lineTo(x(0), size.height); p.close() }
    return p
}

/** Graf trafik masa nyata (Mbps): muat turun (cyan) dan muat naik (ungu). */
@Composable
fun TrafficChart(down: List<Float>, up: List<Float>, modifier: Modifier = Modifier) {
    val peak = maxOf(down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f)
    val maxV = maxOf(10f, peak * 1.25f)
    Row(modifier) {
        Column(Modifier.width(34.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(Fmt.speed(maxV.toDouble()), color = C.muted, fontSize = 10.sp)
            Text(Fmt.speed(maxV / 2.0), color = C.muted, fontSize = 10.sp)
            Text("0", color = C.muted, fontSize = 10.sp)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            for (i in 0..2) {
                val y = size.height * i / 2f
                drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val cap = 60
            if (down.isNotEmpty()) {
                drawPath(smoothPath(down, maxV, cap, true), Brush.verticalGradient(listOf(C.cyan.copy(alpha = 0.25f), Color.Transparent)))
                drawPath(smoothPath(down, maxV, cap, false), C.cyan, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            if (up.isNotEmpty()) {
                drawPath(smoothPath(up, maxV, cap, true), Brush.verticalGradient(listOf(C.pink.copy(alpha = 0.2f), Color.Transparent)))
                drawPath(smoothPath(up, maxV, cap, false), C.pink, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/** Carta bar. [highlight] ialah indeks bar yang diserlahkan (atau -1). [labels] ditunjuk bagi sebahagian bar sahaja. */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, highlight: Int, unit: String, modifier: Modifier = Modifier) {
    val maxV = maxOf(values.maxOrNull() ?: 0f, 0.0001f)
    Column(modifier) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.width(38.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("${Fmt.d1(maxV.toDouble())} $unit", color = C.muted, fontSize = 10.sp)
                Text("${Fmt.d1(maxV / 2.0)}", color = C.muted, fontSize = 10.sp)
                Text("0", color = C.muted, fontSize = 10.sp)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                for (i in 0..2) {
                    val y = size.height * i / 2f
                    drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                if (values.isEmpty()) return@Canvas
                val slot = size.width / values.size
                val bw = slot * 0.6f
                values.forEachIndexed { i, v ->
                    val h = (v / maxV) * size.height
                    val c = if (i == highlight || highlight < 0) listOf(C.cyan, C.purple) else listOf(C.cyan.copy(alpha = 0.5f), C.purple.copy(alpha = 0.5f))
                    if (h > 0f) drawRoundRect(
                        Brush.verticalGradient(c, startY = size.height - h, endY = size.height),
                        Offset(slot * i + (slot - bw) / 2f, size.height - h), Size(bw, h), CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 38.dp, top = 4.dp)) {
            labels.forEach { Text(it, color = C.muted, fontSize = 10.sp, modifier = Modifier.weight(1f)) }
        }
    }
}

/** Meter bulat dengan jarum bergerak. Skala tidak linear 0–1000 Mbps. */
@Composable
fun SpeedGauge(mbps: Double, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState(Parse.gaugeFraction(mbps).toFloat(), tween(350), label = "gauge")
    val marks = listOf("0", "10", "50", "100", "300", "500", "1000")
    Canvas(modifier.aspectRatio(1f)) {
        val sw = 16.dp.toPx()
        val r = size.minDimension / 2f - sw
        val c = Offset(size.width / 2f, size.height / 2f)
        val tl = Offset(c.x - r, c.y - r)
        val sz = Size(2 * r, 2 * r)
        val start = 150f; val sweep = 240f
        drawArc(Color(0xFF1B2650), start, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        if (frac > 0.001f) {
            drawArc(
                Brush.linearGradient(listOf(C.cyan, C.blue, C.pink), Offset(c.x - r, c.y), Offset(c.x + r, c.y)),
                start, sweep * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true; color = 0xFFEAF0FF.toInt(); textSize = 12.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER
        }
        marks.forEachIndexed { i, t ->
            val a = Math.toRadians((start + sweep * i / 6f).toDouble())
            val rr = r - sw * 1.5f
            drawContext.canvas.nativeCanvas.drawText(t, c.x + (rr * Math.cos(a)).toFloat(), c.y + (rr * Math.sin(a)).toFloat() + 4.dp.toPx(), paint)
        }
        val na = Math.toRadians((start + sweep * frac).toDouble())
        val tip = Offset(c.x + (r * 0.8f * Math.cos(na)).toFloat(), c.y + (r * 0.8f * Math.sin(na)).toFloat())
        drawLine(Brush.linearGradient(listOf(C.cyan, C.pink), c, tip), c, tip, 4.dp.toPx(), StrokeCap.Round)
        drawCircle(C.bg, 9.dp.toPx(), c)
        drawCircle(C.cyan, 6.dp.toPx(), c)
    }
}
