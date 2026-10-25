package com.aw1000.manager

import kotlinx.coroutines.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * Ujian kelajuan DARI TELEFON ke pelayan Cloudflare (laluan: telefon -> Wi-Fi/router -> Internet).
 * Ini bukan ujian yang berjalan pada router. Semua nombor diukur sebenar daripada trafik.
 */
object PhoneSpeed {
    private const val DOWN = "https://speed.cloudflare.com/__down?bytes=50000000"
    private const val UP = "https://speed.cloudflare.com/__up"
    private const val PING = "https://speed.cloudflare.com/__down?bytes=0"
    private const val DURATION_MS = 9000L
    private const val STREAMS = 4

    suspend fun ping(): PingResult? = withContext(Dispatchers.IO) {
        val t = ArrayList<Double>(); var fail = 0; val n = 8
        repeat(n) {
            ensureActive()
            val s = System.nanoTime()
            try {
                val c = URL(PING).openConnection() as HttpURLConnection
                c.connectTimeout = 3000; c.readTimeout = 3000; c.useCaches = false
                c.inputStream.use { it.readBytes() }
                t.add((System.nanoTime() - s) / 1e6)
            } catch (e: IOException) { fail++ }
            delay(150)
        }
        val ok = if (t.size > 3) t.drop(1) else t // sampel pertama termasuk jabat tangan TLS
        if (ok.isEmpty()) null
        else PingResult(ok.average(), ok.min(), ok.max(), Parsers.jitter(ok), 100.0 * fail / n)
    }

    suspend fun download(onMbps: (Double) -> Unit): Double? = withContext(Dispatchers.IO) {
        val total = AtomicLong(0)
        val start = System.nanoTime()
        val jobs = (1..STREAMS).map {
            async {
                try {
                    val c = URL(DOWN).openConnection() as HttpURLConnection
                    c.connectTimeout = 4000; c.readTimeout = 4000; c.useCaches = false
                    try {
                        c.inputStream.use { ins ->
                            val buf = ByteArray(65536)
                            while (isActive && (System.nanoTime() - start) / 1_000_000 < DURATION_MS) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                total.addAndGet(n.toLong())
                            }
                        }
                    } finally { c.disconnect() }
                } catch (e: IOException) { }
            }
        }
        var last = 0L; var lastT = start
        while (jobs.any { it.isActive }) {
            delay(250)
            val now = System.nanoTime(); val cur = total.get()
            onMbps((cur - last) * 8 / ((now - lastT) / 1e9) / 1e6)
            last = cur; lastT = now
        }
        jobs.awaitAll()
        val sec = (System.nanoTime() - start) / 1e9
        if (total.get() == 0L) null else total.get() * 8 / sec / 1e6
    }

    suspend fun upload(onMbps: (Double) -> Unit): Double? = withContext(Dispatchers.IO) {
        val total = AtomicLong(0)
        val start = System.nanoTime()
        val jobs = (1..STREAMS).map {
            async {
                try {
                    val c = URL(UP).openConnection() as HttpURLConnection
                    c.connectTimeout = 4000; c.readTimeout = 5000; c.useCaches = false
                    c.requestMethod = "POST"; c.doOutput = true; c.setChunkedStreamingMode(65536)
                    try {
                        c.outputStream.use { os ->
                            val buf = ByteArray(65536)
                            while (isActive && (System.nanoTime() - start) / 1_000_000 < DURATION_MS) {
                                os.write(buf)
                                total.addAndGet(buf.size.toLong())
                            }
                        }
                        try { c.responseCode } catch (e: IOException) { }
                    } finally { c.disconnect() }
                } catch (e: IOException) { }
            }
        }
        var last = 0L; var lastT = start
        while (jobs.any { it.isActive }) {
            delay(250)
            val now = System.nanoTime(); val cur = total.get()
            onMbps((cur - last) * 8 / ((now - lastT) / 1e9) / 1e6)
            last = cur; lastT = now
        }
        jobs.awaitAll()
        val sec = (System.nanoTime() - start) / 1e9
        if (total.get() == 0L) null else total.get() * 8 / sec / 1e6
    }
}
