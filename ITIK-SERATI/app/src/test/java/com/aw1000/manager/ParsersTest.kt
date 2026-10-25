package com.aw1000.manager

import org.junit.Assert.*
import org.junit.Test

class ParsersTest {
    @Test fun mac() {
        assertTrue(Parsers.isMac("A4:5E:60:1B:2C:3D"))
        assertFalse(Parsers.isMac("A4:5E:60:1B:2C"))
        assertFalse(Parsers.isMac("A4:5E:60:1B:2C:3D; reboot"))
    }
    @Test fun host() {
        assertTrue(Parsers.isHost("192.168.1.1"))
        assertTrue(Parsers.isHost("router.lan:8080"))
        assertFalse(Parsers.isHost("1.1.1.1/../x"))
        assertFalse(Parsers.isHost("a b"))
    }
    @Test fun ipv4() {
        assertTrue(Parsers.isIpv4("10.123.45.67"))
        assertFalse(Parsers.isIpv4("300.1.1.1"))
    }
    @Test fun ping() {
        val o = """
            64 bytes from 1.1.1.1: seq=0 ttl=57 time=10.0 ms
            64 bytes from 1.1.1.1: seq=1 ttl=57 time=14.0 ms
            --- 1.1.1.1 ping statistics ---
            2 packets transmitted, 2 packets received, 0% packet loss
            round-trip min/avg/max = 10.0/12.0/14.0 ms
        """.trimIndent()
        val r = Parsers.parsePing(o)!!
        assertEquals(12.0, r.avg!!, 0.001)
        assertEquals(4.0, r.jitter!!, 0.001)
        assertEquals(0.0, r.loss!!, 0.001)
        assertNull(Parsers.parsePing("ping: bad address"))
    }
    @Test fun df() {
        val o = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/ubi0_1 100000 25000 75000 25% /overlay"
        assertEquals(Pair(100000L, 25000L), Parsers.parseDf(o))
    }
    @Test fun routerSpeed() {
        val mbps = Parsers.parseRouterSpeed("BYTES=12500000 START=10.00 END=12.00")!!
        assertEquals(50.0, mbps, 0.001)
        assertNull(Parsers.parseRouterSpeed("BYTES=0 START=1 END=2"))
    }
    @Test fun format() {
        assertEquals("1.5 GB", Parsers.fmtBytes(1_500_000_000L))
        assertEquals("1 hari 2 jam 3 min", Parsers.fmtUptime(86400 + 7200 + 180))
    }
}
