package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class ServerResolverTest {

    @Test
    fun replaceHostWithIp_preservesPortAndPath() {
        assertEquals(
            "http://192.168.1.45:8080",
            ServerResolver.replaceHostWithIp("http://diemdanh.local:8080", "192.168.1.45")
        )
        assertEquals(
            "http://192.168.1.45:8080/login",
            ServerResolver.replaceHostWithIp("http://diemdanh.local:8080/login", "192.168.1.45")
        )
    }

    @Test
    fun buildAndParseMdnsAResponse_resolvesIpWithPointerCompression() {
        val query = ServerResolver.buildMdnsAQuery("diemdanh.local")
        // Verify QDCOUNT == 1 and QNAME starts with length 8 ("diemdanh")
        assertEquals(1, query[5].toInt() and 0xFF)
        assertEquals(8, query[12].toInt() and 0xFF)

        // Build synthetic mDNS response with 1 question ("diemdanh.local") and 1 A-record answer using pointer 0xC00C
        val out = ByteArrayOutputStream()
        // Header: ID=0, Flags=0x8400 (response+auth), QD=1, AN=1, NS=0, AR=0
        out.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 1, 0, 1, 0, 0, 0, 0))
        // Question section copied from query[12..]
        out.write(query, 12, query.size - 12)
        // Answer: pointer to offset 12 (0xC0, 0x0C), TYPE=1 (A), CLASS=1 (IN), TTL=120, RDLENGTH=4, IP=192.168.1.88
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
    fun parseMdnsAResponse_ignoresQueryPacket() {
        val query = ServerResolver.buildMdnsAQuery("diemdanh.local")
        assertNull(ServerResolver.parseMdnsAResponse(query, query.size, "diemdanh.local"))
    }
}
