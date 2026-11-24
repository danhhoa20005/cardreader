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
                put("versionName", "1.0.0")
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
    fun parseHttpRequest_parsesMethodPathHeadersAndBody() {
        val raw = "POST /api/command HTTP/1.1\r\n" +
            "Host: diemdanh.local:8080\r\n" +
            "Content-Length: 20\r\n" +
            "X-Admin-Token: abc\r\n" +
            "\r\n" +
            "{\"action\":\"connect\"}"
        val req = LocalControlServer.parseHttpRequest(ByteArrayInputStream(raw.toByteArray()))
        assertNotNull(req)
        assertEquals("POST", req!!.method)
        assertEquals("/api/command", req.path)
        assertEquals("abc", req.headers["x-admin-token"])
        assertEquals("{\"action\":\"connect\"}", req.body)
    }

    @Test
    fun routeRequest_allowsOpenAccessWhenPinIsEmpty() {
        val delegate = FakeDelegate(pin = "")
        val server = LocalControlServer(8080, delegate) {}

        val dashboard = server.routeRequest(HttpRequestData("GET", "/", emptyMap(), ""))
        assertEquals(200, dashboard.statusCode)
        assertTrue(dashboard.body.contains("CardReader"))

        val cmd = server.routeRequest(
            HttpRequestData("POST", "/api/command", emptyMap(), "{\"action\":\"reload_webview\"}")
        )
        assertEquals(200, cmd.statusCode)
        assertEquals("reload_webview", delegate.lastAction)
    }

    @Test
    fun routeRequest_enforcesPinAuthenticationWhenConfigured() {
        val delegate = FakeDelegate(pin = "2468")
        val server = LocalControlServer(8080, delegate) {}

        val unauthCmd = server.routeRequest(
            HttpRequestData("POST", "/api/command", emptyMap(), "{\"action\":\"connect\"}")
        )
        assertEquals(401, unauthCmd.statusCode)

        val badAuth = server.routeRequest(
            HttpRequestData("POST", "/api/auth", emptyMap(), "{\"pin\":\"0000\"}")
        )
        assertEquals(401, badAuth.statusCode)

        val goodAuth = server.routeRequest(
            HttpRequestData("POST", "/api/auth", emptyMap(), "{\"pin\":\"2468\"}")
        )
        assertEquals(200, goodAuth.statusCode)
        val token = JSONObject(goodAuth.body).getString("token")

        val authCmd = server.routeRequest(
            HttpRequestData(
                "POST",
                "/api/command",
                mapOf("x-admin-token" to token),
                "{\"action\":\"relogin\"}"
            )
        )
        assertEquals(200, authCmd.statusCode)
        assertEquals("relogin", delegate.lastAction)
    }
}
