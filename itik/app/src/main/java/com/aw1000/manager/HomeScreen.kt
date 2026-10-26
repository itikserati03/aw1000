package com.aw1000.manager

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    NeonCard(modifier, pad = 10.dp) {
        Text(label, color = C.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(value, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (sub != null) Text(sub, color = C.muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SpeedBig(title: String, mbps: Double?, icon: ImageVector, tint: Color, modifier: Modifier) {
    NeonCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            Text(title, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (mbps == null) "—" else Fmt.speed(mbps), color = C.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Mbps", color = C.muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun HomeScreen(m: AppModel) {
    val s = m.snap
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot"
    )
    val net = m.internet
    val (stText, stColor) = when (net) {
        true -> "ONLINE" to C.good
        false -> "OFFLINE" to C.bad
        null -> "MENYEMAK…" to C.muted
    }
    val todayUsage = m.dataLog.usage(java.time.LocalDate.now()).also { m.logVersion }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        m.connError?.let {
            NeonCard { Text("Sambungan bermasalah: $it", color = C.warn, fontSize = 13.sp) }
        }
        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).alpha(if (net == true) pulse else 1f).clip(CircleShape).background(stColor))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(stText, color = stColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (s == null) "—" else "WAN - ${s.wanName} (${s.wanProto})" + (s.wanIp?.let { " • $it" } ?: ""),
                        color = C.muted, fontSize = 13.sp
                    )
                }
            }
            m.pingNote?.let { Text(it, color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedBig("Muat Turun", s?.let { Fmt.mbps(it.rxBps) }, Icons.Filled.ArrowDownward, C.cyan, Modifier.weight(1f))
            SpeedBig("Muat Naik", s?.let { Fmt.mbps(it.txBps) }, Icons.Filled.ArrowUpward, C.pink, Modifier.weight(1f))
        }
        NeonCard {
            Text("Kelajuan Masa Nyata", color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Text("● Muat Turun", color = C.cyan, fontSize = 11.sp)
                Text("● Muat Naik", color = C.pink, fontSize = 11.sp)
                Text("Mbps", color = C.muted, fontSize = 11.sp)
            }
            TrafficChart(m.downHist, m.upHist, Modifier.fillMaxWidth().height(140.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Ping", m.ping?.let { "${Fmt.speed(it.avg)} ms" } ?: "—", Modifier.weight(1f))
            StatTile("Jitter", m.ping?.let { "${Fmt.speed(it.jitter)} ms" } ?: "—", Modifier.weight(1f))
            StatTile("Paket Hilang", m.ping?.let { "${Fmt.speed(it.loss)}%" } ?: "—", Modifier.weight(1f))
            StatTile("Rangkaian", s?.wanProto ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Peranti Bersambung", s?.clients?.size?.toString() ?: "—", Modifier.weight(1f))
            StatTile("Data Hari Ini", if (todayUsage.total > 0) Fmt.bytes(todayUsage.total) else "—", Modifier.weight(1f))
            StatTile("Tempoh Hidup", s?.let { Fmt.uptime(it.uptime) } ?: "—", Modifier.weight(1f))
        }
        NeonCard {
            Text("Ringkasan Sistem", color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            MiniMeter("CPU", s?.cpu?.let { "${it.toInt()}%" } ?: "—", (s?.cpu ?: 0.0).toFloat() / 100f)
            val memPct = if (s != null && s.memTotal > 0) s.memUsed.toFloat() / s.memTotal else 0f
            MiniMeter("RAM", if (s != null && s.memTotal > 0) "${(memPct * 100).toInt()}%" else "—", memPct)
            val t = s?.cpuTemp ?: s?.temps?.firstOrNull()?.second
            MiniMeter("Suhu", t?.let { "${it.toInt()}°C" } ?: "tiada sensor", ((t ?: 0.0).toFloat() / 100f))
        }
    }
}

@Composable
fun MiniMeter(name: String, value: String, fraction: Float) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = C.muted, fontSize = 13.sp)
            Text(value, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        ThinBar(fraction)
    }
}
