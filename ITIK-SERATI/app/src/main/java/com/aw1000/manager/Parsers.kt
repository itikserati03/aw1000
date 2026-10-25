package com.aw1000.manager

import java.util.Locale
import kotlin.math.abs

data class PingResult(val avg: Double?, val min: Double?, val max: Double?, val jitter: Double?, val loss: Double?)

/** Fungsi tulen (tanpa Android) supaya boleh diuji unit. */
object Parsers {
    private val MAC = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
    private val HOST = Regex("^[A-Za-z0-9]([A-Za-z0-9.\\-]{0,251}[A-Za-z0-9])?(:[0-9]{1,5})?$")
    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")
    private val PERIOD = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun isMac(s: String) = MAC.matches(s)
    fun isHost(s: String) = HOST.matches(s)
    fun isIpv4(s: String) = IPV4.matches(s)
    fun isPeriod(s: String) = PERIOD.matches(s)

    fun jitter(t: List<Double>): Double? =
        if (t.size >= 2) t.zipWithNext { a, b -> abs(b - a) }.average() else null

    fun parsePing(out: String): PingResult? {
        val times = Regex("time=([0-9.]+)").findAll(out).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val loss = Regex("([0-9.]+)% packet loss").find(out)?.groupValues?.get(1)?.toDoubleOrNull()
        if (times.isEmpty() && loss == null) return null
        val rt = Regex("=\\s*([0-9.]+)/([0-9.]+)/([0-9.]+)").find(out)
        val avg = rt?.groupValues?.get(2)?.toDoubleOrNull() ?: if (times.isNotEmpty()) times.average() else null
        return PingResult(avg, rt?.groupValues?.get(1)?.toDoubleOrNull(), rt?.groupValues?.get(3)?.toDoubleOrNull(), jitter(times), loss)
    }

    /** Hasil `df -k`: pulangkan (jumlah KB, digunakan KB). */
    fun parseDf(out: String): Pair<Long, Long>? {
        for (l in out.lines()) {
            val p = l.trim().split(Regex("\\s+"))
            if (p.size >= 5) {
                val t = p[1].toLongOrNull(); val u = p[2].toLongOrNull()
                if (t != null && u != null && t > 0) return Pair(t, u)
            }
        }
        return null
    }

    /** Hasil skrip ujian pada router: "BYTES=n START=s END=e" -> Mbps. */
    fun parseRouterSpeed(out: String): Double? {
        val b = Regex("BYTES=(\\d+)").find(out)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val s = Regex("START=([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val e = Regex("END=([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val dt = e - s
        return if (b > 0 && dt > 0.05) b * 8.0 / dt / 1e6 else null
    }

    fun fmtBytes(b: Long): String {
        val v = b.toDouble()
        return when {
            v >= 1e12 -> String.format(Locale.US, "%.2f TB", v / 1e12)
            v >= 1e9 -> String.format(Locale.US, "%.1f GB", v / 1e9)
            v >= 1e6 -> String.format(Locale.US, "%.1f MB", v / 1e6)
            v >= 1e3 -> String.format(Locale.US, "%.0f KB", v / 1e3)
            else -> "$b B"
        }
    }

    fun fmtUptime(s: Long): String {
        val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60
        return if (d > 0) "$d hari $h jam $m min" else if (h > 0) "$h jam $m min" else "$m min"
    }

    fun fmtMbps(bps: Double?): String = if (bps == null) "—" else String.format(Locale.US, "%.1f", bps / 1e6)
    fun f1(v: Double?): String = if (v == null) "—" else String.format(Locale.US, "%.1f", v)
}
