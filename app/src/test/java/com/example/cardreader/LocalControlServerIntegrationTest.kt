package com.example.cardreader

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kiểm thử tích hợp TCP Socket thực tế cho LocalControlServer:
 * - Xác thực PIN, Cookie, Localhost Bypass
 * - Quẹt thẻ RFID qua POST & GET query (`/api/rfid/scan`)
 * - Chụp màn hình nhị phân JPEG (`/api/web/screenshot`)
 */
class LocalControlServerIntegrationTest {

    private lateinit var server: LocalControlServer
    private var adminPin: String = "1357"
    private var localhostBypass: Boolean = false
    private var lastCommand: String? = null
    private var savedUsername: String = ""
    private var lastRfidCard: String = ""
    private val sampleJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())

    private val delegate = object : ControlServerDelegate {
        override fun getAdminPin(): String = adminPin
        override fun isLocalhostBypassEnabled(): Boolean = localhostBypass

        override fun buildStatusJson(authorized: Boolean): JSONObject =
            JSONObject().apply {
                put("locked", !authorized)
                put("hasAdminPin", adminPin.isNotEmpty())
                if (authorized) {
                    put("username", savedUsername)
                    put("currentScreen", "MAIN")
                }
            }

        override fun handleConfigUpdate(json: JSONObject): JSONObject {
            savedUsername = json.optString("username", "")
            return JSONObject().apply {
                put("ok", true)
                put("message", "Đã lưu cấu hình")
            }
        }

        override fun handleRemoteCommand(action: String): JSONObject {
            lastCommand = action
            return JSONObject().apply {
                put("ok", true)
                put("message", "Đã thực thi $action")
            }
        }

        override fun handleRfidScan(cardId: String, mode: String, source: String): JSONObject {
            lastRfidCard = cardId
            return JSONObject().apply {
                put("ok", true)
                put("cardId", cardId)
                put("httpStatus", 200)
                put("message", "Điểm danh thành công: $cardId")
            }
        }

        override fun captureScreenJpeg(): ByteArray = sampleJpeg
    }

    @Before
    fun setUp() {
        localhostBypass = false
        server = LocalControlServer(port = 0, delegate = delegate, onLog = {})
        server.start()
        val deadline = System.currentTimeMillis() + 3_000L
        while (server.boundPort <= 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25L)
        }
        assertTrue("Máy chủ phải bind thành công cổng nội bộ", server.boundPort > 0)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun fullHttpLifecycle_healthAuthRfidScreenshotAndLocalhostBypassOverSocket() {
        val baseUrl = "http://127.0.0.1:${server.boundPort}"

        // 1. Health endpoint + security headers
        val (healthCode, healthBody, healthHeaders) = sendHttp("$baseUrl/api/health", "GET")
        assertEquals(200, healthCode)
        assertTrue(JSONObject(healthBody).getBoolean("ok"))
        assertEquals("nosniff", healthHeaders["X-Content-Type-Options"])
        assertEquals("DENY", healthHeaders["X-Frame-Options"])

        // 2. Dashboard HTML
        val (htmlCode, htmlBody, _) = sendHttp("$baseUrl/", "GET")
        assertEquals(200, htmlCode)
        assertTrue(htmlBody.contains("Trung Tâm Điều Khiển Localhost & Đầu Đọc RFID"))

        // 3. Protected endpoint rejected when localhostBypass=false and no PIN session
        val (unauthCode, _, _) = sendHttp(
            "$baseUrl/api/command",
            "POST",
            body = "{\"action\":\"stop_countdown\"}"
        )
        assertEquals(401, unauthCode)

        // 4. Enable localhostBypass -> 127.0.0.1 calls RFID scan & binary screenshot with zero friction!
        localhostBypass = true
        val (rfidCode, rfidBody, _) = sendHttp(
            "$baseUrl/api/rfid/scan?cardId=0011223344",
            "GET"
        )
        assertEquals(200, rfidCode)
        assertEquals("0011223344", lastRfidCard)
        assertTrue(JSONObject(rfidBody).getBoolean("ok"))

        val shotBytes = fetchBytes("$baseUrl/api/web/screenshot")
        assertArrayEquals(sampleJpeg, shotBytes)

        // 5. Turn off localhostBypass and verify PIN session cookie flow
        localhostBypass = false
        val (authCode, authBody, authHeaders) = sendHttp(
            "$baseUrl/api/auth",
            "POST",
            body = "{\"pin\":\"1357\"}"
        )
        assertEquals(200, authCode)
        assertTrue(JSONObject(authBody).getBoolean("ok"))
        val cookieHeader = authHeaders["Set-Cookie"].orEmpty().substringBefore(';')
        assertTrue(cookieHeader.startsWith("cr_token="))

        val (cfgCode, _, _) = sendHttp(
            "$baseUrl/api/config",
            "POST",
            headers = mapOf("Cookie" to cookieHeader),
            body = "{\"username\":\"test_operator\"}"
        )
        assertEquals(200, cfgCode)
        assertEquals("test_operator", savedUsername)

        val (logoutCode, _, _) = sendHttp(
            "$baseUrl/api/logout",
            "POST",
            headers = mapOf("Cookie" to cookieHeader),
            body = "{}"
        )
        assertEquals(200, logoutCode)

        val (afterLogoutCode, _, _) = sendHttp(
            "$baseUrl/api/command",
            "POST",
            headers = mapOf("Cookie" to cookieHeader),
            body = "{\"action\":\"connect\"}"
        )
        assertEquals(401, afterLogoutCode)
    }

    private fun fetchBytes(urlStr: String): ByteArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        return try {
            assertEquals(200, conn.responseCode)
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun sendHttp(
        urlStr: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Triple<Int, String, Map<String, String>> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 3_000
            readTimeout = 3_000
            setRequestProperty("Connection", "close")
            for ((k, v) in headers) setRequestProperty(k, v)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray(Charsets.UTF_8)
                setFixedLengthStreamingMode(bytes.size)
                outputStream.use { it.write(bytes) }
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val respHeaders = mutableMapOf<String, String>()
            for ((k, v) in conn.headerFields) {
                if (k != null && !v.isNullOrEmpty()) {
                    respHeaders[k] = v.first()
                }
            }
            Triple(code, text, respHeaders)
        } finally {
            conn.disconnect()
        }
    }
}
