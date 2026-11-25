package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun normalizeUrl_addsHttpAndStripsTrailingSlash() {
        assertEquals("http://diemdanhhs.com", AppConfig.normalizeUrl(" diemdanhhs.com/ "))
        assertEquals("https://dontrahs.com", AppConfig.normalizeUrl("https://dontrahs.com///"))
        assertEquals("http://192.168.1.50:8888", AppConfig.normalizeUrl("192.168.1.50:8888/"))
        assertEquals("", AppConfig.normalizeUrl("   "))
    }

    @Test
    fun normalizeMdnsHostname_normalizesAndAppendsDotLocal() {
        assertEquals("diemdanh.local", AppConfig.normalizeMdnsHostname("diemdanh"))
        assertEquals("lop1a.local", AppConfig.normalizeMdnsHostname("http://Lop1A.LOCAL:8888/"))
        assertEquals("diemdanh.local", AppConfig.normalizeMdnsHostname("   "))
        assertEquals("diemdanh.local", AppConfig.normalizeMdnsHostname("invalid host!"))
    }

    @Test
    fun normalizeGithubRepo_parsesFullUrlAndOwnerRepo() {
        assertEquals(
            "danhhoa20005/cardreader",
            AppConfig.normalizeGithubRepo("https://github.com/danhhoa20005/cardreader.git")
        )
        assertEquals(
            "danhhoa20005/cardreader",
            AppConfig.normalizeGithubRepo("danhhoa20005/cardreader/")
        )
        assertEquals("", AppConfig.normalizeGithubRepo("not-a-valid-repo"))
    }

    @Test
    fun appSettings_credentialAndPinPredicatesAreAccurate() {
        val empty = AppSettings("", "", "http://diemdanhhs.com", "diemdanh.local", "danhhoa20005/cardreader", "")
        assertFalse(empty.hasCredentials())
        assertFalse(empty.hasAdminPin())

        val complete = AppSettings("test_user", "test_pass", "https://dontrahs.com", "diemdanh.local", "danhhoa20005/cardreader", "1234")
        assertTrue(complete.hasCredentials())
        assertTrue(complete.hasAdminPin())
    }
}
