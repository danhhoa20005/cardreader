package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class ServerResolverTest {

    @Test
    fun replaceHostWithIp_preservesPort8888AndPath() {
        assertEquals(
            "http://192.168.1.45:8888",
            ServerResolver.replaceHostWithIp("http://diemdanh.local:8888", "192.168.1.45")
        )
        assertEquals(
            "http://192.168.1.45:8888/api/status",
            ServerResolver.replaceHostWithIp("http://diemdanh.local:8888/api/status", "192.168.1.45")
        )
    }

    @Test
    fun buildAndParseMdnsAResponse_resolvesIpWithPointerCompression() {
        val query = ServerResolver.buildMdnsAQuery("diemdanh.local")
        assertEquals(1, query[5].toInt() and 0xFF)
        assertEquals(8, query[12].toInt() and 0xFF)

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 1, 0, 1, 0, 0, 0, 0))
        out.write(query, 12, query.size - 12)
        out.write(
            byteArrayOf(
                0xC0.toByte(), 0x0C,
                0x00, 0x01,
                0x00, 0x01,
                0x00, 0x00, 0x00, 120,
                0x00, 0x04,
                192.toByte(), 168.toByte(), 1, 88
            )
        )
        val response = out.toByteArray()

        assertEquals(
            "192.168.1.88",
            ServerResolver.parseMdnsAResponse(response, response.size, "diemdanh.local")
        )
        assertNull(
            ServerResolver.parseMdnsAResponse(response, response.size, "other.local")
        )
    }

    @Test
    fun parseMdnsAResponse_ignoresQueryAndTruncatedPackets() {
        val query = ServerResolver.buildMdnsAQuery("diemdanh.local")
        assertNull(ServerResolver.parseMdnsAResponse(query, query.size, "diemdanh.local"))
        assertNull(ServerResolver.parseMdnsAResponse(byteArrayOf(1, 2, 3), 3, "diemdanh.local"))
    }
}
