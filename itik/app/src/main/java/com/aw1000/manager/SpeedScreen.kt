package com.aw1000.manager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpeedScreen(m: AppModel) {
    var all by remember { mutableStateOf(false) }
    val routerLive = m.speedRunning && m.speedMode == 1
    val shown = if (routerLive) (m.snap?.let { Fmt.mbps(it.rxBps) } ?: 0.0) else m.liveMbps
    val gaugeVal = if (m.speedRunning) shown else (m.resUp ?: m.resDown ?: 0.0)
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Speedtest", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SegTabs(listOf("Dari Telefon", "Pada Router"), m.speedMode) { if (!m.speedRunning) m.speedMode = it }
        NeonCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SpeedGauge(gaugeVal, Modifier.fillMaxWidth(0.85f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 120.dp)) {
                    Text(Fmt.speed(gaugeVal), color = C.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("Mbps", color = C.muted, fontSize = 14.sp)
                }
            }
            Text(
                when {
                    m.speedRunning -> m.speedPhase
                    m.speedPhase == "Selesai" -> "Ujian selesai"
                    else -> "Tekan Mula untuk menguji"
                },
                color = C.cyan, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultTile("Muat Turun", m.resDown, Icons.Filled.ArrowDownward, C.cyan, Modifier.weight(1f))
            ResultTile(if (m.speedMode == 1) "Muat Naik (tiada)" else "Muat Naik", m.resUp, Icons.Filled.ArrowUpward, C.pink, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Ping", m.resPing?.let { "${Fmt.speed(it)} ms" } ?: "—", Modifier.weight(1f))
            StatTile("Jitter", m.resJitter?.let { "${Fmt.speed(it)} ms" } ?: "—", Modifier.weight(1f))
            StatTile("Paket Hilang", m.resLoss?.let { "${Fmt.speed(it)}%" } ?: "—", Modifier.weight(1f))
            StatTile("Pelayan", "Cloudflare", Modifier.weight(1f))
        }
        m.speedError?.let { NeonCard { Text("Ujian gagal: $it", color = C.bad, fontSize = 13.sp) } }
        if (m.speedRunning) GradButton("■  HENTI") { m.stopSpeed() } else GradButton("▶  MULA SPEEDTEST") { m.startSpeed() }
        Text(
            if (m.speedMode == 0)
                "Ujian dari telefon: mengukur sambungan telefon ke Internet. Pastikan telefon pada Wi-Fi router (bukan data mudah alih) supaya ia mengukur router ini."
            else
                "Ujian pada router: router memuat turun fail Cloudflare sendiri (perlu wget HTTPS pada router). Muat naik tidak disokong dalam mod ini.",
            color = C.muted, fontSize = 12.sp
        )
        NeonCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sejarah Speedtest", color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (m.speedHistory.size > 3) Text(if (all) "Kurangkan" else "Lihat Semua", color = C.cyan, fontSize = 13.sp, modifier = Modifier.clickable { all = !all })
            }
            if (m.speedHistory.isEmpty()) Text("Belum ada ujian.", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            (if (all) m.speedHistory else m.speedHistory.take(3)).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1.4f)) {
                        Text(fmt.format(Date(r.ts)), color = C.text, fontSize = 12.sp)
                        Text(r.mode, color = C.muted, fontSize = 10.sp)
                    }
                    Text("↓ ${Fmt.speed(r.down)}", color = C.cyan, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(if (r.up != null) "↑ ${Fmt.speed(r.up)}" else "↑ —", color = C.pink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(r.ping?.let { "${Fmt.speed(it)} ms" } ?: "—", color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                }
            }
        }
    }
}

@Composable
private fun ResultTile(title: String, v: Double?, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    NeonCard(modifier, pad = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(" $title", color = tint, fontSize = 12.sp)
        }
        Text(if (v == null) "—" else Fmt.speed(v), color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Mbps", color = C.muted, fontSize = 11.sp)
    }
}
