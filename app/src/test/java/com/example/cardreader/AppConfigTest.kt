package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun normalizeUrl_addsHttpAndStripsTrailingSlash() {
        assertEquals("http://diemdanhhs.com", AppConfig.normalizeUrl(" diemdanhhs.com/ "))
        assertEquals("https://diemdanhhs.com", AppConfig.normalizeUrl("https://diemdanhhs.com///"))
        assertEquals("", AppConfig.normalizeUrl("   "))
    }

    @Test
    fun normalizeMdnsHostname_normalizesAndAppendsDotLocal() {
        assertEquals("diemdanh.local", AppConfig.normalizeMdnsHostname("diemdanh"))
        assertEquals("lop1a.local", AppConfig.normalizeMdnsHostname("http://Lop1A.LOCAL:8080/"))
        assertEquals("diemdanh.local", AppConfig.normalizeMdnsHostname("   "))
        assertEquals("diemdanh.local", AppConfig.normalizeMdnsHostname("invalid host!"))
    }

    @Test
    fun normalizeGithubRepo_parsesFullGithubUrlAndOwnerRepo() {
        assertEquals("acme/cardreader", AppConfig.normalizeGithubRepo("https://github.com/acme/cardreader.git"))
        assertEquals("acme/cardreader", AppConfig.normalizeGithubRepo("https://github.com/acme/cardreader/releases"))
        assertEquals("acme/cardreader", AppConfig.normalizeGithubRepo(" acme/cardreader/ "))
    }

    @Test
    fun isValidGithubRepo_validatesFormat() {
        assertTrue(AppConfig.isValidGithubRepo("acme-org/CardReader_V2"))
        assertFalse(AppConfig.isValidGithubRepo(""))
        assertFalse(AppConfig.isValidGithubRepo("onlyowner"))
        assertFalse(AppConfig.isValidGithubRepo("a/b/c"))
    }
}
