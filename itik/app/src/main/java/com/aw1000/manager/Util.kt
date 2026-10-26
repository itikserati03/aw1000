package com.aw1000.manager

import java.util.Locale

/** Pemformat nombor dan teks (fungsi tulen, boleh diuji). */
object Fmt {
    fun mbps(bytesPerSec: Double): Double = bytesPerSec * 8.0 / 1_000_000.0
    fun d1(v: Double): String = String.format(Locale.US, "%.1f", v)
    fun speed(v: Double): String = if (v >= 100.0) String.format(Locale.US, "%.0f", v) else d1(v)

    fun bytes(b: Long): String {
        val v = b.toDouble()
        return when {
            v >= 1e12 -> d1(v / 1e12) + " TB"
            v >= 1e9 -> d1(v / 1e9) + " GB"
            v >= 1e6 -> d1(v / 1e6) + " MB"
            v >= 1e3 -> d1(v / 1e3) + " KB"
            else -> "$b B"
        }
    }

    fun uptime(sec: Long): String {
        val d = sec / 86400
        val h = (sec % 86400) / 3600
        val m = (sec % 3600) / 60
        return when {
            d > 0 -> "$d hari $h jam"
            h > 0 -> "${h}j ${m}m"
            else -> "$m min"
        }
    }

    val months = listOf(
        "Januari", "Februari", "Mac", "April", "Mei", "Jun",
        "Julai", "Ogos", "September", "Oktober", "November", "Disember"
    )
}

/** Penghurai dan pengesah input (fungsi tulen, boleh diuji). */
object Parse {
    class Ping(val avg: Double, val jitter: Double, val loss: Double)

    private val lossRe = Regex("""([\d.]+)% packet loss""")
    private val rttRe = Regex("""min/avg/max[^=]*=\s*([\d.]+)/([\d.]+)/([\d.]+)""")

    fun ping(out: String): Ping? {
        val loss = lossRe.find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val m = rttRe.find(out)
        if (m == null) return if (loss >= 100.0) Ping(0.0, 0.0, loss) else null
        val mn = m.groupValues[1].toDoubleOrNull() ?: return null
        val avg = m.groupValues[2].toDoubleOrNull() ?: return null
        val mx = m.groupValues[3].toDoubleOrNull() ?: return null
        return Ping(avg, mx - mn, loss)
    }

    private val macRe = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    fun isMac(s: String): Boolean = macRe.matches(s)

    private val hostRe = Regex("^[A-Za-z0-9][A-Za-z0-9.-]{0,251}(:\\d{1,5})?$")
    fun isHost(s: String): Boolean = hostRe.matches(s)

    /** Skala meter tidak linear: 0,10,50,100,300,500,1000 Mbps. Pulangkan 0..1. */
    private val marks = doubleArrayOf(0.0, 10.0, 50.0, 100.0, 300.0, 500.0, 1000.0)
    fun gaugeFraction(v: Double): Double {
        if (v <= 0.0) return 0.0
        if (v >= marks.last()) return 1.0
        for (i in 0 until marks.size - 1) {
            if (v < marks[i + 1]) return (i + (v - marks[i]) / (marks[i + 1] - marks[i])) / (marks.size - 1)
        }
        return 1.0
    }
}
