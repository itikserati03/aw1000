package com.aw1000.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {
    @Test fun mbpsConversion() = assertEquals(8.0, Fmt.mbps(1_000_000.0), 0.0001)

    @Test fun bytesFormat() {
        assertEquals("1.5 GB", Fmt.bytes(1_500_000_000L))
        assertEquals("512 B", Fmt.bytes(512L))
    }

    @Test fun uptimeFormat() {
        assertEquals("3 hari 6 jam", Fmt.uptime(3 * 86400L + 6 * 3600L))
        assertEquals("2j 5m", Fmt.uptime(2 * 3600L + 5 * 60L))
    }

    @Test fun pingParse() {
        val out = "5 packets transmitted, 5 packets received, 0% packet loss\nround-trip min/avg/max = 10.0/12.5/20.0 ms"
        val p = Parse.ping(out)
        assertNotNull(p)
        assertEquals(12.5, p!!.avg, 0.001)
        assertEquals(10.0, p.jitter, 0.001)
        assertEquals(0.0, p.loss, 0.001)
        assertNull(Parse.ping("sampah"))
    }

    @Test fun macValidation() {
        assertTrue(Parse.isMac("AA:BB:CC:11:22:33"))
        assertFalse(Parse.isMac("AA:BB:CC:11:22"))
        assertFalse(Parse.isMac("AA:BB:CC:11:22:33; reboot"))
    }

    @Test fun hostValidation() {
        assertTrue(Parse.isHost("192.168.1.1"))
        assertTrue(Parse.isHost("router.lan:8080"))
        assertFalse(Parse.isHost("1.1.1.1; rm -rf /"))
        assertFalse(Parse.isHost("a b"))
    }

    @Test fun gaugeFraction() {
        assertEquals(0.0, Parse.gaugeFraction(0.0), 1e-9)
        assertEquals(1.0, Parse.gaugeFraction(5000.0), 1e-9)
        assertEquals(0.5, Parse.gaugeFraction(100.0), 1e-9)
    }
}
