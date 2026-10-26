package com.aw1000.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val model by lazy { AppModel(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ItikTheme { Root(model) } }
    }

    override fun onResume() { super.onResume(); if (model.loggedIn) model.startPolling() }
    override fun onPause() { super.onPause(); model.stopPolling() }
}

@Composable
fun Root(m: AppModel) {
    Box(Modifier.fillMaxSize().background(C.bg)) {
        if (m.loggedIn) Shell(m) else LoginScreen(m)
    }
}

private fun tabIcon(t: Tab): ImageVector = when (t) {
    Tab.UTAMA -> Icons.Filled.Home
    Tab.DATA -> Icons.Filled.BarChart
    Tab.SPEED -> Icons.Filled.Speed
    Tab.PERANTI -> Icons.Filled.Devices
    Tab.SISTEM -> Icons.Filled.Settings
}

@Composable
fun Shell(m: AppModel) {
    var askLogout by remember { mutableStateOf(false) }
    BackHandler(enabled = m.tab != Tab.UTAMA) { m.tab = Tab.UTAMA }

    if (askLogout) {
        AlertDialog(
            onDismissRequest = { askLogout = false },
            containerColor = C.card, titleContentColor = C.text, textContentColor = C.muted,
            title = { Text("Log keluar?") },
            text = { Text("Sambungan ke router akan ditutup.") },
            confirmButton = { TextButton(onClick = { askLogout = false; m.logout() }) { Text("Log Keluar", color = C.bad) } },
            dismissButton = { TextButton(onClick = { askLogout = false }) { Text("Batal", color = C.cyan) } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { askLogout = true }) { Icon(Icons.Filled.PowerSettingsNew, "Log keluar", tint = C.cyan) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                DuckLogo(Modifier.size(36.dp))
                Box(Modifier.padding(start = 6.dp)) { GradText("ITIK SERATI", 22.sp, italic = true) }
            }
            IconButton(onClick = { m.refreshNow() }) { Icon(Icons.Filled.Refresh, "Muat semula", tint = C.cyan) }
        }
        Box(Modifier.weight(1f)) {
            Crossfade(m.tab, label = "tab") { t ->
                when (t) {
                    Tab.UTAMA -> HomeScreen(m)
                    Tab.DATA -> DataScreen(m)
                    Tab.SPEED -> SpeedScreen(m)
                    Tab.PERANTI -> DevicesScreen(m)
                    Tab.SISTEM -> SystemScreen(m)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(C.card, C.bg))).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Tab.values().forEach { t ->
                val sel = m.tab == t
                Column(
                    Modifier.weight(1f).clickable { m.tab = t }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(tabIcon(t), t.label, tint = if (sel) C.cyan else C.muted, modifier = Modifier.size(24.dp))
                    Text(t.label, color = if (sel) C.cyan else C.muted, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
