package com.aw1000.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate

enum class Tab(val label: String) {
    UTAMA("Utama"), DATA("Data"), SPEED("Speedtest"), PERANTI("Peranti"), SISTEM("Sistem")
}

class PingRes(val avg: Double, val jitter: Double, val loss: Double)

class SpeedRec(
    val ts: Long, val mode: String, val down: Double, val up: Double?,
    val ping: Double?, val jitter: Double?, val loss: Double?
)

class AppModel(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("aw", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())
    val dataLog = DataLog(prefs)

    var loggedIn by mutableStateOf(false)
    var tab by mutableStateOf(Tab.UTAMA)

    // ---- log masuk ----
    var loginBusy by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    val savedIp: String = prefs.getString("ip", "192.168.1.1") ?: "192.168.1.1"
    val savedUser: String = prefs.getString("user", "root") ?: "root"
    val savedPw: String = prefs.getString("pw", null)?.let { Secure.dec(it) } ?: ""
    val savedRemember: Boolean = prefs.getBoolean("remember", prefs.contains("pw"))
    val savedHttps: Boolean = prefs.getBoolean("https", false)
    val savedSelf: Boolean = prefs.getBoolean("self", false)

    // ---- data langsung ----
    var snap by mutableStateOf<Snap?>(null)
    var connError by mutableStateOf<String?>(null)
    var downHist by mutableStateOf<List<Float>>(emptyList())
    var upHist by mutableStateOf<List<Float>>(emptyList())
    var internet by mutableStateOf<Boolean?>(null)
    var ping by mutableStateOf<PingRes?>(null)
    var pingNote by mutableStateOf<String?>(null)
    var nlbw by mutableStateOf<Map<String, Pair<Long, Long>>?>(null)
    var nlbwMsg by mutableStateOf<String?>(null)
    var blocked by mutableStateOf<Set<String>>(emptySet())
    var busyMac by mutableStateOf<String?>(null)
    var logVersion by mutableStateOf(0)
    var refreshing by mutableStateOf(false)

    // ---- speedtest ----
    var speedMode by mutableStateOf(0) // 0 = telefon, 1 = router
    var speedRunning by mutableStateOf(false)
    var speedPhase by mutableStateOf("")
    var liveMbps by mutableStateOf(0.0)
    var resDown by mutableStateOf<Double?>(null)
    var resUp by mutableStateOf<Double?>(null)
    var resPing by mutableStateOf<Double?>(null)
    var resJitter by mutableStateOf<Double?>(null)
    var resLoss by mutableStateOf<Double?>(null)
    var speedError by mutableStateOf<String?>(null)
    var speedHistory by mutableStateOf<List<SpeedRec>>(loadHistory())
    private var tester: SpeedTester? = null
    @Volatile private var speedToken = 0

    private var client: RouterClient? = null
    @Volatile private var generation = 0
    @Volatile private var running = false
    private var lastNlbw: Map<String, Pair<Long, Long>> = loadLastNlbw()

    private fun toast(t: String) { ui.post { Toast.makeText(ctx, t, Toast.LENGTH_LONG).show() } }
    private fun today(): String = LocalDate.now().toString()

    fun friendly(e: Exception): String = when (e) {
        is ConnectException, is NoRouteToHostException, is UnknownHostException, is SocketTimeoutException ->
            "Tidak dapat capai router. Pastikan telefon bersambung ke Wi-Fi router dan IP betul."
        is javax.net.ssl.SSLException -> "Ralat HTTPS: ${e.message}. Cuba terima sijil swatandatang atau guna HTTP."
        else -> e.message ?: e.toString()
    }

    // ---------------- log masuk / keluar ----------------
    fun login(ip: String, user: String, pw: String, remember: Boolean, https: Boolean, self: Boolean) {
        val i = ip.trim(); val u = user.trim()
        if (i.isEmpty() || u.isEmpty() || pw.isEmpty()) { loginError = "Sila isi semua ruang."; return }
        if (!Parse.isHost(i)) { loginError = "Alamat IP/hos router tidak sah."; return }
        loginBusy = true; loginError = null
        Thread {
            val c = RouterClient(i, u, pw, https, self)
            try {
                c.login()
                val s = c.snapshot()
                val e = prefs.edit().putString("ip", i).putString("user", u)
                    .putBoolean("https", https).putBoolean("self", self).putBoolean("remember", remember)
                if (remember) e.putString("pw", Secure.enc(pw)) else e.remove("pw")
                e.apply()
                ui.post {
                    client = c; snap = s; loggedIn = true; loginBusy = false; connError = null
                    downHist = emptyList(); upHist = emptyList(); tab = Tab.UTAMA
                    startPolling()
                }
            } catch (ex: Exception) {
                ui.post { loginBusy = false; loginError = friendly(ex) }
            }
        }.start()
    }

    fun logout() {
        stopPolling(); speedToken++; tester?.cancel(); speedRunning = false
        dataLog.save()
        client = null; loggedIn = false; snap = null; internet = null; ping = null
        nlbw = null; blocked = emptySet()
    }

    // ---------------- polling ----------------
    fun startPolling() {
        val c = client ?: return
        if (running) return
        running = true
        val gen = ++generation
        Thread {
            var n = 0
            while (running && gen == generation) {
                pollOnce(c, n)
                n++
                try { Thread.sleep(2000) } catch (e: InterruptedException) { }
            }
        }.also { it.isDaemon = true }.start()
    }

    fun stopPolling() { running = false; generation++; dataLog.save() }

    fun refreshNow() {
        val c = client ?: return
        refreshing = true
        Thread {
            pollOnce(c, 0)
            ui.post { refreshing = false }
        }.start()
    }

    private fun pollOnce(c: RouterClient, n: Int) {
        try {
            val s = c.snapshot()
            downHist = (downHist + Fmt.mbps(s.rxBps).toFloat()).takeLast(60)
            upHist = (upHist + Fmt.mbps(s.txBps).toFloat()).takeLast(60)
            if (s.rxDelta > 0 || s.txDelta > 0) { dataLog.addTotals(today(), s.rxDelta, s.txDelta); logVersion++ }
            snap = s; connError = null
            if (n % 15 == 14) dataLog.save()
        } catch (e: Exception) { connError = friendly(e) }
        if (n % 5 == 0) pingStep(c)
        if (n % 15 == 0) slowStep(c)
    }

    private fun pingStep(c: RouterClient) {
        try {
            val p = c.ping()
            if (p != null) {
                ping = PingRes(p.avg, p.jitter, p.loss); internet = p.loss < 100.0; pingNote = null
            } else {
                internet = snap?.wanUp; pingNote = "Keluaran ping router tidak dapat dibaca"
            }
        } catch (e: Exception) {
            internet = snap?.wanUp; pingNote = "Ping dari router tidak tersedia (${e.message ?: "ralat"})"
        }
    }

    private fun slowStep(c: RouterClient) {
        try {
            val m = c.nlbw()
            nlbw = m; nlbwMsg = null
            recordNlbw(m)
        } catch (e: Exception) {
            nlbw = null
            nlbwMsg = "nlbwmon tidak tersedia pada router (pasang pakej nlbwmon). Butiran: ${e.message ?: "ralat"}"
        }
        try { blocked = c.blockedMacs() } catch (e: Exception) { }
    }

    /** Tambah perbezaan kaunter nlbwmon sejak bacaan terakhir kepada log hari ini. Bacaan pertama hanya menjadi garis dasar. */
    private fun recordNlbw(cur: Map<String, Pair<Long, Long>>) {
        val d = today()
        if (lastNlbw.isNotEmpty()) {
            for ((mac, v) in cur) {
                val p = lastNlbw[mac]
                val dr = if (p == null) v.first else if (v.first >= p.first) v.first - p.first else v.first
                val dt = if (p == null) v.second else if (v.second >= p.second) v.second - p.second else v.second
                dataLog.addDevice(d, mac, dr + dt)
            }
            logVersion++
        }
        lastNlbw = cur
        val o = JSONObject()
        for ((m, v) in cur) o.put(m, JSONArray().put(v.first).put(v.second))
        prefs.edit().putString("nlbwlast", o.toString()).apply()
    }

    private fun loadLastNlbw(): Map<String, Pair<Long, Long>> = try {
        val o = JSONObject(prefs.getString("nlbwlast", "{}") ?: "{}")
        val m = HashMap<String, Pair<Long, Long>>()
        for (k in o.keys()) { val a = o.getJSONArray(k); m[k] = a.getLong(0) to a.getLong(1) }
        m
    } catch (e: Exception) { emptyMap() }

    // ---------------- sekat / buka peranti ----------------
    fun setBlocked(mac: String, block: Boolean) {
        val c = client ?: return
        if (!Parse.isMac(mac) || busyMac != null) return
        busyMac = mac
        Thread {
            var err: String? = null
            try { c.setBlocked(mac, block) } catch (e: Exception) { err = friendly(e) }
            try { blocked = c.blockedMacs() } catch (e: Exception) { }
            ui.post { busyMac = null }
            toast(
                if (err == null) (if (block) "Internet disekat untuk $mac (disahkan pada router)"
                else "Internet dibuka semula untuk $mac (disahkan pada router)")
                else "Gagal: $err"
            )
        }.start()
    }

    // ---------------- speedtest ----------------
    private fun loadHistory(): List<SpeedRec> = try {
        val a = JSONArray(prefs.getString("speedhist", "[]") ?: "[]")
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            SpeedRec(
                o.getLong("ts"), o.optString("mode"), o.getDouble("down"),
                if (o.has("up")) o.getDouble("up") else null,
                if (o.has("ping")) o.getDouble("ping") else null,
                if (o.has("jitter")) o.getDouble("jitter") else null,
                if (o.has("loss")) o.getDouble("loss") else null
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun saveHistory(list: List<SpeedRec>) {
        val a = JSONArray()
        for (r in list) {
            val o = JSONObject().put("ts", r.ts).put("mode", r.mode).put("down", r.down)
            r.up?.let { o.put("up", it) }; r.ping?.let { o.put("ping", it) }
            r.jitter?.let { o.put("jitter", it) }; r.loss?.let { o.put("loss", it) }
            a.put(o)
        }
        prefs.edit().putString("speedhist", a.toString()).apply()
    }

    fun stopSpeed() { speedToken++; tester?.cancel(); speedRunning = false; speedPhase = "" }

    fun startSpeed() {
        if (speedRunning) return
        val token = ++speedToken
        val mode = speedMode
        speedRunning = true; speedError = null; liveMbps = 0.0
        resDown = null; resUp = null; resPing = null; resJitter = null; resLoss = null
        Thread {
            fun alive() = token == speedToken
            try {
                if (mode == 0) {
                    val t = SpeedTester(); tester = t
                    speedPhase = "Mengukur ping…"
                    val lat = t.latency()
                    resPing = lat.ping; resJitter = lat.jitter; resLoss = lat.loss
                    if (!alive()) return@Thread
                    speedPhase = "Muat turun…"
                    val d = t.download { if (alive()) liveMbps = it }
                    resDown = d; liveMbps = d
                    if (!alive()) return@Thread
                    speedPhase = "Muat naik…"
                    val u = t.upload { if (alive()) liveMbps = it }
                    resUp = u; liveMbps = u
                    if (!alive()) return@Thread
                    finishSpeed("Telefon", d, u, lat.ping, lat.jitter, lat.loss)
                } else {
                    val c = client ?: throw java.io.IOException("Tiada sambungan router")
                    speedPhase = "Ping dari router…"
                    try {
                        val p = c.ping()
                        if (p != null) { resPing = p.avg; resJitter = p.jitter; resLoss = p.loss }
                    } catch (e: Exception) { }
                    if (!alive()) return@Thread
                    speedPhase = "Router memuat turun…"
                    val d = c.routerDownloadMbps()
                    if (!alive()) return@Thread
                    resDown = d; liveMbps = d
                    finishSpeed("Router", d, null, resPing, resJitter, resLoss)
                }
            } catch (e: Exception) {
                if (alive()) ui.post { speedError = friendly(e); speedRunning = false; speedPhase = "" }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun finishSpeed(mode: String, d: Double, u: Double?, p: Double?, j: Double?, l: Double?) {
        val rec = SpeedRec(System.currentTimeMillis(), mode, d, u, p, j, l)
        val list = (listOf(rec) + speedHistory).take(50)
        saveHistory(list)
        ui.post { speedHistory = list; speedRunning = false; speedPhase = "Selesai" }
    }
}
