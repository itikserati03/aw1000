package com.aw1000.manager

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

data class Live(
    val ticks: Int = 0, val lost: Boolean = false,
    val cpu: Float? = null, val ramPct: Float? = null, val ramText: String = "", val temp: Float? = null,
    val rx: Double? = null, val tx: Double? = null,
    val rxH: List<Float> = emptyList(), val txH: List<Float> = emptyList(),
    val online: Boolean? = null, val wan: String? = null, val wanIp: String? = null, val lanIp: String? = null,
    val uptime: Long? = null,
    val clients: List<Client>? = null, val modem: ModemInfo? = null, val raw: String = "",
    val ping: Double? = null, val pingH: List<Float> = emptyList(),
    val board: Board? = null, val storagePct: Float? = null, val storageText: String = "",
    val blocked: Set<String>? = null
)

data class ST(
    val running: Boolean = false, val mode: Int = 0, val phase: String = "", val live: Float = 0f,
    val dl: Double? = null, val ul: Double? = null, val ping: Double? = null,
    val jitter: Double? = null, val loss: Double? = null, val error: String? = null, val done: Boolean = false
)

data class SpeedRec(
    val time: Long, val mode: Int, val dl: Double?, val ul: Double?,
    val ping: Double?, val jitter: Double?, val loss: Double?
)

class VM(app: Application) : AndroidViewModel(app) {
    private val p = app.getSharedPreferences("itik", 0)

    // ---- Log masuk ----
    var host by mutableStateOf(p.getString("h", "192.168.1.1")!!)
    var user by mutableStateOf(p.getString("u", "root")!!)
    var pass by mutableStateOf("")
    var remember by mutableStateOf(p.getBoolean("r", false))
    var https by mutableStateOf(p.getBoolean("s", false))
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var connected by mutableStateOf(false)
    var s by mutableStateOf(Live())
    var msg by mutableStateOf<String?>(null)
    private var router: Router? = null
    private var job: Job? = null

    init {
        if (remember) p.getString("pe", null)?.let { e -> Vault.decrypt(e)?.let { pass = it } }
    }

    private fun upd(f: (Live) -> Live) { synchronized(this) { s = f(s) } }

    fun connect() {
        if (busy) return
        val h = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (!Parsers.isHost(h)) { error = "Alamat IP/hos router tidak sah."; return }
        if (user.isBlank() || user.length > 64 || user.any { it.isISOControl() }) { error = "Nama pengguna tidak sah."; return }
        if (pass.isEmpty()) { error = "Sila masukkan kata laluan."; return }
        busy = true; error = null
        viewModelScope.launch {
            try {
                val r = Router(h, user.trim(), pass, https)
                withContext(Dispatchers.IO) { r.login() }
                val e = p.edit().putString("h", h).putString("u", user.trim()).putBoolean("r", remember).putBoolean("s", https)
                if (remember) e.putString("pe", Vault.encrypt(pass)) else e.remove("pe")
                e.apply()
                router = r
                s = Live(); connected = true
                usage = null; usageErr = null
                job = viewModelScope.launch(Dispatchers.IO) { loop(r) }
            } catch (e: AuthEx) {
                error = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Tidak dapat sambung ke router. Pastikan telefon berada pada Wi-Fi router dan IP betul."
            }
            busy = false
        }
    }

    fun disconnect() {
        job?.cancel(); stJob?.cancel(); router = null
        connected = false; st = ST()
        if (!remember) pass = ""
    }

    private suspend fun loop(r: Router) = coroutineScope {
        launch {
            var n = 0
            while (isActive) {
                try {
                    val x = r.slow()
                    upd { it.copy(online = x.online, wan = x.wan, wanIp = x.wanIp, lanIp = x.lanIp, clients = x.clients,
                        modem = x.modem, raw = x.raw, blocked = x.blocked) }
                    if (n % 5 == 0) {
                        val b = r.board(); val st = r.storage()
                        upd { it.copy(board = b ?: it.board,
                            storagePct = st?.let { v -> 100f * v.second / v.first },
                            storageText = st?.let { v -> "${v.second / 1024} / ${v.first / 1024} MB" } ?: "") }
                    }
                    n++
                } catch (e: CancellationException) { throw e } catch (e: Exception) { upd { it.copy(lost = true) } }
                delay(6000)
            }
        }
        launch {
            while (isActive) {
                try {
                    val f = r.fast()
                    upd {
                        it.copy(
                            ticks = it.ticks + 1, lost = false,
                            cpu = f.cpu ?: it.cpu, ramPct = f.ramPct, ramText = f.ramText,
                            temp = f.temp, uptime = f.uptime, rx = f.rx, tx = f.tx,
                            rxH = f.rx?.let { v -> (it.rxH + (v / 1e6).toFloat()).takeLast(40) } ?: it.rxH,
                            txH = f.tx?.let { v -> (it.txH + (v / 1e6).toFloat()).takeLast(40) } ?: it.txH
                        )
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { upd { it.copy(lost = true) } }
                delay(1500)
            }
        }
        launch {
            while (isActive) {
                try {
                    val pg = r.pingOnce()
                    upd { it.copy(ping = pg, pingH = if (pg != null) (it.pingH + pg.toFloat()).takeLast(12) else it.pingH) }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                delay(5000)
            }
        }
        launch {
            while (isActive) { refreshDataNow(r); delay(60000) }
        }
    }

    // ---- Sekat / buka peranti ----
    var pendingBlock by mutableStateOf<Client?>(null)
    var busyMacs by mutableStateOf<Set<String>>(emptySet())

    fun toggleDevice(c: Client, allow: Boolean) {
        if (allow) doBlock(c, false) else pendingBlock = c
    }

    fun confirmBlock() { pendingBlock?.let { doBlock(it, true) }; pendingBlock = null }

    private fun doBlock(c: Client, block: Boolean) {
        val r = router ?: return
        if (busyMacs.contains(c.mac)) return
        busyMacs = busyMacs + c.mac
        viewModelScope.launch(Dispatchers.IO) {
            try {
                r.setBlocked(c.mac, c.ip, block)
                msg = if (block) "${c.name} telah disekat daripada Internet (disahkan)." else "${c.name} dibuka semula (disahkan)."
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                msg = "Gagal: ${e.message ?: "ralat tidak diketahui"}"
            }
            try { upd { it.copy(blocked = r.blockedMacs() ?: it.blocked) } } catch (e: Exception) { }
            busyMacs = busyMacs - c.mac
        }
    }

    fun refreshDevices() {
        val r = router ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val x = r.slow()
                upd { it.copy(online = x.online, wan = x.wan, wanIp = x.wanIp, lanIp = x.lanIp, clients = x.clients,
                    modem = x.modem, raw = x.raw, blocked = x.blocked) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { msg = "Gagal memuat semula senarai peranti." }
        }
    }

    // ---- Data penggunaan ----
    var usage by mutableStateOf<Map<String, Pair<Long, Long>>?>(null)
    var usageErr by mutableStateOf<String?>(null)
    var usageLoading by mutableStateOf(false)
    var snapVer by mutableIntStateOf(0)
    var dataMonthly by mutableStateOf(true)
    var dataDate by mutableStateOf(LocalDate.now())

    fun setDate(d: LocalDate) {
        val changed = YearMonth.from(d) != YearMonth.from(dataDate)
        dataDate = d
        if (changed) refreshData()
    }

    fun refreshData() { val r = router ?: return; viewModelScope.launch(Dispatchers.IO) { refreshDataNow(r) } }

    private fun snap(d: LocalDate): Long? = if (p.contains("snap_$d")) p.getLong("snap_$d", 0) else null

    fun dayUsage(d: LocalDate): Long? {
        val a = snap(d); val b = snap(d.minusDays(1))
        return if (a != null && b != null && a >= b) a - b else null
    }

    @Suppress("UNUSED_PARAMETER")
    fun dayValues(ym: YearMonth, ver: Int): List<Long?> = (1..ym.lengthOfMonth()).map { dayUsage(ym.atDay(it)) }

    private suspend fun refreshDataNow(r: Router) {
        usageLoading = true
        try {
            val periods = r.nlbwPeriods()
            if (periods == null || periods.isEmpty()) {
                usage = null
                usageErr = "Sejarah data tidak tersedia. Pasang dan aktifkan nlbwmon pada router (lihat README)."
                return
            }
            val latest = periods.max()
            val cur = r.nlbwUsage(latest)
            if (cur != null) {
                p.edit().putLong("snap_${LocalDate.now()}", cur.values.sumOf { it.first + it.second }).apply()
                snapVer++
            }
            val ym = YearMonth.from(dataDate).toString()
            val sel = periods.filter { it.startsWith(ym) }.maxOrNull()
            usage = when { sel == null -> emptyMap(); sel == latest -> cur; else -> r.nlbwUsage(sel) }
            usageErr = if (usage == null) "Gagal membaca data nlbwmon." else null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            usageErr = "Gagal membaca data penggunaan."
        } finally { usageLoading = false }
    }

    // ---- Speedtest ----
    var stMode by mutableIntStateOf(0) // 0 = dari telefon, 1 = pada router
    var st by mutableStateOf(ST())
    var history by mutableStateOf(loadHistory())
    private var stJob: Job? = null

    private fun loadHistory(): List<SpeedRec> = try {
        val a = JSONArray(p.getString("hist", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            fun d(k: String): Double? = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null
            SpeedRec(o.getLong("t"), o.optInt("m"), d("d"), d("u"), d("p"), d("j"), d("l"))
        }
    } catch (e: Exception) { emptyList() }

    private fun saveHistory(h: List<SpeedRec>) {
        val a = JSONArray()
        h.forEach { r ->
            a.put(JSONObject().put("t", r.time).put("m", r.mode).put("d", r.dl ?: JSONObject.NULL)
                .put("u", r.ul ?: JSONObject.NULL).put("p", r.ping ?: JSONObject.NULL)
                .put("j", r.jitter ?: JSONObject.NULL).put("l", r.loss ?: JSONObject.NULL))
        }
        p.edit().putString("hist", a.toString()).apply()
    }

    fun clearHistory() { history = emptyList(); saveHistory(history) }

    fun stopSpeed() { stJob?.cancel(); st = ST(mode = stMode) }

    fun startSpeed() {
        if (st.running) return
        val mode = stMode
        val r = router
        if (mode == 1 && r == null) return
        st = ST(running = true, mode = mode, phase = "Menyediakan")
        stJob = viewModelScope.launch {
            try {
                var pr: PingResult?; var dl: Double?; var ul: Double? = null
                if (mode == 0) {
                    st = st.copy(phase = "Ping")
                    pr = PhoneSpeed.ping()
                    st = st.copy(ping = pr?.avg, jitter = pr?.jitter, loss = pr?.loss, phase = "Muat turun")
                    dl = PhoneSpeed.download { m -> st = st.copy(live = m.toFloat()) }
                    st = st.copy(dl = dl, live = 0f, phase = "Muat naik")
                    ul = PhoneSpeed.upload { m -> st = st.copy(live = m.toFloat()) }
                } else {
                    st = st.copy(phase = "Ping pada router")
                    pr = withContext(Dispatchers.IO) { r!!.routerPing() }
                    st = st.copy(ping = pr?.avg, jitter = pr?.jitter, loss = pr?.loss, phase = "Muat turun pada router")
                    dl = withContext(Dispatchers.IO) { r!!.routerDownload() }
                }
                if (dl == null && ul == null && pr == null) {
                    st = ST(mode = mode, error = if (mode == 0) "Ujian gagal. Tiada sambungan Internet pada telefon."
                    else "Ujian gagal. Router tidak dapat menjalankan ujian (perlu wget dan akses Internet).")
                } else {
                    st = st.copy(running = false, done = true, live = 0f, dl = dl, ul = ul, phase = "Selesai",
                        error = if (mode == 1) "Ujian muat naik pada router tidak disokong (tiada pelayan sasaran)." else null)
                    history = (listOf(SpeedRec(System.currentTimeMillis(), mode, dl, ul, pr?.avg, pr?.jitter, pr?.loss)) + history).take(20)
                    saveHistory(history)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                st = ST(mode = mode, error = "Ujian gagal: ${e.message ?: "ralat tidak diketahui"}")
            }
        }
    }
}
