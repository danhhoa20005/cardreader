package com.example.cardreader

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class HttpRequestData(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
    val clientIp: String = "127.0.0.1",
    val bodyTooLarge: Boolean = false
)

data class HttpResponseData(
    val statusCode: Int,
    val statusText: String,
    val contentType: String,
    val body: String,
    val extraHeaders: Map<String, String> = emptyMap()
)

interface ControlServerDelegate {
    fun getAdminPin(): String
    fun buildStatusJson(authorized: Boolean): JSONObject
    fun handleConfigUpdate(json: JSONObject): JSONObject
    fun handleRemoteCommand(action: String): JSONObject
    fun handleUpdateCheck(): JSONObject = JSONObject().put("ok", false)
    fun handleUpdateInstall(): JSONObject = JSONObject().put("ok", false)
}

/**
 * Máy chủ HTTP/1.1 nội bộ chạy trên cổng 8888 phục vụ điều khiển TV Box qua mạng LAN.
 * Hỗ trợ xác thực PIN an toàn thời gian hằng (constant-time), giới hạn tốc độ chống dò PIN,
 * thời hạn phiên (TTL 24h) và kiểm tra kích thước payload.
 */
class LocalControlServer(
    private val port: Int,
    private val delegate: ControlServerDelegate,
    private val onLog: (String) -> Unit,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private data class RateRecord(var failures: Int, var windowStartMs: Long)

    private val running = AtomicBoolean(false)
    private val sessions = ConcurrentHashMap<String, Long>()
    private val authFailures = ConcurrentHashMap<String, RateRecord>()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ExecutorService? = null

    val boundPort: Int
        get() = serverSocket?.localPort ?: -1

    fun start() {
        if (!running.compareAndSet(false, true)) return
        workerPool = Executors.newFixedThreadPool(8)

        val thread = Thread({
            var bound: ServerSocket? = null
            var lastErr: Exception? = null
            for (attempt in 1..3) {
                if (!running.get()) return@Thread
                try {
                    val ss = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(port))
                    }
                    bound = ss
                    break
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < 3) {
                        try {
                            Thread.sleep(300L * attempt)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                    }
                }
            }

            val ss = bound
            if (ss == null) {
                running.set(false)
                onLog("Không thể mở Web Server cổng $port: ${lastErr?.message ?: "Cổng bận"}")
                return@Thread
            }

            serverSocket = ss
            onLog("Web điều khiển LAN đang lắng nghe trên cổng ${ss.localPort}")

            while (running.get() && !ss.isClosed) {
                val client = try {
                    ss.accept()
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                    continue
                }
                workerPool?.execute { handleClient(client) }
            }
        }, "local-http-server")
        acceptThread = thread
        thread.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        workerPool?.shutdownNow()
        workerPool = null
    }

    fun isRunning(): Boolean = running.get() && serverSocket?.isClosed == false

    fun clearSessions() {
        sessions.clear()
        authFailures.clear()
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            runCatching {
                sock.soTimeout = CLIENT_TIMEOUT_MS
                val clientIp = sock.inetAddress?.hostAddress ?: "unknown"
                val req = parseHttpRequest(BufferedInputStream(sock.getInputStream()), clientIp) ?: return
                val resp = routeRequest(req)
                writeHttpResponse(sock.getOutputStream(), resp)
            }
        }
    }

    internal fun routeRequest(req: HttpRequestData): HttpResponseData {
        if (req.bodyTooLarge) {
            val err = JSONObject().apply {
                put("ok", false)
                put("message", "Dữ liệu gửi lên vượt quá giới hạn cho phép (64 KB)")
            }
            return jsonResponse(413, "Payload Too Large", err)
        }

        if (req.method == "OPTIONS") {
            return HttpResponseData(204, "No Content", "text/plain; charset=utf-8", "")
        }

        val authorized = isAuthorized(req)

        return when {
            (req.method == "GET" || req.method == "HEAD") && (req.path == "/" || req.path == "/index.html") -> {
                val htmlBody = if (req.method == "HEAD") "" else WebDashboardHtml.HTML
                HttpResponseData(200, "OK", "text/html; charset=utf-8", htmlBody)
            }

            req.method == "GET" && req.path == "/api/health" -> {
                val health = JSONObject().apply {
                    put("ok", true)
                    put("port", if (boundPort > 0) boundPort else port)
                    put("versionName", BuildConfig.VERSION_NAME)
                }
                jsonResponse(200, "OK", health)
            }

            req.method == "GET" && req.path == "/api/status" -> {
                jsonResponse(200, "OK", delegate.buildStatusJson(authorized))
            }

            req.method == "POST" && req.path == "/api/auth" -> {
                if (isRateLimited(req.clientIp)) {
                    val body = JSONObject().apply {
                        put("ok", false)
                        put("message", "Nhập sai mã PIN quá nhiều lần. Vui lòng thử lại sau 60 giây.")
                    }
                    return jsonResponse(429, "Too Many Requests", body)
                }

                val json = runCatching { JSONObject(req.body) }.getOrNull() ?: JSONObject()
                val submittedPin = json.optString("pin", "").trim()
                val currentPin = delegate.getAdminPin()
                if (currentPin.isEmpty() || constantTimeEquals(submittedPin, currentPin)) {
                    clearRateLimit(req.clientIp)
                    pruneSessions()
                    val token = UUID.randomUUID().toString()
                    sessions[token] = clockMs() + SESSION_TTL_MS
                    val body = JSONObject().apply {
                        put("ok", true)
                        put("token", token)
                        put("message", "Đăng nhập quản trị thành công")
                    }
                    jsonResponse(
                        200,
                        "OK",
                        body,
                        mapOf("Set-Cookie" to "cr_token=$token; HttpOnly; SameSite=Strict; Max-Age=86400; Path=/")
                    )
                } else {
                    recordAuthFailure(req.clientIp)
                    val body = JSONObject().apply {
                        put("ok", false)
                        put("message", "Mã PIN không đúng")
                    }
                    jsonResponse(401, "Unauthorized", body)
                }
            }

            req.method == "POST" && req.path == "/api/logout" -> {
                extractToken(req)?.let { sessions.remove(it) }
                val body = JSONObject().apply {
                    put("ok", true)
                    put("message", "Đã khóa phiên điều khiển")
                }
                jsonResponse(
                    200,
                    "OK",
                    body,
                    mapOf("Set-Cookie" to "cr_token=; HttpOnly; SameSite=Strict; Max-Age=0; Path=/")
                )
            }

            req.method == "POST" && req.path == "/api/config" -> {
                if (!authorized) return unauthorizedResponse()
                val json = runCatching { JSONObject(req.body) }.getOrNull()
                    ?: return badRequestResponse("Payload JSON không hợp lệ")
                val result = delegate.handleConfigUpdate(json)
                jsonResponse(200, "OK", result)
            }

            req.method == "POST" && req.path == "/api/command" -> {
                if (!authorized) return unauthorizedResponse()
                val json = runCatching { JSONObject(req.body) }.getOrNull()
                    ?: return badRequestResponse("Payload JSON không hợp lệ")
                val action = json.optString("action", "").trim()
                if (action.isEmpty()) return badRequestResponse("Thiếu trường action")
                val result = delegate.handleRemoteCommand(action)
                jsonResponse(200, "OK", result)
            }

            req.method == "POST" && req.path == "/api/update/check" -> {
                if (!authorized) return unauthorizedResponse()
                jsonResponse(200, "OK", delegate.handleUpdateCheck())
            }

            req.method == "POST" && req.path == "/api/update/install" -> {
                if (!authorized) return unauthorizedResponse()
                jsonResponse(200, "OK", delegate.handleUpdateInstall())
            }

            else -> {
                val err = JSONObject().apply {
                    put("ok", false)
                    put("message", "Không tìm thấy endpoint: ${req.method} ${req.path}")
                }
                jsonResponse(404, "Not Found", err)
            }
        }
    }

    private fun isAuthorized(req: HttpRequestData): Boolean {
        val pin = delegate.getAdminPin()
        if (pin.isEmpty()) return true
        val token = extractToken(req) ?: return false
        val expiresAt = sessions[token] ?: return false
        if (clockMs() > expiresAt) {
            sessions.remove(token)
            return false
        }
        return true
    }

    private fun pruneSessions() {
        val now = clockMs()
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            if (now > iterator.next().value) {
                iterator.remove()
            }
        }
        if (sessions.size >= MAX_ACTIVE_SESSIONS) {
            val oldestKey = sessions.entries.minByOrNull { it.value }?.key
            if (oldestKey != null) {
                sessions.remove(oldestKey)
            }
        }
    }

    private fun isRateLimited(clientIp: String): Boolean {
        val now = clockMs()
        val rec = authFailures[clientIp] ?: return false
        synchronized(rec) {
            if (now - rec.windowStartMs > AUTH_WINDOW_MS) {
                authFailures.remove(clientIp)
                return false
            }
            return rec.failures >= MAX_AUTH_FAILURES
        }
    }

    private fun recordAuthFailure(clientIp: String) {
        val now = clockMs()
        val rec = authFailures.getOrPut(clientIp) { RateRecord(0, now) }
        synchronized(rec) {
            if (now - rec.windowStartMs > AUTH_WINDOW_MS) {
                rec.failures = 1
                rec.windowStartMs = now
            } else {
                rec.failures += 1
            }
        }
    }

    private fun clearRateLimit(clientIp: String) {
        authFailures.remove(clientIp)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val digestA = MessageDigest.getInstance("SHA-256").digest(a.toByteArray(Charsets.UTF_8))
        val digestB = MessageDigest.getInstance("SHA-256").digest(b.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(digestA, digestB)
    }

    private fun extractToken(req: HttpRequestData): String? {
        val headerToken = req.headers["x-admin-token"]?.trim()
        if (!headerToken.isNullOrEmpty()) return headerToken

        val cookie = req.headers["cookie"] ?: return null
        for (part in cookie.split(';')) {
            val trimmed = part.trim()
            if (trimmed.startsWith("cr_token=")) {
                return trimmed.removePrefix("cr_token=").trim()
            }
        }
        return null
    }

    private fun unauthorizedResponse(): HttpResponseData {
        val body = JSONObject().apply {
            put("ok", false)
            put("locked", true)
            put("message", "Cần nhập mã PIN quản trị")
        }
        return jsonResponse(401, "Unauthorized", body)
    }

    private fun badRequestResponse(msg: String): HttpResponseData {
        val body = JSONObject().apply {
            put("ok", false)
            put("message", msg)
        }
        return jsonResponse(400, "Bad Request", body)
    }

    private fun jsonResponse(
        code: Int,
        statusText: String,
        json: JSONObject,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpResponseData {
        return HttpResponseData(
            statusCode = code,
            statusText = statusText,
            contentType = "application/json; charset=utf-8",
            body = json.toString(),
            extraHeaders = extraHeaders
        )
    }

    companion object {
        private const val CLIENT_TIMEOUT_MS = 10_000
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val SESSION_TTL_MS = 24L * 60L * 60L * 1000L
        private const val MAX_ACTIVE_SESSIONS = 32
        private const val MAX_AUTH_FAILURES = 5
        private const val AUTH_WINDOW_MS = 60_000L

        internal fun parseHttpRequest(
            input: InputStream,
            clientIp: String = "127.0.0.1"
        ): HttpRequestData? {
            val headerBytes = ByteArray(MAX_HEADER_BYTES)
            var headerLen = 0
            var headerEndIdx = -1

            while (headerLen < MAX_HEADER_BYTES) {
                val b = input.read()
                if (b == -1) break
                headerBytes[headerLen++] = b.toByte()
                if (headerLen >= 4 &&
                    headerBytes[headerLen - 4] == '\r'.code.toByte() &&
                    headerBytes[headerLen - 3] == '\n'.code.toByte() &&
                    headerBytes[headerLen - 2] == '\r'.code.toByte() &&
                    headerBytes[headerLen - 1] == '\n'.code.toByte()
                ) {
                    headerEndIdx = headerLen - 4
                    break
                }
            }

            if (headerEndIdx <= 0) return null
            val rawHeader = String(headerBytes, 0, headerEndIdx, Charsets.UTF_8)
            val lines = rawHeader.split("\r\n")
            if (lines.isEmpty()) return null

            val requestParts = lines[0].split(' ')
            if (requestParts.size < 2) return null
            val method = requestParts[0].uppercase()
            val rawPath = requestParts[1].substringBefore('?')

            val headers = HashMap<String, String>()
            for (i in 1 until lines.size) {
                val colon = lines[i].indexOf(':')
                if (colon > 0) {
                    val key = lines[i].substring(0, colon).trim().lowercase()
                    val value = lines[i].substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            val rawLength = headers["content-length"]?.toLongOrNull() ?: 0L
            if (rawLength > MAX_BODY_BYTES) {
                return HttpRequestData(method, rawPath, headers, "", clientIp, bodyTooLarge = true)
            }

            val contentLength = rawLength.toInt().coerceAtLeast(0)
            val body = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = input.read(buf, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                String(buf, 0, readTotal, Charsets.UTF_8)
            } else {
                ""
            }

            return HttpRequestData(method, rawPath, headers, body, clientIp)
        }

        private fun writeHttpResponse(out: OutputStream, resp: HttpResponseData) {
            val bodyBytes = resp.body.toByteArray(Charsets.UTF_8)
            val sb = StringBuilder()
            sb.append("HTTP/1.1 ").append(resp.statusCode).append(' ').append(resp.statusText).append("\r\n")
            sb.append("Content-Type: ").append(resp.contentType).append("\r\n")
            sb.append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            sb.append("Cache-Control: no-store, no-cache\r\n")
            sb.append("X-Content-Type-Options: nosniff\r\n")
            sb.append("X-Frame-Options: DENY\r\n")
            sb.append("Referrer-Policy: no-referrer\r\n")
            sb.append("Connection: close\r\n")
            for ((k, v) in resp.extraHeaders) {
                sb.append(k).append(": ").append(v).append("\r\n")
            }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
            if (bodyBytes.isNotEmpty()) {
                out.write(bodyBytes)
            }
            out.flush()
        }
    }
}
