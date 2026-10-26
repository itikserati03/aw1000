package com.aw1000.manager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF050A1C)
    val card = Color(0xFF0B1536)
    val cardHi = Color(0xFF111E4A)
    val cyan = Color(0xFF22D3EE)
    val blue = Color(0xFF3B82F6)
    val purple = Color(0xFFA855F7)
    val pink = Color(0xFFE040FB)
    val text = Color(0xFFEAF0FF)
    val muted = Color(0xFF8FA0C8)
    val good = Color(0xFF22E56B)
    val bad = Color(0xFFFF4D6A)
    val warn = Color(0xFFFFB020)
    val grad = Brush.horizontalGradient(listOf(cyan, blue, purple))
    val border = Brush.linearGradient(listOf(cyan.copy(alpha = 0.55f), purple.copy(alpha = 0.55f)))
}

@Composable
fun ItikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.cyan, secondary = C.purple, background = C.bg, surface = C.card,
            onPrimary = C.bg, onBackground = C.text, onSurface = C.text
        ),
        content = content
    )
}

@Composable
fun NeonCard(modifier: Modifier = Modifier, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.cardHi, C.card)), shape)
            .border(BorderStroke(1.dp, C.border), shape)
            .padding(pad),
        content = content
    )
}

@Composable
fun GradText(text: String, size: TextUnit, weight: FontWeight = FontWeight.Bold, italic: Boolean = false) {
    Text(
        text,
        style = TextStyle(
            brush = C.grad, fontSize = size, fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
        )
    )
}

@Composable
fun GradButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth().height(54.dp).clip(shape)
            .background(if (enabled) Brush.horizontalGradient(listOf(C.cyan, C.blue, C.purple)) else Brush.horizontalGradient(listOf(Color(0xFF2A3355), Color(0xFF2A3355))), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SegTabs(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(C.card, shape).border(BorderStroke(1.dp, C.border), shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(21.dp))
                    .background(if (sel) Brush.horizontalGradient(listOf(C.cyan, C.blue)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Text(o, color = if (sel) C.bg else C.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
    }
}

@Composable
fun ThinBar(fraction: Float, modifier: Modifier = Modifier, color: Brush = Brush.horizontalGradient(listOf(C.cyan, C.purple))) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "bar")
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1B2650))) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

private const val DUCK_BODY = "M34,60 C30,70 40,84 56,84 C72,84 82,74 82,62 C82,56 76,52 70,50 L62,46 C60,54 50,56 44,54 C38,52 36,56 34,60 Z"
private const val DUCK_HEAD = "M51,38 a11,11 0 1,0 22,0 a11,11 0 1,0 -22,0 Z"
private const val DUCK_BEAK = "M72,36 L86,41 L72,45 Z"
private const val DUCK_WING = "M46,66 C54,76 68,74 72,64 C64,68 54,66 46,66 Z"

/** Logo itik serati (dilukis dengan Canvas, tanpa gambar luar). */
@Composable
fun DuckLogo(modifier: Modifier = Modifier) {
    val body: Path = androidx.compose.runtime.remember { PathParser().parsePathString(DUCK_BODY).toPath() }
    val head: Path = androidx.compose.runtime.remember { PathParser().parsePathString(DUCK_HEAD).toPath() }
    val beak: Path = androidx.compose.runtime.remember { PathParser().parsePathString(DUCK_BEAK).toPath() }
    val wing: Path = androidx.compose.runtime.remember { PathParser().parsePathString(DUCK_WING).toPath() }
    Canvas(modifier) {
        val s = size.minDimension / 108f
        scale(s, Offset.Zero) {
            val g = Brush.linearGradient(listOf(C.cyan, C.blue, C.purple), Offset(30f, 30f), Offset(86f, 86f))
            drawPath(body, g)
            drawPath(head, Brush.linearGradient(listOf(C.cyan, C.blue), Offset(50f, 26f), Offset(74f, 50f)))
            drawPath(beak, C.pink.copy(alpha = 0.9f))
            drawPath(wing, Color(0xFF0B1536).copy(alpha = 0.55f))
            drawCircle(C.bg, 2f, Offset(65f, 35.5f))
        }
    }
}
