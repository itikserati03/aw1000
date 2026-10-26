package com.aw1000.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun NeonField(
    value: String, onChange: (String) -> Unit, label: String, icon: ImageVector,
    password: Boolean = false, show: Boolean = false, onToggle: () -> Unit = {}, keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        leadingIcon = { Icon(icon, null, tint = C.muted) },
        trailingIcon = if (password) ({
            IconButton(onClick = onToggle) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Sembunyi kata laluan" else "Tunjuk kata laluan", tint = C.muted)
            }
        }) else null,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = C.text, unfocusedTextColor = C.text,
            focusedBorderColor = C.cyan, unfocusedBorderColor = Color(0xFF2A3A75),
            focusedLabelColor = C.cyan, unfocusedLabelColor = C.muted, cursorColor = C.cyan
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun LoginScreen(m: AppModel) {
    var ip by remember { mutableStateOf(m.savedIp) }
    var user by remember { mutableStateOf(m.savedUser) }
    var pw by remember { mutableStateOf(m.savedPw) }
    var show by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(m.savedRemember) }
    var adv by remember { mutableStateOf(false) }
    var https by remember { mutableStateOf(m.savedHttps) }
    var self by remember { mutableStateOf(m.savedSelf) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(C.bg, Color(0xFF0B1240), C.bg)))
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        DuckLogo(Modifier.size(130.dp))
        GradText("ITIK SERATI", 38.sp, italic = true)
        Text("PENGURUS ROUTER ANDA", color = C.muted, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(24.dp))
        NeonCard(pad = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonField(ip, { ip = it }, "Alamat IP Router", Icons.Filled.Language, keyboard = KeyboardType.Uri)
                NeonField(user, { user = it }, "Nama Pengguna", Icons.Filled.Person)
                NeonField(pw, { pw = it }, "Kata Laluan", Icons.Filled.Lock, password = true, show = show, onToggle = { show = !show })
                Row(Modifier.clickable { save = !save }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(save, { save = it }, colors = CheckboxDefaults.colors(checkedColor = C.cyan, uncheckedColor = C.muted, checkmarkColor = C.bg))
                    Text("Simpan maklumat log masuk (disulitkan)", color = C.text, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GradButton(if (m.loginBusy) "Menyambung…" else "Log Masuk  →", enabled = !m.loginBusy) {
            m.login(ip, user, pw, save, https, self)
        }
        if (m.loginBusy) { Spacer(Modifier.height(10.dp)); CircularProgressIndicator(color = C.cyan, modifier = Modifier.size(24.dp)) }
        m.loginError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = C.bad, fontSize = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.clickable { adv = !adv }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, null, tint = C.cyan, modifier = Modifier.size(18.dp))
            Text("  Tetapan Lanjutan", color = C.text, fontSize = 14.sp)
        }
        if (adv) {
            NeonCard {
                Row(Modifier.clickable { https = !https }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(https, { https = it }, colors = CheckboxDefaults.colors(checkedColor = C.cyan, uncheckedColor = C.muted, checkmarkColor = C.bg))
                    Text("Guna HTTPS", color = C.text, fontSize = 14.sp)
                }
                Row(Modifier.clickable { self = !self }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(self, { self = it }, colors = CheckboxDefaults.colors(checkedColor = C.cyan, uncheckedColor = C.muted, checkmarkColor = C.bg))
                    Text("Terima sijil HTTPS swatandatang router ini sahaja", color = C.muted, fontSize = 13.sp)
                }
                Text("HTTP biasa tidak disulitkan. Gunakan hanya pada rangkaian rumah yang dipercayai.", color = C.muted, fontSize = 12.sp)
            }
        }
    }
}
