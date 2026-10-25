package com.aw1000.manager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

val BULAN = listOf("Januari", "Februari", "Mac", "April", "Mei", "Jun", "Julai", "Ogos", "September", "Oktober", "November", "Disember")

private fun shortUp(s: Long?): String? = s?.let {
    val d = it / 86400; val h = it % 86400 / 3600; val m = it % 3600 / 60
    if (d > 0) "${d}h ${h}j" else "${h}j ${m}m"
}

@Composable
fun AppRoot(vm: VM) {
    ItikTheme {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, Bg)))) {
            if (!vm.connected) Login(vm) else Main(vm)
        }
    }
}

@Composable
fun SectionTitle(icon: ImageVector, text: String, trailing: @Composable () -> Unit = {}) = Row(
    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
) {
    Icon(icon, null, tint = Cyan, modifier = Modifier.size(26.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    trailing()
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp), content = content
)

@Composable
fun Banner(text: String, color: Color = Red) = Box(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)).padding(10.dp)
) { Text(text, color = color, fontSize = 13.sp) }

// ================= LOG MASUK =================
@Composable
fun Field(
    v: String, on: (String) -> Unit, label: String, icon: ImageVector, pw: Boolean = false,
    show: Boolean = false, onToggle: (() -> Unit)? = null, kb: KeyboardType = KeyboardType.Text
) = OutlinedTextField(
    value = v, onValueChange = on, modifier = Modifier.fillMaxWidth(),
    label = { Text(label) }, singleLine = true,
    leadingIcon = { Icon(icon, null, tint = Cyan) },
    trailingIcon = {
        if (onToggle != null) IconButton(onClick = onToggle) {
            Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Tunjuk atau sembunyi kata laluan", tint = Dim)
        }
    },
    visualTransformation = if (pw && !show) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = if (pw) KeyboardType.Password else kb),
    shape = RoundedCornerShape(14.dp),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Cyan, unfocusedBorderColor = Color(0x44FFFFFF), focusedLabelColor = Cyan,
        unfocusedLabelColor = Dim, cursorColor = Cyan, focusedTextColor = Color.White, unfocusedTextColor = Color.White
    )
)

@Composable
fun Login(vm: VM) {
    var show by remember { mutableStateOf(false) }
    var adv by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Duck(130.dp)
        GradText("ITIK SERATI", 38)
        Text("PENGURUS ROUTER ANDA", color = Dim, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(28.dp))
        NeonCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(vm.host, { vm.host = it }, "Alamat IP Router", Icons.Filled.Language, kb = KeyboardType.Uri)
                Field(vm.user, { vm.user = it }, "Nama Pengguna", Icons.Filled.Person)
                Field(vm.pass, { vm.pass = it }, "Kata Laluan", Icons.Filled.Lock, pw = true, show = show, onToggle = { show = !show })
                Row(Modifier.fillMaxWidth().clickable { vm.remember = !vm.remember }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(vm.remember, { vm.remember = it }, colors = CheckboxDefaults.colors(checkedColor = Cyan, checkmarkColor = Color.Black))
                    Text("Simpan maklumat log masuk (disulitkan)", color = Color.White, fontSize = 13.sp)
                }
                when {
                    vm.busy -> Banner("Menyambung ke ${vm.host}…", Cyan)
                    vm.error != null -> Banner(vm.error!!)
                    else -> Text("Belum disambungkan", color = Dim, fontSize = 12.sp)
                }
                GradButton("Log Masuk", loading = vm.busy) { vm.connect() }
                TextButton(onClick = { adv = !adv }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Filled.Settings, null, tint = Cyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Tetapan Lanjutan", color = Color.White)
                }
                if (adv) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(vm.https, { vm.https = it })
                        Spacer(Modifier.width(10.dp))
                        Text("Guna HTTPS (sijil router mesti sah; TLS tidak dilumpuhkan)", color = Dim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ================= RANGKA UTAMA =================
@Composable
fun Main(vm: VM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    BackHandler(enabled = tab != 0) { tab = 0 }
    LaunchedEffect(vm.msg) { vm.msg?.let { snack.showSnackbar(it); vm.msg = null } }
    vm.pendingBlock?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.pendingBlock = null },
            title = { Text("Sekat Internet?") },
            text = { Text("${c.name} (${c.ip}) tidak akan dapat mengakses Internet sehingga anda membukanya semula. Peraturan firewall akan ditambah pada router.") },
            confirmButton = { TextButton(onClick = { vm.confirmBlock() }) { Text("Sekat", color = Red) } },
            dismissButton = { TextButton(onClick = { vm.pendingBlock = null }) { Text("Batal") } },
            containerColor = CardBg
        )
    }
    val items = listOf(
        Triple("Utama", Icons.Filled.Home, 0), Triple("Data", Icons.Filled.BarChart, 1),
        Triple("Speedtest", Icons.Filled.Speed, 2), Triple("Peranti", Icons.Filled.Devices, 3),
        Triple("Sistem", Icons.Filled.Settings, 4)
    )
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(34.dp, 22)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.disconnect() }) { Icon(Icons.AutoMirrored.Filled.Logout, "Log keluar", tint = Cyan) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                items.forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0x3322D3EE),
                            unselectedIconColor = Dim, unselectedTextColor = Dim
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }, label = "tab") { t ->
                when (t) {
                    0 -> Dash(vm)
                    1 -> DataPage(vm)
                    2 -> SpeedPage(vm)
                    3 -> DevicesPage(vm)
                    else -> SystemPage(vm)
                }
            }
        }
    }
}

// ================= 1. UTAMA =================
@Composable
fun SpeedBox(label: String, bps: Double?, up: Boolean, modifier: Modifier) {
    val c = if (up) Magenta else Cyan
    NeonCard(modifier, accent = if (up) Purple else Cyan) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, tint = c, modifier = Modifier.size(30.dp))
            Text(label, color = c, fontWeight = FontWeight.SemiBold)
            Text(Parsers.fmtMbps(bps), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("Mbps", color = Dim)
        }
    }
}

@Composable
fun Cell(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = Dim, fontSize = 11.sp)
    Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
}

@Composable
fun StatCard(icon: ImageVector, label: String, value: String?, pct: Float?, sub: String? = null, modifier: Modifier = Modifier) {
    NeonCard(modifier, accent = Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, color = Dim, fontSize = 12.sp)
                Text(value ?: NA, color = if (value == null) Dim else Color.White, fontSize = if (value == null) 12.sp else 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (pct != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (pct / 100f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                color = Cyan, trackColor = Color(0x22FFFFFF))
        }
        if (sub != null) Text(sub, color = Dim, fontSize = 11.sp)
    }
}

@Composable
fun Dash(vm: VM) {
    val s = vm.s
    val ver = vm.snapVer
    Page {
        if (s.lost) Banner("Sambungan ke router terganggu. Mencuba semula…")
        NeonCard(Modifier.fillMaxWidth(), accent = if (s.online == false) Red else Green) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(when (s.online) { true -> Green; false -> Red; null -> Amber })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(when (s.online) { true -> "ONLINE"; false -> "OFFLINE"; null -> "MEMUATKAN…" },
                        color = when (s.online) { true -> Green; false -> Red; null -> Amber }, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("WAN - ${s.wan ?: NA}", color = Dim, fontSize = 13.sp)
                }
                if (s.modem != null) Text(s.modem.mode, color = Cyan, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeedBox("Muat Turun", s.rx, false, Modifier.weight(1f))
            SpeedBox("Muat Naik", s.tx, true, Modifier.weight(1f))
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kelajuan Masa Nyata", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("● Turun  ", color = Cyan, fontSize = 11.sp); Text("● Naik", color = Magenta, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                TrafficGraph(s.rxH, s.txH, Modifier.fillMaxSize())
                if (s.rxH.size < 2) Text(if (s.online == false) "Tiada trafik WAN" else "Menunggu data trafik…", color = Dim, fontSize = 12.sp)
            }
            Text("Satuan: Mbps (kaunter trafik antara muka WAN router)", color = Dim, fontSize = 10.sp)
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            Row(Modifier.fillMaxWidth()) {
                Cell("Ping", s.ping?.let { "${it.roundToInt()} ms" } ?: "—", Modifier.weight(1f))
                Cell("Jitter", Parsers.jitter(s.pingH.map { it.toDouble() })?.let { "${it.roundToInt()} ms" } ?: "—", Modifier.weight(1f))
                Cell("Rangkaian", s.modem?.mode ?: "—", Modifier.weight(1f))
                Cell("IP WAN", s.wanIp ?: "—", Modifier.weight(1.4f))
            }
            Text("Ping diukur oleh router ke ${Router.PING_TARGET}", color = Dim, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(Icons.Filled.Devices, s.clients?.size?.toString() ?: "—", "Peranti", Modifier.weight(1f))
            MiniStat(Icons.Filled.BarChart, vm.dayUsage(LocalDate.now())?.let { Parsers.fmtBytes(it) } ?: "—", "Data Hari Ini", Modifier.weight(1f))
            MiniStat(Icons.Filled.Timer, shortUp(s.uptime) ?: "—", "Tempoh Hidup", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Icons.Filled.Memory, "CPU", s.cpu?.let { "${it.roundToInt()}%" }, s.cpu, modifier = Modifier.weight(1f))
            StatCard(Icons.Filled.Storage, "RAM", s.ramPct?.let { "${it.roundToInt()}%" }, s.ramPct, modifier = Modifier.weight(1f))
            StatCard(Icons.Filled.Thermostat, "Suhu", s.temp?.let { "${it.roundToInt()}°C" }, null, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ================= 2. DATA =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPage(vm: VM) {
    val d = vm.dataDate
    val ym = YearMonth.from(d)
    var picker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshData() }
    val vals = remember(ym, vm.snapVer) { vm.dayValues(ym, vm.snapVer) }
    val clients = vm.s.clients
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().coerceAtMost()) }
                    picker = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Batal") } }
        ) { DatePicker(state) }
    }
    Page {
        SectionTitle(Icons.Filled.BarChart, "Data Penggunaan") {
            if (vm.usageLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
            else IconButton(onClick = { vm.refreshData() }) { Icon(Icons.Filled.Refresh, "Muat semula", tint = Cyan) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Harian", !vm.dataMonthly, Modifier.weight(1f)) { vm.dataMonthly = false }
            Chip("Bulanan", vm.dataMonthly, Modifier.weight(1f)) { vm.dataMonthly = true }
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.setDate(if (vm.dataMonthly) d.minusMonths(1) else d.minusDays(1)) }) {
                    Icon(Icons.Filled.ChevronLeft, "Sebelum", tint = Cyan)
                }
                Text(
                    if (vm.dataMonthly) "${BULAN[d.monthValue - 1]} ${d.year}" else "${d.dayOfMonth} ${BULAN[d.monthValue - 1]} ${d.year}",
                    color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable { picker = true }
                )
                IconButton(onClick = { picker = true }) { Icon(Icons.Filled.CalendarMonth, "Pilih tarikh", tint = Cyan) }
                IconButton(
                    enabled = if (vm.dataMonthly) ym < YearMonth.now() else d < LocalDate.now(),
                    onClick = { vm.setDate(if (vm.dataMonthly) d.plusMonths(1) else d.plusDays(1)) }
                ) { Icon(Icons.Filled.ChevronRight, "Seterusnya", tint = Cyan) }
            }
        }
        vm.usageErr?.let { Banner(it, Amber) }
        NeonCard(Modifier.fillMaxWidth(), accent = Purple) {
            Text("Jumlah Penggunaan", color = Dim, fontSize = 13.sp)
            if (vm.dataMonthly) {
                val u = vm.usage
                if (u == null) Text(NA, color = Dim, fontSize = 20.sp)
                else {
                    val dl = u.values.sumOf { it.first }; val ul = u.values.sumOf { it.second }
                    Text(if (u.isEmpty()) "Tiada rekod" else Parsers.fmtBytes(dl + ul), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    if (u.isNotEmpty()) Text("↓ ${Parsers.fmtBytes(dl)}   ↑ ${Parsers.fmtBytes(ul)}", color = Cyan, fontSize = 13.sp)
                }
            } else {
                val x = vm.dayUsage(d)
                Text(x?.let { Parsers.fmtBytes(it) } ?: "Tiada rekod", color = if (x == null) Dim else Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Text("Dikira daripada bacaan kaunter nlbwmon yang direkod apabila aplikasi dibuka (anggaran). Hari tanpa rekod tidak dipaparkan.",
                    color = Dim, fontSize = 11.sp)
            }
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            Text("Penggunaan Harian — ${BULAN[ym.monthValue - 1]}", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                BarChart(vals, if (ym == YearMonth.from(d)) d.dayOfMonth - 1 else null, Modifier.fillMaxSize())
                if (vals.all { it == null }) Text("Belum ada rekod harian bulan ini", color = Dim, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", color = Dim, fontSize = 10.sp); Text("${ym.lengthOfMonth() / 2}", color = Dim, fontSize = 10.sp)
                Text("${ym.lengthOfMonth()}", color = Dim, fontSize = 10.sp)
            }
        }
        Text("Penggunaan Setiap Peranti", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        val u = vm.usage
        if (!vm.dataMonthly) Banner("Penggunaan setiap peranti hanya tersedia secara bulanan (nlbwmon menyimpan jumlah mengikut tempoh).", Cyan)
        else if (u != null && u.isNotEmpty()) {
            val rows = u.entries.sortedByDescending { it.value.first + it.value.second }
            val mx = (rows.firstOrNull()?.value?.let { it.first + it.second } ?: 1L).coerceAtLeast(1L)
            rows.forEach { e ->
                val c = clients?.firstOrNull { it.mac == e.key }
                val tot = e.value.first + e.value.second
                NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c?.name ?: "Peranti tidak dikenali", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("${c?.ip ?: "—"} • ${e.key}", color = Dim, fontSize = 11.sp)
                        }
                        Text(Parsers.fmtBytes(tot), color = Cyan, fontWeight = FontWeight.Bold)
                    }
                    Text("↓ ${Parsers.fmtBytes(e.value.first)}   ↑ ${Parsers.fmtBytes(e.value.second)}", color = Dim, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { tot.toFloat() / mx }, Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                        color = Purple, trackColor = Color(0x22FFFFFF))
                }
            }
        } else if (u != null) Text("Tiada rekod peranti untuk bulan ini.", color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
    }
}

private fun LocalDate.coerceAtMost(): LocalDate = if (isAfter(LocalDate.now())) LocalDate.now() else this

// ================= 3. SPEEDTEST =================
@Composable
fun SpeedPage(vm: VM) {
    val st = vm.st
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ms", "MY")) }
    Page {
        SectionTitle(Icons.Filled.Speed, "Speedtest")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Dari Telefon", vm.stMode == 0, Modifier.weight(1f)) { if (!st.running) vm.stMode = 0 }
            Chip("Pada Router", vm.stMode == 1, Modifier.weight(1f)) { if (!st.running) vm.stMode = 1 }
        }
        Text(
            if (vm.stMode == 0) "Mengukur laluan telefon → router → Internet (pelayan Cloudflare). Ini BUKAN ujian pada router."
            else "Router menjalankan ujian sendiri (ping dan muat turun melalui wget). Muat naik tidak disokong pada mod ini.",
            color = Dim, fontSize = 12.sp
        )
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            val live = if (st.running) st.live else (st.dl?.toFloat() ?: 0f)
            SpeedGauge(live, if (st.running && st.mode == 1) "…" else String.format(Locale.US, "%.1f", live), Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally))
            Text(if (st.running) "${st.phase}…" else if (st.done) "Selesai" else "Sedia", color = Cyan, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonCard(Modifier.weight(1f), accent = Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ArrowDownward, null, tint = Cyan); Spacer(Modifier.width(6.dp))
                    Column { Text("Muat Turun", color = Cyan, fontSize = 12.sp); Text(Parsers.f1(st.dl), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Mbps", color = Dim, fontSize = 11.sp) }
                }
            }
            NeonCard(Modifier.weight(1f), accent = Purple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ArrowUpward, null, tint = Magenta); Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Muat Naik", color = Magenta, fontSize = 12.sp)
                        Text(if (st.mode == 1) "N/A" else Parsers.f1(st.ul), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Mbps", color = Dim, fontSize = 11.sp)
                    }
                }
            }
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            Row(Modifier.fillMaxWidth()) {
                Cell("Ping", st.ping?.let { "${it.roundToInt()} ms" } ?: "—", Modifier.weight(1f))
                Cell("Jitter", st.jitter?.let { String.format(Locale.US, "%.1f ms", it) } ?: "—", Modifier.weight(1f))
                Cell("Paket Hilang", st.loss?.let { "${it.roundToInt()}%" } ?: "—", Modifier.weight(1f))
                Cell("Pelayan", "Cloudflare", Modifier.weight(1.2f))
            }
        }
        st.error?.let { Banner(it, if (st.done) Amber else Red) }
        if (st.running) GradButton("HENTI", color = listOf(Red, Magenta)) { vm.stopSpeed() }
        else GradButton("MULA SPEEDTEST", enabled = vm.stMode == 0 || vm.s.online != false) { vm.startSpeed() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sejarah Speedtest", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (vm.history.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text("Kosongkan", color = Cyan) }
        }
        if (vm.history.isEmpty()) Text("Belum ada ujian. Hanya keputusan sebenar disimpan di sini.", color = Dim, fontSize = 12.sp)
        vm.history.forEach { h ->
            NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fmt.format(Date(h.time)), color = Color.White, fontSize = 13.sp)
                        Text(if (h.mode == 0) "Dari telefon" else "Pada router", color = Dim, fontSize = 11.sp)
                    }
                    Text("↓ ${Parsers.f1(h.dl)}", color = Cyan, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Text("↑ ${h.ul?.let { Parsers.f1(it) } ?: "—"}", color = Magenta, fontWeight = FontWeight.SemiBold)
                }
                if (h.ping != null) Text("Ping ${h.ping.roundToInt()} ms", color = Dim, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ================= 4. PERANTI =================
private fun deviceIcon(n: String): ImageVector {
    val l = n.lowercase()
    return when {
        listOf("laptop", "pc", "desktop", "macbook", "book").any { l.contains(it) } -> Icons.Filled.Laptop
        listOf("tv", "chromecast", "firestick").any { l.contains(it) } -> Icons.Filled.Tv
        listOf("printer", "print", "epson", "canon", "hp").any { l.contains(it) } -> Icons.Filled.Print
        listOf("cctv", "cam", "ipc", "dvr", "nvr").any { l.contains(it) } -> Icons.Filled.Videocam
        listOf("phone", "note", "redmi", "oppo", "vivo", "galaxy", "android", "ipad", "tablet").any { l.contains(it) } -> Icons.Filled.Smartphone
        else -> Icons.Filled.DevicesOther
    }
}

@Composable
fun DevicesPage(vm: VM) {
    val clients = vm.s.clients
    var filter by remember { mutableIntStateOf(0) }
    val wifi = clients?.count { it.link == "Wi-Fi" } ?: 0
    val lan = clients?.count { it.link == "LAN" } ?: 0
    val shown = clients?.filter { filter == 0 || (filter == 1 && it.link == "Wi-Fi") || (filter == 2 && it.link == "LAN") }
    Page {
        SectionTitle(Icons.Filled.Devices, "Peranti Bersambung") {
            IconButton(onClick = { vm.refreshDevices() }) { Icon(Icons.Filled.Refresh, "Muat semula", tint = Cyan) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Semua (${clients?.size ?: 0})", filter == 0) { filter = 0 }
            Chip("Wi-Fi ($wifi)", filter == 1) { filter = 1 }
            Chip("LAN ($lan)", filter == 2) { filter = 2 }
        }
        if (vm.s.blocked == null) Banner("Status sekatan tidak dapat dibaca (UCI/firewall). Suis dilumpuhkan.", Amber)
        if (clients == null) Text("Senarai peranti tidak tersedia daripada router.", color = Dim)
        else if (shown != null && shown.isEmpty()) Text("Tiada peranti.", color = Dim)
        shown?.forEach { c ->
            val blocked = vm.s.blocked?.contains(c.mac)
            val busy = vm.busyMacs.contains(c.mac)
            val use = vm.usage?.get(c.mac)
            NeonCard(Modifier.fillMaxWidth(), accent = if (blocked == true) Red else Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(deviceIcon(c.name), null, tint = if (blocked == true) Red else Cyan, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(c.ip, color = Dim, fontSize = 12.sp)
                        Text(c.mac, color = Dim, fontSize = 11.sp)
                        val extra = listOfNotNull(c.link, if (blocked == true) "DISEKAT" else null,
                            use?.let { "Bulan ini ${Parsers.fmtBytes(it.first + it.second)}" }).joinToString(" • ")
                        if (extra.isNotEmpty()) Text(extra, color = if (blocked == true) Red else Cyan, fontSize = 11.sp)
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Cyan, strokeWidth = 2.dp)
                    else Switch(
                        checked = blocked != true, enabled = blocked != null, onCheckedChange = { vm.toggleDevice(c, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF3A4260), uncheckedThumbColor = Color.White)
                    )
                }
            }
        }
        Text("Suis hijau = Internet dibuka. Mematikan suis menambah peraturan firewall pada router dan disahkan selepas dimuat semula.",
            color = Dim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
    }
}

// ================= 5. SISTEM =================
@Composable
fun SystemPage(vm: VM) {
    val s = vm.s
    Page {
        SectionTitle(Icons.Filled.Settings, "Maklumat Sistem")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Icons.Filled.Memory, "CPU", s.cpu?.let { "${it.roundToInt()}%" }, s.cpu, modifier = Modifier.weight(1f))
            StatCard(Icons.Filled.Storage, "RAM", s.ramPct?.let { "${it.roundToInt()}%" }, s.ramPct, s.ramText.ifEmpty { null }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Icons.Filled.Thermostat, "Suhu", s.temp?.let { "${it.roundToInt()}°C" }, null, modifier = Modifier.weight(1f))
            StatCard(Icons.Filled.Storage, "Storan", s.storagePct?.let { "${it.roundToInt()}%" }, s.storagePct, s.storageText.ifEmpty { null }, Modifier.weight(1f))
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            Text("Maklumat Router", color = Cyan, fontWeight = FontWeight.Bold)
            InfoRow("Model", s.board?.model)
            InfoRow("Firmware", s.board?.firmware)
            InfoRow("Nama hos", s.board?.hostname)
            InfoRow("Tempoh hidup", s.uptime?.let { Parsers.fmtUptime(it) })
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Blue) {
            Text("Maklumat Rangkaian", color = Cyan, fontWeight = FontWeight.Bold)
            InfoRow("Status Internet", when (s.online) { true -> "Online"; false -> "Offline"; null -> null },
                if (s.online == true) Green else Red)
            InfoRow("Antara muka WAN", s.wan)
            InfoRow("IP WAN", s.wanIp)
            InfoRow("IP LAN", s.lanIp)
            InfoRow("Ping Internet", s.ping?.let { "${it.roundToInt()} ms" })
        }
        NeonCard(Modifier.fillMaxWidth(), accent = Purple) {
            Text("Maklumat Modem 4G/5G", color = Cyan, fontWeight = FontWeight.Bold)
            val m = s.modem
            if (m != null) {
                InfoRow("Mod rangkaian", m.mode)
                InfoRow("Band", m.band)
                InfoRow("RSRP", m.rsrp?.let { "$it dBm" })
                InfoRow("RSRQ", m.rsrq?.let { "$it dB" })
                InfoRow("SINR", m.sinr?.let { "$it dB" })
            } else {
                Text("Tidak tersedia. Router tidak membalas arahan AT modem (perlu sms_tool dan modem Quectel).", color = Dim, fontSize = 12.sp)
                if (s.raw.isNotBlank()) Text("Diagnostik: ${s.raw.take(200)}", color = Dim, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
