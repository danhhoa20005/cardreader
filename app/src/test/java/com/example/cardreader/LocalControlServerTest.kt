package com.example.cardreader

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LocalControlServerTest {

    private class FakeDelegate(
        var pin: String = "",
        var localhostBypass: Boolean = false
    ) : ControlServerDelegate {
        var lastAction: String? = null
        var lastConfig: JSONObject? = null
        var lastScannedCard: String? = null
        var lastScanSource: String? = null
        var lastNavigateRoute: String? = null
        var lastLoginUser: String? = null

        override fun getAdminPin(): String = pin
        override fun isLocalhostBypassEnabled(): Boolean = localhostBypass

        override fun buildStatusJson(authorized: Boolean): JSONObject {
            return JSONObject().apply {
                put("locked", !authorized)
                put("port", 8888)
                put("versionName", "1.0.7")
            }
        }

        override fun handleConfigUpdate(json: JSONObject): JSONObject {
            lastConfig = json
            return JSONObject().apply { put("ok", true) }
        }

        override fun handleRemoteCommand(action: String): JSONObject {
            lastAction = action
            return JSONObject().apply { put("ok", true) }
        }

        override fun handleRfidScan(cardId: String, mode: String, source: String): JSONObject {
            lastScannedCard = cardId
            lastScanSource = source
            return JSONObject().apply {
                put("ok", true)
                put("cardId", cardId)
                put("mode", mode)
                put("source", source)
                put("httpStatus", 200)
                put("message", "Điểm danh thành công")
            }
        }

        override fun handleWebLogin(json: JSONObject): JSONObject {
            lastLoginUser = json.optString("username")
            return JSONObject().put("ok", true).put("route", json.optString("route", "/"))
        }

        override fun handleWebNavigate(json: JSONObject): JSONObject {
            lastNavigateRoute = json.optString("route")
            return JSONObject().put("ok", true).put("route", lastNavigateRoute)
        }

        override fun captureScreenJpeg(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }

    @Test
    fun parseHttpRequest_parsesMethodPathQueryHeadersAndBodyOnPort8888() {
        val raw = "POST /api/command?src=lan&cardId=001234 HTTP/1.1\r\n" +
            "Host: diemdanh.local:8888\r\n" +
            "Content-Length: 20\r\n" +
            "X-Admin-Token: abc\r\n" +
            "\r\n" +
            "{\"action\":\"connect\"}"
        val req = LocalControlServer.parseHttpRequest(ByteArrayInputStream(raw.toByteArray()), "192.168.1.25")
        assertNotNull(req)
        assertEquals("POST", req!!.method)
        assertEquals("/api/command", req.path)
        assertEquals("lan", req.queryParams["src"])
        assertEquals("001234", req.queryParams["cardId"])
        assertEquals("192.168.1.25", req.clientIp)
        assertEquals("abc", req.headers["x-admin-token"])
        assertEquals("{\"action\":\"connect\"}", req.body)
    }

    @Test
    fun parseHttpRequest_flagsOversizedPayloadAndReturns413() {
        val raw = "POST /api/config HTTP/1.1\r\n" +
            "Host: diemdanh.local:8888\r\n" +
            "Content-Length: 70000\r\n" +
            "\r\n"
        val req = LocalControlServer.parseHttpRequest(ByteArrayInputStream(raw.toByteArray()))
        assertNotNull(req)
        assertTrue(req!!.bodyTooLarge)

        val server = LocalControlServer(8888, FakeDelegate(), onLog = {})
        val resp = server.routeRequest(req)
        assertEquals(413, resp.statusCode)
    }

    @Test
    fun routeRequest_servesHealthAndHeadDashboardCleanly() {
        val server = LocalControlServer(8888, FakeDelegate(), onLog = {})

        val health = server.routeRequest(HttpRequestData("GET", "/api/health", emptyMap(), ""))
        assertEquals(200, health.statusCode)
        val healthJson = JSONObject(health.body)
        assertTrue(healthJson.getBoolean("ok"))
        assertEquals(8888, healthJson.getInt("port"))
        assertEquals("/api/rfid/scan", healthJson.getString("rfidEndpoint"))

        val headResp = server.routeRequest(HttpRequestData("HEAD", "/", emptyMap(), ""))
        assertEquals(200, headResp.statusCode)
        assertEquals("", headResp.body)

        val getResp = server.routeRequest(HttpRequestData("GET", "/", emptyMap(), ""))
        assertEquals(200, getResp.statusCode)
        assertTrue(getResp.body.contains("RFID"))
    }

    @Test
    fun routeRequest_allowsLocalhostBypassWhileProtectingLanWithPinAndHeaderPin() {
        val delegate = FakeDelegate(pin = "9999", localhostBypass = true)
        val server = LocalControlServer(8888, delegate, onLog = {})

        // 1. Localhost 127.0.0.1 bypasses PIN for RFID reader & login APIs
        val localScan = server.routeRequest(
            HttpRequestData(
                method = "POST",
                path = "/api/rfid/scan",
                headers = emptyMap(),
                body = "{\"cardId\":\"0098765432\"}",
                clientIp = "127.0.0.1"
            )
        )
        assertEquals(200, localScan.statusCode)
        assertEquals("0098765432", delegate.lastScannedCard)
        assertEquals("LOCALHOST_API", delegate.lastScanSource)

        // 2. External LAN IP without PIN gets 401
        val lanUnauth = server.routeRequest(
            HttpRequestData(
                method = "POST",
                path = "/api/rfid/scan",
                headers = emptyMap(),
                body = "{\"cardId\":\"0098765432\"}",
                clientIp = "192.168.1.88"
            )
        )
        assertEquals(401, lanUnauth.statusCode)

        // 3. External LAN IP with X-Admin-Pin header succeeds in 1 request
        val lanPinHeader = server.routeRequest(
            HttpRequestData(
                method = "GET",
                path = "/api/rfid/scan",
                headers = mapOf("x-admin-pin" to "9999"),
                body = "",
                clientIp = "192.168.1.88",
                queryParams = mapOf("cardId" to "CARD_LAN_01")
            )
        )
        assertEquals(200, lanPinHeader.statusCode)
        assertEquals("CARD_LAN_01", delegate.lastScannedCard)
        assertEquals("LAN_API", delegate.lastScanSource)
    }

    @Test
    fun routeRequest_supportsFullLocalhostControlLoginNavigateAndScreenshot() {
        val delegate = FakeDelegate(pin = "", localhostBypass = true)
        val server = LocalControlServer(8888, delegate, onLog = {})

        val loginResp = server.routeRequest(
            HttpRequestData(
                "POST",
                "/api/web/login",
                emptyMap(),
                "{\"username\":\"truong_thcs_a\",\"password\":\"secret\",\"route\":\"/check-in-out\"}"
            )
        )
        assertEquals(200, loginResp.statusCode)
        assertEquals("truong_thcs_a", delegate.lastLoginUser)

        val navResp = server.routeRequest(
            HttpRequestData("POST", "/api/web/navigate", emptyMap(), "{\"route\":\"/danh-sach-diem-danh\"}")
        )
        assertEquals(200, navResp.statusCode)
        assertEquals("/danh-sach-diem-danh", delegate.lastNavigateRoute)

        val shotResp = server.routeRequest(
            HttpRequestData("GET", "/api/web/screenshot", emptyMap(), "")
        )
        assertEquals(200, shotResp.statusCode)
        assertEquals("image/jpeg", shotResp.contentType)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()), shotResp.bodyBytes)

        val badRfid = server.routeRequest(
            HttpRequestData("POST", "/api/rfid/scan", emptyMap(), "{\"cardId\":\"bad space!\"}")
        )
        assertEquals(400, badRfid.statusCode)
    }

    @Test
    fun routeRequest_enforcesPinRateLimitingAndSessionExpiration() {
        var nowMs = 1_000_000L
        val delegate = FakeDelegate(pin = "2468", localhostBypass = false)
        val server = LocalControlServer(8888, delegate, {}, clockMs = { nowMs })

        repeat(5) {
            val bad = server.routeRequest(
                HttpRequestData("POST", "/api/auth", emptyMap(), "{\"pin\":\"0000\"}", clientIp = "192.168.1.90")
            )
            assertEquals(401, bad.statusCode)
        }

        val throttled = server.routeRequest(
            HttpRequestData("POST", "/api/auth", emptyMap(), "{\"pin\":\"2468\"}", clientIp = "192.168.1.90")
        )
        assertEquals(429, throttled.statusCode)

        nowMs += 61_000L
        val goodAuth = server.routeRequest(
            HttpRequestData("POST", "/api/auth", emptyMap(), "{\"pin\":\"2468\"}", clientIp = "192.168.1.90")
        )
        assertEquals(200, goodAuth.statusCode)
        val token = JSONObject(goodAuth.body).getString("token")

        val authedCmd = server.routeRequest(
            HttpRequestData(
                "POST",
                "/api/command",
                mapOf("x-admin-token" to token),
                "{\"action\":\"relogin\"}",
                clientIp = "192.168.1.90"
            )
        )
        assertEquals(200, authedCmd.statusCode)
        assertEquals("relogin", delegate.lastAction)

        nowMs += 24L * 60L * 60L * 1000L + 5_000L
        val expiredCmd = server.routeRequest(
            HttpRequestData(
                "POST",
                "/api/command",
                mapOf("x-admin-token" to token),
                "{\"action\":\"relogin\"}",
                clientIp = "192.168.1.90"
            )
        )
        assertEquals(401, expiredCmd.statusCode)
    }
}
