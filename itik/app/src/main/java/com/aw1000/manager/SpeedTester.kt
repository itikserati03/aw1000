package com.aw1000.manager

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/** Ujian kelajuan sebenar dari telefon ke pelayan Cloudflare (melalui router jika telefon pada Wi-Fi router). */
class SpeedTester {
    @Volatile var cancelled = false
    fun cancel() { cancelled = true }

    class Lat(val ping: Double, val jitter: Double, val loss: Double)

    private val down = "https://speed.cloudflare.com/__down?bytes=50000000"
    private val up = "https://speed.cloudflare.com/__up"

    private fun nap(ms: Long) { try { Thread.sleep(ms) } catch (e: InterruptedException) { } }

    fun latency(): Lat {
        val rtts = ArrayList<Double>()
        var fail = 0
        val n = 10
        for (i in 0 until n) {
            if (cancelled) break
            val t0 = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 2000) }
                rtts.add((System.nanoTime() - t0) / 1e6)
            } catch (e: Exception) { fail++ }
            nap(120)
        }
        if (rtts.isEmpty()) throw IOException("Tiada sambungan Internet (ping gagal)")
        var jit = 0.0
        for (i in 1 until rtts.size) jit += Math.abs(rtts[i] - rtts[i - 1])
        jit = if (rtts.size > 1) jit / (rtts.size - 1) else 0.0
        return Lat(rtts.average(), jit, 100.0 * fail / n)
    }

    private fun measure(total: AtomicLong, startNs: Long, endNs: Long, onSpeed: (Double) -> Unit): Double {
        var warmB = -1L; var warmT = 0.0; var last = 0.0
        while (!cancelled && System.nanoTime() < endNs) {
            nap(200)
            val t = (System.nanoTime() - startNs) / 1e9
            val b = total.get()
            if (warmB < 0 && t >= 1.0) { warmB = b; warmT = t }
            last = if (warmB >= 0 && t - warmT > 0.3) (b - warmB) * 8.0 / (t - warmT) / 1e6
            else b * 8.0 / t / 1e6
            onSpeed(last)
        }
        return last
    }

    fun download(seconds: Int = 8, threads: Int = 4, onSpeed: (Double) -> Unit): Double {
        val total = AtomicLong(0)
        val start = System.nanoTime(); val end = start + seconds * 1_000_000_000L
        val ws = (0 until threads).map {
            Thread {
                val buf = ByteArray(65536)
                while (!cancelled && System.nanoTime() < end) {
                    var c: HttpURLConnection? = null
                    try {
                        c = URL(down).openConnection() as HttpURLConnection
                        c.connectTimeout = 4000; c.readTimeout = 4000
                        c.inputStream.use { ins ->
                            while (!cancelled && System.nanoTime() < end) {
                                val r = ins.read(buf)
                                if (r < 0) break
                                total.addAndGet(r.toLong())
                            }
                        }
                    } catch (e: Exception) { nap(200) } finally { c?.disconnect() }
                }
            }
        }
        ws.forEach { it.isDaemon = true; it.start() }
        val v = measure(total, start, end, onSpeed)
        ws.forEach { it.join(1500) }
        if (!cancelled && total.get() == 0L) throw IOException("Muat turun gagal: tiada data diterima")
        return v
    }

    fun upload(seconds: Int = 8, threads: Int = 4, onSpeed: (Double) -> Unit): Double {
        val total = AtomicLong(0)
        val start = System.nanoTime(); val end = start + seconds * 1_000_000_000L
        val ws = (0 until threads).map {
            Thread {
                val buf = ByteArray(32768)
                val size = 32768 * 120
                while (!cancelled && System.nanoTime() < end) {
                    var c: HttpURLConnection? = null
                    try {
                        c = URL(up).openConnection() as HttpURLConnection
                        c.requestMethod = "POST"; c.doOutput = true
                        c.connectTimeout = 4000; c.readTimeout = 6000
                        c.setFixedLengthStreamingMode(size)
                        c.outputStream.use { out ->
                            var sent = 0
                            while (sent < size) {
                                if (cancelled || System.nanoTime() >= end) throw IOException("dihentikan")
                                out.write(buf)
                                sent += buf.size
                                total.addAndGet(buf.size.toLong())
                            }
                        }
                        c.responseCode
                    } catch (e: Exception) { if (!cancelled) nap(200) } finally { c?.disconnect() }
                }
            }
        }
        ws.forEach { it.isDaemon = true; it.start() }
        val v = measure(total, start, end, onSpeed)
        ws.forEach { it.join(1500) }
        if (!cancelled && total.get() == 0L) throw IOException("Muat naik gagal: tiada data dihantar")
        return v
    }
}
