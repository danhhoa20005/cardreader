package com.example.cardreader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LocalControlServerTest {

    private class FakeDelegate(var pin: String = "") : ControlServerDelegate {
        var lastAction: String? = null
        var lastConfig: JSONObject? = null

        override fun getAdminPin(): String = pin

        override fun buildStatusJson(authorized: Boolean): JSONObject {
            return JSONObject().apply {
                put("locked", !authorized)
                put("port", 8888)
                put("versionName", "1.0.3")
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
    }

    @Test
    fun parseHttpRequest_parsesMethodPathHeadersAndBodyOnPort8888() {
        val raw = "POST /api/command?src=lan HTTP/1.1\r\n" +
            "Host: diemdanh.local:8888\r\n" +
            "Content-Length: 20\r\n" +
            "X-Admin-Token: abc\r\n" +
            "\r\n" +
            "{\"action\":\"connect\"}"
        val req = LocalControlServer.parseHttpRequest(ByteArrayInputStream(raw.toByteArray()), "192.168.1.25")
        assertNotNull(req)
        assertEquals("POST", req!!.method)
        assertEquals("/api/command", req.path)
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

        val headResp = server.routeRequest(HttpRequestData("HEAD", "/", emptyMap(), ""))
        assertEquals(200, headResp.statusCode)
        assertEquals("", headResp.body)

        val getResp = server.routeRequest(HttpRequestData("GET", "/", emptyMap(), ""))
        assertEquals(200, getResp.statusCode)
        assertTrue(getResp.body.contains("8888"))
    }

    @Test
    fun routeRequest_allowsOpenAccessWhenPinIsEmptyAndRejectsMalformedJson() {
        val delegate = FakeDelegate(pin = "")
        val server = LocalControlServer(8888, delegate, onLog = {})

        val cmd = server.routeRequest(
            HttpRequestData("POST", "/api/command", emptyMap(), "{\"action\":\"resume_countdown\"}")
        )
        assertEquals(200, cmd.statusCode)
        assertEquals("resume_countdown", delegate.lastAction)

        val badJson = server.routeRequest(
            HttpRequestData("POST", "/api/config", emptyMap(), "{broken-json")
        )
        assertEquals(400, badJson.statusCode)

        val emptyAction = server.routeRequest(
            HttpRequestData("POST", "/api/command", emptyMap(), "{}")
        )
        assertEquals(400, emptyAction.statusCode)
    }

    @Test
    fun routeRequest_enforcesPinRateLimitingAndSessionExpiration() {
        var nowMs = 1_000_000L
        val delegate = FakeDelegate(pin = "2468")
        val server = LocalControlServer(8888, delegate, {}, clockMs = { nowMs })

        // 5 failed attempts return 401
        repeat(5) {
            val bad = server.routeRequest(
                HttpRequestData("POST", "/api/auth", emptyMap(), "{\"pin\":\"0000\"}", clientIp = "192.168.1.90")
            )
            assertEquals(401, bad.statusCode)
        }

        // 6th attempt within 60s window is blocked with 429 Too Many Requests even with right PIN
        val throttled = server.routeRequest(
            HttpRequestData("POST", "/api/auth", emptyMap(), "{\"pin\":\"2468\"}", clientIp = "192.168.1.90")
        )
        assertEquals(429, throttled.statusCode)

        // Advance clock past 60s rate-limit window -> valid PIN succeeds
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
                "{\"action\":\"relogin\"}"
            )
        )
        assertEquals(200, authedCmd.statusCode)
        assertEquals("relogin", delegate.lastAction)

        // Advance clock past 24h TTL -> token expires with 401
        nowMs += 24L * 60L * 60L * 1000L + 5_000L
        val expiredCmd = server.routeRequest(
            HttpRequestData(
                "POST",
                "/api/command",
                mapOf("x-admin-token" to token),
                "{\"action\":\"relogin\"}"
            )
        )
        assertEquals(401, expiredCmd.statusCode)
    }
}
