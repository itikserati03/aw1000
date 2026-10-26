package com.aw1000.manager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun iconFor(host: String): ImageVector {
    val h = host.lowercase()
    return when {
        Regex("tv|bravia|roku|chromecast").containsMatchIn(h) -> Icons.Filled.Tv
        Regex("print|epson|canon|hp-").containsMatchIn(h) -> Icons.Filled.Print
        Regex("ipad|tablet|tab").containsMatchIn(h) -> Icons.Filled.Tablet
        Regex("laptop|pc|desktop|macbook|windows|notebook").containsMatchIn(h) -> Icons.Filled.Computer
        else -> Icons.Filled.Smartphone
    }
}

@Composable
fun DevicesScreen(m: AppModel) {
    val clients = m.snap?.clients ?: emptyList()
    var filter by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Client?>(null) }
    val wifi = clients.count { it.wifi }
    val shown = when (filter) { 1 -> clients.filter { it.wifi }; 2 -> clients.filter { !it.wifi }; else -> clients }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = C.card, titleContentColor = C.text, textContentColor = C.muted,
            title = { Text("Sekat Internet?") },
            text = {
                Text(
                    "Peranti ${if (c.host != "-") c.host else c.mac} (${c.ip}) tidak akan dapat mengakses Internet sehingga anda membukanya semula. " +
                        "Jangan sekat telefon ini sendiri. Perubahan akan dilaksanakan pada firewall router dan disahkan."
                )
            },
            confirmButton = { TextButton(onClick = { m.setBlocked(c.mac, true); confirm = null }) { Text("Sekat", color = C.bad) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal", color = C.cyan) } }
        )
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Peranti Bersambung", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (m.refreshing) CircularProgressIndicator(color = C.cyan, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { m.refreshNow() }) { Icon(Icons.Filled.Refresh, "Muat semula", tint = C.cyan) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Semua (${clients.size})", "Wi-Fi ($wifi)", "LAN (${clients.size - wifi})").forEachIndexed { i, t ->
                val sel = i == filter
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) Brush.horizontalGradient(listOf(C.cyan, C.blue)) else Brush.horizontalGradient(listOf(C.card, C.card)))
                        .clickable { filter = i },
                    contentAlignment = Alignment.Center
                ) { Text(t, color = if (sel) C.bg else C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (shown.isEmpty()) NeonCard { Text("Tiada peranti dikesan.", color = C.muted, fontSize = 14.sp) }
        shown.forEach { c ->
            val isBlocked = c.mac in m.blocked
            val usage = m.nlbw?.get(c.mac)
            val name = if (c.host != "-") c.host else "Peranti ${c.mac.takeLast(5)}"
            NeonCard(Modifier.clickable { open = if (open == c.mac) null else c.mac }, pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFor(c.host), null, tint = if (isBlocked) C.bad else C.cyan, modifier = Modifier.size(30.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.ip, color = C.muted, fontSize = 12.sp)
                        Text(c.mac, color = C.muted, fontSize = 11.sp)
                    }
                    if (m.busyMac == c.mac) CircularProgressIndicator(color = C.cyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Switch(
                        checked = !isBlocked,
                        onCheckedChange = { allow -> if (allow) m.setBlocked(c.mac, false) else confirm = c },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = C.good, checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF2A3355), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
                if (open == c.mac) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("Sambungan: ${if (c.wifi) "Wi-Fi (${c.dev})" else "LAN (${c.dev})"}", color = C.muted, fontSize = 12.sp)
                        c.signal?.let { Text("Isyarat: $it dBm", color = C.muted, fontSize = 12.sp) }
                        Text(
                            if (usage != null) "Data tempoh nlbwmon: turun ${Fmt.bytes(usage.first)} / naik ${Fmt.bytes(usage.second)}"
                            else "Penggunaan data: tidak tersedia (nlbwmon tiada)",
                            color = C.muted, fontSize = 12.sp
                        )
                        Text(if (isBlocked) "Status: Internet disekat" else "Status: Internet dibenarkan", color = if (isBlocked) C.bad else C.good, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(
            "Suis mati = Internet disekat melalui peraturan firewall router (zon lan → wan). Sambungan sedia ada mungkin bertahan seketika selepas sekatan.",
            color = C.muted, fontSize = 11.sp
        )
    }
}
