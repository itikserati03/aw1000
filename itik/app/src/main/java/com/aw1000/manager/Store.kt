package com.aw1000.manager

import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate
import java.util.TreeMap

class Usage(val rx: Long, val tx: Long, val dev: Map<String, Long>) {
    val total: Long get() = rx + tx
}

/** Rekod penggunaan harian yang dikumpul oleh aplikasi daripada kaunter sebenar router. Tiada data direka. */
class DataLog(private val prefs: SharedPreferences) {
    private class Day { var rx = 0L; var tx = 0L; val dev = HashMap<String, Long>() }

    private val days = TreeMap<String, Day>()

    init {
        try {
            val o = JSONObject(prefs.getString("datalog", "{}") ?: "{}")
            for (k in o.keys()) {
                val j = o.getJSONObject(k)
                val d = Day(); d.rx = j.optLong("rx"); d.tx = j.optLong("tx")
                val dv = j.optJSONObject("dev")
                if (dv != null) for (m in dv.keys()) d.dev[m] = dv.optLong(m)
                days[k] = d
            }
        } catch (e: Exception) { }
    }

    @Synchronized fun addTotals(date: String, rx: Long, tx: Long) {
        if (rx <= 0 && tx <= 0) return
        val d = days.getOrPut(date) { Day() }
        d.rx += rx; d.tx += tx
    }

    @Synchronized fun addDevice(date: String, mac: String, bytes: Long) {
        if (bytes <= 0) return
        val d = days.getOrPut(date) { Day() }
        d.dev[mac] = (d.dev[mac] ?: 0L) + bytes
    }

    @Synchronized fun usage(date: LocalDate): Usage {
        val d = days[date.toString()] ?: return Usage(0, 0, emptyMap())
        return Usage(d.rx, d.tx, HashMap(d.dev))
    }

    @Synchronized fun sum(dates: List<LocalDate>): Usage {
        var rx = 0L; var tx = 0L; val dev = HashMap<String, Long>()
        for (dt in dates) {
            val d = days[dt.toString()] ?: continue
            rx += d.rx; tx += d.tx
            for ((m, b) in d.dev) dev[m] = (dev[m] ?: 0L) + b
        }
        return Usage(rx, tx, dev)
    }

    @Synchronized fun save() {
        val cutoff = LocalDate.now().minusDays(400).toString()
        days.keys.removeAll { it < cutoff }
        val o = JSONObject()
        for ((k, d) in days) {
            val dv = JSONObject()
            for ((m, b) in d.dev) dv.put(m, b)
            o.put(k, JSONObject().put("rx", d.rx).put("tx", d.tx).put("dev", dv))
        }
        prefs.edit().putString("datalog", o.toString()).apply()
    }
}
