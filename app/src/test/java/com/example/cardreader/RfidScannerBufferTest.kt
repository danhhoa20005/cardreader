package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RfidScannerBufferTest {

    @Test
    fun onKeyChar_decodesRapidUsbHidStreamAndNormalizesCardId() {
        val scanner = RfidScannerBuffer(maxGapMs = 300L, minLength = 4)
        var t = 1000L
        for (ch in "0012345678") {
            assertNull(scanner.onKeyChar(ch, isEnter = false, eventTimeMs = t))
            t += 20L
        }
        val card = scanner.onKeyChar(null, isEnter = true, eventTimeMs = t + 15L)
        assertEquals("0012345678", card)
    }

    @Test
    fun onKeyChar_discardsStalePrefixAfterIdleGapAndSupportsBackspace() {
        val scanner = RfidScannerBuffer(maxGapMs = 250L, minLength = 4)
        scanner.onKeyChar('9', isEnter = false, eventTimeMs = 1000L)
        scanner.onKeyChar('9', isEnter = false, eventTimeMs = 1020L)

        // Gap > 250ms clears old "99"
        scanner.onKeyChar('A', isEnter = false, eventTimeMs = 1500L)
        scanner.onKeyChar('B', isEnter = false, eventTimeMs = 1520L)
        scanner.onKeyChar('X', isEnter = false, eventTimeMs = 1540L)
        scanner.onKeyChar(null, isEnter = false, isBackspace = true, eventTimeMs = 1560L)
        scanner.onKeyChar('C', isEnter = false, eventTimeMs = 1580L)
        scanner.onKeyChar('D', isEnter = false, eventTimeMs = 1600L)

        val result = scanner.onKeyChar(null, isEnter = true, eventTimeMs = 1620L)
        assertEquals("ABCD", result)
    }

    @Test
    fun recordScan_maintainsCountersAndBoundedRingBuffer() {
        val scanner = RfidScannerBuffer(maxHistory = 3)
        scanner.recordScan("CARD01", "USB_HID", 200, true, "Học sinh A")
        scanner.recordScan("CARD02", "LOCALHOST_API", 404, false, "Thẻ chưa đăng ký")
        scanner.recordScan("CARD03", "USB_HID", 200, true, "Học sinh B")
        scanner.recordScan("CARD04", "WEB_CONSOLE", 200, true, "Học sinh C")

        assertEquals(4, scanner.totalScans)
        assertEquals(3, scanner.successCount)
        assertEquals(1, scanner.errorCount)

        val hist = scanner.getHistory()
        assertEquals(3, hist.size)
        assertEquals("CARD04", hist[0].cardId)
        assertEquals("CARD02", hist[2].cardId)

        scanner.clearHistory()
        assertEquals(0, scanner.totalScans)
        assertTrue(scanner.getHistory().isEmpty())
    }

    @Test
    fun resolveApiBaseUrl_mapsBothAttendanceDomainsAccurately() {
        assertEquals(
            "https://api.diemdanhhs.com/api",
            RfidScannerBuffer.resolveApiBaseUrl("http://diemdanhhs.com")
        )
        assertEquals(
            "https://api.dontrahs.com/api",
            RfidScannerBuffer.resolveApiBaseUrl("https://dontrahs.com/check-in-out")
        )
        assertEquals("", RfidScannerBuffer.normalizeCardId("bad<script>card"))
        assertEquals("RFID_2026-01", RfidScannerBuffer.normalizeCardId("  RFID_2026-01 \r\n "))
    }
}
