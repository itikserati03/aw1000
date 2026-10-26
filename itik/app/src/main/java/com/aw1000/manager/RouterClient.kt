package com.aw1000.manager

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class Client(
    val mac: String, val ip: String, val host: String,
    val signal: Int?, val dev: String, val wifi: Boolean
)

class Snap(
    val model: String, val firmware: String, val kernel: String,
    val wanName: String, val wanDev: String, val wanProto: String,
    val wanIp: String?, val lanIp: String?, val wanUp: Boolean,
    val rxBps: Double, val txBps: Double,
    val rxDelta: Long, val txDelta: Long,
    val cpu: Double?, val memTotal: Long, val memUsed: Long,
    val storTotal: Long?, val storUsed: Long?,
    val uptime: Long, val load: Double,
    val temps: List<Pair<String, Double>>, val cpuTemp: Double?, val modemTemp: Double?,
    val clients: List<Client>, val notes: List<String>
)

class ExecRes(val code: Int, val out: String, val err: String)

/** Klien JSON-RPC ubus (uhttpd-mod-ubus + rpcd) untuk OpenWrt. Semua argumen arahan ditetapkan tetap atau disahkan. */
class RouterClient(
    ip: String, private val user: String, private val pass: String,
    useHttps: Boolean, private val trustSelfSigned: Boolean
) {
    private val base = (if (useHttps) "https://" else "http://") + ip.trim()
    private val nullSid = "00000000000000000000000000000000"
    @Volatile private var sid = nullSid
    private val id = AtomicInteger(1)

    private var lastT = 0L
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastIdle = -1L
    private var lastTotal = -1L

    private class Res(val code: Int, val data: JSONObject)

    private fun post(body: String, readMs: Int): JSONObject {
        val conn = URL("$base/ubus").openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection && trustSelfSigned) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), SecureRandom())
            conn.sslSocketFactory = ctx.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = readMs
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 404) throw IOException("Router tiada /ubus (uhttpd-mod-ubus tidak dipasang atau dimatikan)")
            if (code !in 200..299) throw IOException("HTTP $code daripada router")
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun raw(session: String, obj: String, method: String, args: JSONObject, readMs: Int): Res {
        val req = JSONObject()
            .put("jsonrpc", "2.0").put("id", id.getAndIncrement()).put("method", "call")
            .put("params", JSONArray().put(session).put(obj).put(method).put(args))
        val r = post(req.toString(), readMs)
        if (r.has("error")) {
            val e = r.getJSONObject("error")
            throw IOException("Ralat RPC ${e.optInt("code")}: ${e.optString("message")}")
        }
        val arr = r.getJSONArray("result")
        val data = if (arr.length() > 1) arr.optJSONObject(1) ?: JSONObject() else JSONObject()
        return Res(arr.getInt(0), data)
    }

    fun login() {
        val res = raw(
            nullSid, "session", "login",
            JSONObject().put("username", user).put("password", pass).put("timeout", 900), 8000
        )
        if (res.code != 0 || !res.data.has("ubus_rpc_session"))
            throw IOException("Nama pengguna atau kata laluan salah")
        sid = res.data.getString("ubus_rpc_session")
    }

    private fun call(obj: String, method: String, args: JSONObject = JSONObject(), readMs: Int = 8000): JSONObject {
        var r = raw(sid, obj, method, args, readMs)
        if (r.code == 6 && sid != nullSid) { login(); r = raw(sid, obj, method, args, readMs) } // sesi tamat
        if (r.code != 0) throw IOException("ubus $obj.$method gagal (kod ${r.code})")
        return r.data
    }

    private fun readFile(path: String): String =
        call("file", "read", JSONObject().put("path", path)).optString("data")

    fun exec(cmd: String, params: List<String>, readMs: Int = 15000): ExecRes {
        val args = JSONObject().put("command", cmd).put("params", JSONArray(params))
        val d = call("file", "exec", args, readMs)
        return ExecRes(d.optInt("code", -1), d.optString("stdout"), d.optString("stderr"))
    }

    private fun norm(m: String) = m.trim().lowercase()

    private class Net(val name: String, val dev: String, val proto: String, val ip: String?, val up: Boolean)

    // ---------------- snapshot ----------------
    fun snapshot(): Snap {
        val notes = ArrayList<String>()
        val now = System.currentTimeMillis()

        val info = call("system", "info")
        val mem = info.optJSONObject("memory") ?: JSONObject()
        val total = mem.optLong("total")
        val avail = if (mem.has("available")) mem.optLong("available")
        else mem.optLong("free") + mem.optLong("buffered") + mem.optLong("cached")
        val load = info.optJSONArray("load")?.optDouble(0, 0.0)?.div(65536.0) ?: 0.0
        val root = info.optJSONObject("root")
        val storTotal = root?.optLong("total")?.takeIf { it > 0 }
        val storUsed = if (root != null && storTotal != null) root.optLong("used") else null

        var model = "AW1000"; var firmware = "-"; var kernel = "-"
        try {
            val b = call("system", "board")
            model = b.optString("model", "AW1000").ifEmpty { "AW1000" }
            kernel = b.optString("kernel", "-")
            val rel = b.optJSONObject("release")
            firmware = rel?.optString("description")?.takeIf { it.isNotEmpty() }
                ?: ((rel?.optString("distribution") ?: "") + " " + (rel?.optString("version") ?: "")).trim().ifEmpty { "-" }
        } catch (e: Exception) { notes.add("Maklumat papan/firmware tidak dapat dibaca") }

        // CPU % daripada /proc/stat
        var cpu: Double? = null
        try {
            val l = readFile("/proc/stat").lineSequence().first { it.startsWith("cpu ") }
            val p = l.trim().split(Regex("\\s+")).drop(1).take(8).map { it.toLong() }
            val tot = p.sum(); val idle = p[3] + p[4]
            if (lastTotal >= 0 && tot > lastTotal) cpu = 100.0 * (1 - (idle - lastIdle).toDouble() / (tot - lastTotal))
            lastTotal = tot; lastIdle = idle
        } catch (e: Exception) { notes.add("CPU % tidak dapat dibaca") }

        // WAN (laluan lalai) dan LAN
        var wan: Net? = null
        var lanIp: String? = null
        try {
            val arr = call("network.interface", "dump").optJSONArray("interface") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("interface")
                val dev = o.optString("l3_device").ifEmpty { o.optString("device") }
                val ip4 = o.optJSONArray("ipv4-address")?.optJSONObject(0)?.optString("address")?.takeIf { it.isNotEmpty() }
                if (name == "lan" || (lanIp == null && dev.startsWith("br-lan"))) lanIp = ip4 ?: lanIp
                if (wan != null || !o.optBoolean("up") || dev.isEmpty()) continue
                val routes = o.optJSONArray("route") ?: continue
                for (j in 0 until routes.length()) {
                    val rt = routes.getJSONObject(j)
                    if (rt.optString("target") == "0.0.0.0" && rt.optInt("mask") == 0) {
                        wan = Net(name, dev, o.optString("proto", "-"), ip4, true); break
                    }
                }
            }
        } catch (e: Exception) { notes.add("Antara muka rangkaian tidak dapat dibaca") }
        if (wan == null) notes.add("Tiada laluan lalai WAN aktif (WAN mungkin terputus)")
        val w = wan

        var rx = 0L; var tx = 0L
        var rxBps = 0.0; var txBps = 0.0; var dRx = 0L; var dTx = 0L
        if (w != null) {
            try {
                val st = call("network.device", "status", JSONObject().put("name", w.dev)).getJSONObject("statistics")
                rx = st.getLong("rx_bytes"); tx = st.getLong("tx_bytes")
                if (lastRx >= 0 && now > lastT && rx >= lastRx && tx >= lastTx) {
                    val dt = (now - lastT) / 1000.0
                    dRx = rx - lastRx; dTx = tx - lastTx
                    rxBps = dRx / dt; txBps = dTx / dt
                }
                lastRx = rx; lastTx = tx; lastT = now
            } catch (e: Exception) { notes.add("Statistik trafik tidak tersedia untuk ${w.dev}") }
        }

        // Suhu
        val temps = ArrayList<Pair<String, Double>>()
        var cpuT: Double? = null; var modemT: Double? = null
        try {
            val ents = call("file", "list", JSONObject().put("path", "/sys/class/thermal")).optJSONArray("entries") ?: JSONArray()
            for (i in 0 until ents.length()) {
                val n = ents.getJSONObject(i).optString("name")
                if (!n.startsWith("thermal_zone")) continue
                try {
                    val type = readFile("/sys/class/thermal/$n/type").trim().ifEmpty { n }
                    var v = readFile("/sys/class/thermal/$n/temp").trim().toDouble()
                    if (v > 200) v /= 1000.0
                    temps.add(type to v)
                    val t = type.lowercase()
                    if (Regex("modem|mdm|wwan|quec|5g|sdx|rm5").containsMatchIn(t)) { if (modemT == null) modemT = v }
                    else if (cpuT == null) cpuT = v
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        if (temps.isEmpty()) notes.add("Tiada sensor suhu dalam /sys/class/thermal")

        // Peranti bersambung
        val ipByMac = HashMap<String, String>(); val devByMac = HashMap<String, String>()
        val hostByMac = HashMap<String, String>()
        try {
            readFile("/proc/net/arp").lineSequence().drop(1).forEach {
                val f = it.trim().split(Regex("\\s+"))
                if (f.size >= 6 && f[2] != "0x0" && f[5] != (w?.dev ?: "")) {
                    ipByMac[norm(f[3])] = f[0]; devByMac[norm(f[3])] = f[5]
                }
            }
        } catch (e: Exception) { notes.add("Jadual ARP tidak dapat dibaca") }
        try {
            readFile("/tmp/dhcp.leases").lineSequence().forEach {
                val f = it.trim().split(Regex("\\s+"))
                if (f.size >= 4) { if (f[3] != "*") hostByMac[norm(f[1])] = f[3] }
            }
        } catch (e: Exception) { }

        val clients = LinkedHashMap<String, Client>()
        val wifiSeen = HashSet<String>()
        try {
            val devs = call("iwinfo", "devices").optJSONArray("devices") ?: JSONArray()
            for (i in 0 until devs.length()) {
                val d = devs.getString(i)
                val res = call("iwinfo", "assoclist", JSONObject().put("device", d)).optJSONArray("results") ?: continue
                for (j in 0 until res.length()) {
                    val o = res.getJSONObject(j); val m = norm(o.optString("mac"))
                    wifiSeen.add(m)
                    clients[m] = Client(
                        m.uppercase(), ipByMac[m] ?: "-", hostByMac[m] ?: "-",
                        if (o.has("signal")) o.optInt("signal") else null, d, true
                    )
                }
            }
        } catch (e: Exception) {
            notes.add("Modul iwinfo tiada: jenis Wi-Fi/LAN tidak dapat dibezakan")
        }
        for ((m, ip4) in ipByMac) {
            if (m !in clients) clients[m] = Client(m.uppercase(), ip4, hostByMac[m] ?: "-", null, devByMac[m] ?: "LAN", false)
        }

        return Snap(
            model = model, firmware = firmware, kernel = kernel,
            wanName = w?.name ?: "-", wanDev = w?.dev ?: "-", wanProto = w?.proto ?: "-",
            wanIp = w?.ip, lanIp = lanIp, wanUp = w != null,
            rxBps = rxBps, txBps = txBps, rxDelta = dRx, txDelta = dTx,
            cpu = cpu, memTotal = total, memUsed = (total - avail).coerceAtLeast(0),
            storTotal = storTotal, storUsed = storUsed,
            uptime = info.optLong("uptime"), load = load,
            temps = temps, cpuTemp = cpuT, modemTemp = modemT,
            clients = clients.values.toList(), notes = notes
        )
    }

    // ---------------- ping dari router ----------------
    fun ping(): Parse.Ping? {
        val r = exec("/bin/ping", listOf("-c", "5", "-W", "2", "1.1.1.1"), 20000)
        return Parse.ping(r.out + "\n" + r.err)
    }

    // ---------------- sejarah data per peranti (nlbwmon) ----------------
    /** Pulangkan peta MAC -> (muat turun, muat naik) bait bagi tempoh semasa. Lontar IOException jika nlbwmon tiada. */
    fun nlbw(): Map<String, Pair<Long, Long>> {
        val r = exec("/usr/sbin/nlbw", listOf("-c", "json", "-g", "mac"), 15000)
        if (r.code != 0) throw IOException("nlbw keluar dengan kod ${r.code}")
        val o = JSONObject(r.out)
        val cols = o.getJSONArray("columns")
        var iMac = -1; var iRx = -1; var iTx = -1
        for (i in 0 until cols.length()) {
            when (cols.getString(i)) {
                "mac" -> iMac = i
                "rx_bytes" -> iRx = i
                "tx_bytes" -> iTx = i
            }
        }
        if (iMac < 0 || iRx < 0 || iTx < 0) throw IOException("Format nlbw tidak dikenali")
        val rows = o.getJSONArray("data")
        val out = HashMap<String, Pair<Long, Long>>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            val mac = row.getString(iMac).uppercase()
            val prev = out[mac] ?: (0L to 0L)
            out[mac] = (prev.first + row.getLong(iRx)) to (prev.second + row.getLong(iTx))
        }
        return out
    }

    // ---------------- sekat / buka peranti (peraturan firewall UCI) ----------------
    private fun sectionName(mac: String) = "itik_" + mac.replace(":", "").lowercase()

    /** MAC (huruf besar) yang disekat oleh aplikasi ini. */
    fun blockedMacs(): Set<String> {
        val vals = call("uci", "get", JSONObject().put("config", "firewall")).optJSONObject("values") ?: return emptySet()
        val out = HashSet<String>()
        for (k in vals.keys()) {
            val s = vals.optJSONObject(k) ?: continue
            if (s.optString(".type") == "rule" && s.optString("name").startsWith("ITIK-BLOCK-")) {
                val m = s.optString("src_mac")
                if (Parse.isMac(m)) out.add(m.uppercase())
            }
        }
        return out
    }

    /** Sekat atau buka peranti, kemudian sahkan dengan membaca semula konfigurasi. Lontar IOException jika gagal. */
    fun setBlocked(macIn: String, block: Boolean) {
        if (!Parse.isMac(macIn)) throw IOException("Alamat MAC tidak sah")
        val mac = macIn.uppercase()
        val sec = sectionName(mac)
        val already = mac in blockedMacs()
        if (block && !already) {
            val values = JSONObject().put("name", "ITIK-BLOCK-$mac").put("src", "lan").put("dest", "wan")
                .put("src_mac", mac).put("target", "REJECT")
            call("uci", "add", JSONObject().put("config", "firewall").put("type", "rule").put("name", sec).put("values", values))
        } else if (!block && already) {
            call("uci", "delete", JSONObject().put("config", "firewall").put("section", sec))
        } else if (block == already) {
            return
        }
        call("uci", "commit", JSONObject().put("config", "firewall"))
        val r = exec("/etc/init.d/firewall", listOf("reload"), 25000)
        if (r.code != 0) throw IOException("Firewall gagal dimuat semula (kod ${r.code})")
        val now = mac in blockedMacs()
        if (now != block) throw IOException("Pengesahan gagal: peraturan tidak berubah pada router")
    }

    // ---------------- speedtest pada router (muat turun sahaja) ----------------
    /** Router memuat turun fail Cloudflare sendiri. Pulangkan Mbps. */
    fun routerDownloadMbps(): Double {
        fun run(bytes: Long): Double {
            val t0 = System.nanoTime()
            val r = exec(
                "/usr/bin/wget",
                listOf("-q", "-O", "/dev/null", "https://speed.cloudflare.com/__down?bytes=$bytes"), 45000
            )
            val sec = (System.nanoTime() - t0) / 1e9
            if (r.code != 0) throw IOException("wget pada router gagal (kod ${r.code}). Pastikan wget dengan HTTPS (ca-bundle) dipasang.")
            return bytes * 8.0 / sec / 1e6
        }
        val first = run(10_000_000)
        if (first < 25.0) return first
        val bytes = (first * 1e6).toLong().coerceIn(20_000_000L, 100_000_000L) // ~8 saat
        return run(bytes)
    }
}
