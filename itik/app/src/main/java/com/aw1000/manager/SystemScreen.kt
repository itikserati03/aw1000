package com.aw1000.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun KV(k: String, v: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v ?: "Tidak tersedia", color = if (v == null) C.warn else C.text, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun Gauge(title: String, value: String, frac: Float, modifier: Modifier) {
    NeonCard(modifier, pad = 12.dp) {
        Text(title, color = C.muted, fontSize = 12.sp)
        Text(value, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ThinBar(frac)
    }
}

@Composable
fun SystemScreen(m: AppModel) {
    val s = m.snap
    val memPct = if (s != null && s.memTotal > 0) s.memUsed.toFloat() / s.memTotal else null
    val storPct = if (s?.storTotal != null && s.storUsed != null) s.storUsed.toFloat() / s.storTotal else null
    val temp = s?.cpuTemp ?: s?.temps?.firstOrNull()?.second

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Maklumat Sistem", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Gauge("CPU", s?.cpu?.let { "${it.toInt()}%" } ?: "—", ((s?.cpu ?: 0.0) / 100.0).toFloat(), Modifier.weight(1f))
            Gauge("RAM", memPct?.let { "${(it * 100).toInt()}%" } ?: "—", memPct ?: 0f, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Gauge("Suhu", temp?.let { "${it.toInt()}°C" } ?: "tiada", ((temp ?: 0.0) / 100.0).toFloat(), Modifier.weight(1f))
            Gauge("Storan", storPct?.let { "${(it * 100).toInt()}%" } ?: "tiada", storPct ?: 0f, Modifier.weight(1f))
        }
        NeonCard {
            Text("Maklumat Router", color = C.cyan, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            KV("Model", s?.model)
            KV("Firmware", s?.firmware?.takeIf { it != "-" })
            KV("Kernel", s?.kernel?.takeIf { it != "-" })
            KV("Tempoh Hidup", s?.let { Fmt.uptime(it.uptime) })
            KV("Beban Purata", s?.let { Fmt.d1(it.load) })
            KV("Suhu Modem 5G", s?.modemTemp?.let { "${it.toInt()}°C" })
        }
        NeonCard {
            Text("Maklumat Rangkaian", color = C.cyan, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            KV("Status Internet", when (m.internet) { true -> "Dalam talian"; false -> "Luar talian"; null -> null })
            KV("Antara Muka WAN", s?.takeIf { it.wanUp }?.let { "${it.wanName} (${it.wanDev})" })
            KV("Protokol WAN", s?.wanProto?.takeIf { it != "-" })
            KV("IP WAN", s?.wanIp)
            KV("IP LAN", s?.lanIp)
        }
        NeonCard {
            Text("Maklumat 4G/5G", color = C.cyan, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            KV("Isyarat", null); KV("Band", null); KV("Status SIM", null); KV("Kekuatan", null)
            Text(
                "Firmware tidak mendedahkan data modem melalui ubus, jadi aplikasi tidak memaparkan bacaan yang direka. " +
                    "Suhu modem dipaparkan hanya jika wujud sebagai sensor thermal.",
                color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        val notes = s?.notes ?: emptyList()
        if (notes.isNotEmpty()) NeonCard {
            Text("Nota", color = C.warn, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            notes.forEach { Text("• $it", color = C.muted, fontSize = 12.sp) }
        }
    }
}
