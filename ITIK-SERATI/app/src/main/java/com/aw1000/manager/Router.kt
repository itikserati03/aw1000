package com.aw1000.manager

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AuthEx(msg: String) : Exception(msg)
class RpcEx(val code: Int) : Exception("ubus $code")

data class Client(val ip: String, val mac: String, val name: String, val link: String? = null)
data class ModemInfo(val mode: String, val band: String, val rsrp: Int?, val rsrq: Int?, val sinr: Int?)
data class Board(val model: String?, val firmware: String?, val hostname: String?)
data class Fast(
    val cpu: Float?, val ramPct: Float?, val ramText: String, val temp: Float?,
    val rx: Double?, val tx: Double?, val uptime: Long?
)
data class Slow(
    val wan: String?, val online: Boolean, val wanIp: String?, val lanIp: String?,
    val clients: List<Client>?, val modem: ModemInfo?, val raw: String, val blocked: Set<String>?
)

/** Klien ubus JSON-RPC (OpenWrt). Semua data dibaca terus daripada router; tiada nilai rekaan. */
class Router(host: String, private val user: String, private val pass: String, https: Boolean = false) {
    private val base = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    private val scheme = if (https) "https" else "http"
    private var sid = ANON
    @Volatile private var wan: String? = null
    private var pT = 0L; private var pI = 0L
    private var pRx = 0L; private var pTx = 0L; private var pTime = 0L; private var pW: String? = null

    companion object {
        const val ANON = "00000000000000000000000000000000"
        const val PING_TARGET = "1.1.1.1"
        // § digantikan dengan $ (elak templat Kotlin). Skrip tetap: tiada input pengguna.
        private val MODEM_SCRIPT = """
for d in /dev/ttyUSB2 /dev/ttyUSB3 /dev/ttyUSB1 /dev/ttyUSB0 /dev/mhi_DUN /dev/wwan0at0 /dev/ttyACM0 /dev/ttyACM1; do
  [ -c "§d" ] || continue
  o=§(timeout 4 sms_tool -d §d at 'AT+QENG="servingcell"' 2>/dev/null)
  case "§o" in *QENG*) echo "§o"; exit 0;; esac
done
echo DIAG
ls /dev 2>/dev/null | grep -E 'tty(USB|ACM)|mhi|wwan|cdc' | tr '\n' ' '
for t in sms_tool gcom uqmi mmcli; do which §t >/dev/null 2>&1 && echo -n " §t"; done
""".replace('§', '$')
        // Ujian muat turun pada router (HTTP, tanpa TLS pada router). Skrip tetap.
        private val SPEED_SCRIPT = """
rm -f /tmp/itik_st.bin
s=§(cut -d' ' -f1 /proc/uptime)
timeout 14 wget -q -T 8 -O /tmp/itik_st.bin "http://speed.cloudflare.com/__down?bytes=15000000" >/dev/null 2>&1
e=§(cut -d' ' -f1 /proc/uptime)
b=§(wc -c < /tmp/itik_st.bin 2>/dev/null || echo 0)
rm -f /tmp/itik_st.bin
echo "BYTES=§b START=§s END=§e"
""".replace('§', '$')
    }

    private fun post(sess: String, obj: String, method: String, args: JSONObject, readMs: Int = 9000): JSONObject {
        val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "call")
            .put("params", JSONArray().put(sess).put(obj).put(method).put(args))
        val c = URL("$scheme://$base/ubus").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 4000; c.readTimeout = readMs
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val res = JSONObject(txt).optJSONArray("result") ?: throw RpcEx(-1)
            val code = res.optInt(0, -1)
            if (code != 0) throw RpcEx(code)
            return res.optJSONObject(1) ?: JSONObject()
        } finally {
            c.disconnect()
        }
    }

    fun login() {
        try {
            val r = post(ANON, "session", "login",
                JSONObject().put("username", user).put("password", pass).put("timeout", 3600))
            sid = r.optString("ubus_rpc_session")
            if (sid.isEmpty()) throw RpcEx(-1)
        } catch (e: RpcEx) {
            throw AuthEx("Log masuk gagal. Semak nama pengguna dan kata laluan.")
        }
    }

    private fun rpc(obj: String, method: String, args: JSONObject = JSONObject(), readMs: Int = 9000): JSONObject =
        try {
            post(sid, obj, method, args, readMs)
        } catch (e: RpcEx) {
            if (e.code == 6) { login(); post(sid, obj, method, args, readMs) } else throw e
        }

    private fun readFile(path: String): String? = try {
        rpc("file", "read", JSONObject().put("path", path)).optString("data").ifEmpty { null }
    } catch (e: RpcEx) { null }

    /** Jalankan arahan tetap (argumen dihantar sebagai tatasusunan, bukan rentetan shell). */
    private fun execFull(cmd: String, vararg a: String, readMs: Int = 15000): Pair<Int, String>? = try {
        val r = rpc("file", "exec", JSONObject().put("command", cmd).put("params", JSONArray(a.toList())), readMs)
        Pair(r.optInt("code", 0), r.optString("stdout"))
    } catch (e: RpcEx) { null }

    private fun exec(cmd: String, vararg a: String): String? = execFull(cmd, *a)?.second

    /** Data pantas: CPU, RAM, suhu, kelajuan WAN. */
    fun fast(): Fast {
        val t = System.nanoTime()
        var cpu: Float? = null
        readFile("/proc/stat")?.lineSequence()?.firstOrNull { it.startsWith("cpu ") }?.let { l ->
            val v = l.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (v.size >= 5) {
                val tot = v.sum(); val idle = v[3] + v[4]
                if (pT > 0 && tot > pT) cpu = (100f * (1f - (idle - pI).toFloat() / (tot - pT))).coerceIn(0f, 100f)
                pT = tot; pI = idle
            }
        }
        var ramPct: Float? = null; var ramText = ""; var up: Long? = null
        try {
            val i = rpc("system", "info")
            up = i.optLong("uptime")
            val m = i.optJSONObject("memory")
            if (m != null) {
                val total = m.optLong("total")
                val avail = if (m.has("available")) m.optLong("available")
                else m.optLong("free") + m.optLong("buffered") + m.optLong("cached")
                if (total > 0) {
                    val used = total - avail
                    ramPct = 100f * used / total
                    ramText = "${used / 1048576} / ${total / 1048576} MB"
                }
            }
        } catch (e: RpcEx) { }
        var temp: Float? = null
        for (z in 0..5) {
            val v = readFile("/sys/class/thermal/thermal_zone$z/temp")?.trim()?.toFloatOrNull() ?: break
            val c = if (v > 200) v / 1000f else v
            if (c in 10f..130f && c > (temp ?: 0f)) temp = c
        }
        var rx: Double? = null; var tx: Double? = null
        val w = wan
        if (w != null) {
            readFile("/proc/net/dev")?.lineSequence()?.firstOrNull { it.trim().startsWith("$w:") }?.let { l ->
                val f = l.substringAfter(":").trim().split(Regex("\\s+"))
                val r = f.getOrNull(0)?.toLongOrNull(); val x = f.getOrNull(8)?.toLongOrNull()
                if (r != null && x != null) {
                    if (pTime > 0 && pW == w && r >= pRx && x >= pTx) {
                        val dt = (t - pTime) / 1e9
                        rx = (r - pRx) * 8 / dt; tx = (x - pTx) * 8 / dt
                    }
                    pRx = r; pTx = x; pTime = t; pW = w
                }
            }
        }
        return Fast(cpu, ramPct, ramText, temp, rx, tx, up)
    }

    /** Ping tunggal dari router ke Internet (ms), atau null jika gagal. */
    fun pingOnce(): Double? {
        val out = exec("/bin/ping", "-c", "1", "-W", "2", PING_TARGET) ?: return null
        return Parsers.parsePing(out)?.avg
    }

    fun routerPing(count: Int = 5): PingResult? {
        val out = execFull("/bin/ping", "-c", "$count", "-W", "2", PING_TARGET, readMs = 25000)?.second ?: return null
        return Parsers.parsePing(out)
    }

    /** Ujian muat turun sebenar pada router (Mbps) atau null jika router tidak dapat menjalankannya. */
    fun routerDownload(): Double? {
        val out = execFull("/bin/sh", "-c", SPEED_SCRIPT, readMs = 40000)?.second ?: return null
        return Parsers.parseRouterSpeed(out)
    }

    fun board(): Board? = try {
        val j = rpc("system", "board")
        Board(
            j.optString("model").ifEmpty { null },
            j.optJSONObject("release")?.optString("description")?.ifEmpty { null },
            j.optString("hostname").ifEmpty { null }
        )
    } catch (e: RpcEx) { null }

    /** Storan (jumlah KB, digunakan KB). */
    fun storage(): Pair<Long, Long>? =
        exec("/bin/sh", "-c", "df -k /overlay 2>/dev/null || df -k /")?.let { Parsers.parseDf(it) }

    private fun wifiMacs(): Set<String>? {
        return try {
            val devs = rpc("iwinfo", "devices").optJSONArray("devices") ?: return null
            val s = HashSet<String>()
            for (i in 0 until devs.length()) {
                val r = rpc("iwinfo", "assoclist", JSONObject().put("device", devs.getString(i))).optJSONArray("results") ?: continue
                for (k in 0 until r.length()) s.add(r.getJSONObject(k).optString("mac").uppercase())
            }
            s
        } catch (e: RpcEx) { null }
    }

    /** Data perlahan: WAN, peranti, modem, senarai sekatan. */
    fun slow(): Slow {
        var w: String? = null; var online = false
        var wanIp: String? = null; var lanIp: String? = null
        try {
            val d = rpc("network.interface", "dump").optJSONArray("interface")
            if (d != null) for (i in 0 until d.length()) {
                val ifc = d.getJSONObject(i)
                if (!ifc.optBoolean("up")) continue
                val ip = ifc.optJSONArray("ipv4-address")?.optJSONObject(0)?.optString("address")?.ifEmpty { null }
                if (ifc.optString("interface") == "lan" && lanIp == null) lanIp = ip
                if (online) continue
                val rt = ifc.optJSONArray("route") ?: continue
                for (k in 0 until rt.length()) {
                    val r = rt.getJSONObject(k)
                    if (r.optString("target") == "0.0.0.0" && r.optInt("mask") == 0) {
                        w = ifc.optString("l3_device").ifEmpty { ifc.optString("device") }
                        wanIp = ip; online = true; break
                    }
                }
            }
        } catch (e: RpcEx) { }
        wan = w

        val names = HashMap<String, String>()
        val leaseList = ArrayList<Client>()
        val leases = readFile("/tmp/dhcp.leases")
        leases?.lineSequence()?.forEach { l ->
            val p = l.trim().split(Regex("\\s+"))
            if (p.size >= 4) {
                val n = if (p[3] == "*") "Tanpa nama" else p[3]
                names[p[1].lowercase()] = n
                leaseList.add(Client(p[2], p[1].uppercase(), n))
            }
        }
        val wifi = wifiMacs()
        fun linkOf(mac: String): String? = if (wifi == null) null else if (wifi.contains(mac)) "Wi-Fi" else "LAN"
        val arp = readFile("/proc/net/arp")
        val clients: List<Client>? = if (arp != null) {
            arp.lineSequence().drop(1).mapNotNull { l ->
                val p = l.trim().split(Regex("\\s+"))
                if (p.size >= 6 && p[2] == "0x2" && p[3] != "00:00:00:00:00:00" && p[5] != w) {
                    val mac = p[3].uppercase()
                    Client(p[0], mac, names[p[3].lowercase()] ?: "Tanpa nama", linkOf(mac))
                } else null
            }.sortedBy { c -> c.ip.substringAfterLast('.').toIntOrNull() ?: 0 }.toList()
        } else if (leases != null) leaseList.map { it.copy(link = linkOf(it.mac)) } else null

        val raw = exec("/bin/sh", "-c", MODEM_SCRIPT) ?: ""
        return Slow(w, online, wanIp, lanIp, clients, parseModem(raw), raw.trim(), blockedMacs())
    }

    private fun parseModem(o: String): ModemInfo? {
        var nsa: List<String>? = null; var sa: List<String>? = null; var lte: List<String>? = null
        for (line in o.lines()) {
            if (!line.contains("+QENG:")) continue
            var l = line.substringAfter("+QENG:").split(",").map { it.trim().trim('"') }
            if (l.firstOrNull() == "servingcell") l = l.drop(2)
            when (l.firstOrNull()) {
                "NR5G-SA" -> sa = l
                "NR5G-NSA" -> nsa = l
                "LTE" -> lte = l
            }
        }
        fun i(l: List<String>, n: Int) = l.getOrNull(n)?.toIntOrNull()
        return when {
            sa != null -> ModemInfo("5G SA", "n" + sa[8], i(sa, 10), i(sa, 11), i(sa, 12))
            nsa != null -> ModemInfo("5G NSA", (lte?.let { "B" + it.getOrNull(7) + " + " } ?: "") + "n" + nsa[8],
                i(nsa, 4), i(nsa, 6), i(nsa, 5))
            lte != null -> ModemInfo("4G LTE", "B" + lte.getOrNull(7), i(lte, 11), i(lte, 12), i(lte, 14))
            else -> null
        }
    }

    // ---------------- Sekat / buka Internet (peraturan firewall UCI) ----------------

    private fun firewallValues(): JSONObject? = try {
        rpc("uci", "get", JSONObject().put("config", "firewall")).optJSONObject("values")
    } catch (e: RpcEx) { null }

    /** Senarai MAC yang disekat oleh aplikasi ini (peraturan bernama ITIK_BLOCK_*). null = tidak dapat dibaca. */
    fun blockedMacs(): Set<String>? {
        val v = firewallValues() ?: return null
        val out = HashSet<String>()
        for (k in v.keys()) {
            val sec = v.optJSONObject(k) ?: continue
            if (sec.optString("name").startsWith("ITIK_BLOCK_")) {
                val m = sec.optString("src_mac").uppercase()
                if (m.isNotEmpty()) out.add(m)
            }
        }
        return out
    }

    /** Sekat/buka peranti. Melontar IllegalStateException berserta mesej jika gagal atau pengesahan gagal. */
    fun setBlocked(mac: String, ip: String?, block: Boolean) {
        val m = mac.uppercase()
        require(Parsers.isMac(m)) { "Alamat MAC tidak sah." }
        val vals = firewallValues() ?: throw IllegalStateException("Tidak dapat membaca konfigurasi firewall router.")
        var existing: String? = null
        for (k in vals.keys()) {
            val sec = vals.optJSONObject(k) ?: continue
            if (sec.optString("name").startsWith("ITIK_BLOCK_") && sec.optString("src_mac").uppercase() == m) existing = k
        }
        val cfg = JSONObject().put("config", "firewall")
        try {
            if (block && existing == null) {
                val id = m.replace(":", "")
                rpc("uci", "add", JSONObject().put("config", "firewall").put("type", "rule")
                    .put("name", "itik_block_" + id.lowercase())
                    .put("values", JSONObject().put("name", "ITIK_BLOCK_$id").put("src", "lan")
                        .put("src_mac", m).put("dest", "wan").put("proto", "all").put("target", "REJECT")))
                rpc("uci", "commit", cfg)
            } else if (!block && existing != null) {
                rpc("uci", "delete", JSONObject().put("config", "firewall").put("section", existing))
                rpc("uci", "commit", cfg)
            }
        } catch (e: RpcEx) {
            try { rpc("uci", "revert", cfg) } catch (x: RpcEx) { }
            throw IllegalStateException("Router menolak perubahan (kod ${e.code}). Akaun mungkin tiada kebenaran UCI.")
        }
        val rl = execFull("/etc/init.d/firewall", "reload", readMs = 30000)
        if (rl == null || rl.first != 0) throw IllegalStateException("Peraturan disimpan tetapi firewall gagal dimuat semula.")
        if (block && ip != null && Parsers.isIpv4(ip)) execFull("/usr/sbin/conntrack", "-D", "-s", ip) // terbaik-usaha
        val after = blockedMacs() ?: throw IllegalStateException("Pengesahan gagal: konfigurasi tidak dapat dibaca semula.")
        if (after.contains(m) != block) throw IllegalStateException("Pengesahan gagal: peraturan tidak berada dalam keadaan yang dijangka.")
    }

    // ---------------- Sejarah data (nlbwmon) ----------------

    /** Tarikh mula tempoh yang disimpan nlbwmon, atau null jika nlbwmon tiada. */
    fun nlbwPeriods(): List<String>? {
        val r = execFull("/usr/sbin/nlbw", "-c", "list") ?: return null
        if (r.first != 0) return null
        return r.second.lines().map { it.trim() }.filter { Parsers.isPeriod(it) }
    }

    /** MAC -> (muat turun, muat naik) bait bagi tempoh. */
    fun nlbwUsage(period: String?): Map<String, Pair<Long, Long>>? {
        if (period != null && !Parsers.isPeriod(period)) return null
        val args = ArrayList<String>(listOf("-c", "json", "-g", "mac"))
        if (period != null) { args.add("-t"); args.add(period) }
        val r = execFull("/usr/sbin/nlbw", *args.toTypedArray(), readMs = 20000) ?: return null
        if (r.first != 0) return null
        return try {
            val j = JSONObject(r.second)
            val cols = j.getJSONArray("columns"); val data = j.getJSONArray("data")
            val names = (0 until cols.length()).map { cols.getString(it) }
            val iMac = names.indexOf("mac"); val iRx = names.indexOf("rx_bytes"); val iTx = names.indexOf("tx_bytes")
            if (iMac < 0 || iRx < 0 || iTx < 0) return null
            val out = HashMap<String, Pair<Long, Long>>()
            for (i in 0 until data.length()) {
                val row = data.getJSONArray(i)
                val mac = row.getString(iMac).uppercase()
                val old = out[mac] ?: Pair(0L, 0L)
                out[mac] = Pair(old.first + row.optLong(iRx), old.second + row.optLong(iTx))
            }
            out
        } catch (e: Exception) { null }
    }
}
