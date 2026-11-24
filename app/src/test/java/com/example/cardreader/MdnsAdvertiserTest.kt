package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MdnsAdvertiserTest {

    @Test
    fun extractMdnsQuestions_parsesMdnsAQuery() {
        val query = ServerResolver.buildMdnsAQuery("diemdanh.local")
        val questions = MdnsAdvertiser.extractMdnsQuestions(query, query.size)
        assertEquals(1, questions.size)
        assertEquals("diemdanh.local", questions[0].qname)
        assertEquals(1, questions[0].qtype)
    }

    @Test
    fun extractMdnsQuestions_ignoresResponsePackets() {
        val resp = MdnsAdvertiser.buildMdnsAResponse("diemdanh.local", "192.168.1.45")
        val questions = MdnsAdvertiser.extractMdnsQuestions(resp, resp.size)
        assertTrue(questions.isEmpty())
    }

    @Test
    fun buildMdnsAResponse_roundTripsWithServerResolverParser() {
        val resp = MdnsAdvertiser.buildMdnsAResponse("diemdanh.local", "192.168.1.99", ttlSeconds = 120)
        val parsedIp = ServerResolver.parseMdnsAResponse(resp, resp.size, "diemdanh.local")
        assertEquals("192.168.1.99", parsedIp)
    }
}
