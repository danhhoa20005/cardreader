package com.example.cardreader

import android.content.Context
import android.net.wifi.WifiManager
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

data class ServerResolution(
    val loadUrl: String,
    val isLocal: Boolean,
    val usedFallback: Boolean,
    val resolvedFromMdns: Boolean,
    val statusMessage: String
)

/**
 * Resolves the preferred local server (including `.local` mDNS lookup when system DNS
 * cannot resolve it on older Android Boxes) and falls back to the cloud URL if unreachable.
 */
object ServerResolver {

    private const val MDNS_ADDRESS = "224.0.0.251"
    private const val MDNS_PORT = 5353
    private const val PROBE_TIMEOUT_MS = 2200

    fun resolveTargetUrl(
        context: Context,
        localUrl: String,
        cloudUrl: String,
        autoFallback: Boolean
    ): ServerResolution {
        val cleanLocal = AppConfig.normalizeUrl(localUrl)
        val cleanCloud = AppConfig.normalizeUrl(cloudUrl)

        if (cleanLocal.isNotEmpty()) {
            val (candidateLocal, resolvedMdns) = resolveLocalHostIfNeeded(context, cleanLocal)
            if (probeHttpReachable(candidateLocal, PROBE_TIMEOUT_MS)) {
                val label = if (resolvedMdns) {
                    "Local ($candidateLocal qua mDNS)"
                } else {
                    "Local ($candidateLocal)"
                }
                return ServerResolution(
                    loadUrl = candidateLocal,
                    isLocal = true,
                    usedFallback = false,
                    resolvedFromMdns = resolvedMdns,
                    statusMessage = "Đang dùng $label"
                )
            }
        }

        if (autoFallback && cleanCloud.isNotEmpty()) {
            return ServerResolution(
                loadUrl = cleanCloud,
                isLocal = false,
                usedFallback = true,
                resolvedFromMdns = false,
                statusMessage = "Không kết nối được Local, chuyển sang Cloud: $cleanCloud"
            )
        }

        val target = cleanLocal.ifEmpty { cleanCloud }
        return ServerResolution(
            loadUrl = target,
            isLocal = cleanLocal.isNotEmpty(),
            usedFallback = false,
            resolvedFromMdns = false,
            statusMessage = "Đang thử kết nối: $target"
        )
    }

    fun resolveLocalHostIfNeeded(context: Context, urlStr: String): Pair<String, Boolean> {
        val uri = runCatching { URI(urlStr) }.getOrNull() ?: return urlStr to false
        val host = uri.host ?: return urlStr to false

        // Fast path: system DNS or Android native mDNS already resolves the host
        val dnsResolved = runCatching { InetAddress.getByName(host) }.isSuccess
        if (dnsResolved) {
            return urlStr to false
        }

        if (!host.endsWith(".local", ignoreCase = true)) {
            return urlStr to false
        }

        val mdnsIp = queryMdnsIpv4WithLock(context, host, timeoutMs = 1500)
        return if (!mdnsIp.isNullOrBlank()) {
            replaceHostWithIp(urlStr, mdnsIp) to true
        } else {
            urlStr to false
        }
    }

    fun probeHttpReachable(urlStr: String, timeoutMs: Int = PROBE_TIMEOUT_MS): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            code in 100..599
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    fun replaceHostWithIp(urlStr: String, ip: String): String {
        val uri = runCatching { URI(urlStr) }.getOrNull() ?: return urlStr
        val updated = URI(
            uri.scheme,
            uri.userInfo,
            ip,
            uri.port,
            uri.path,
            uri.query,
            uri.fragment
        )
        return updated.toString().trimEnd('/')
    }

    private fun queryMdnsIpv4WithLock(
        context: Context,
        hostname: String,
        timeoutMs: Int
    ): String? {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifiManager?.createMulticastLock("CardReaderMdnsLock")?.apply {
            setReferenceCounted(false)
        }
        return try {
            runCatching { lock?.acquire() }
            queryMdnsIpv4(hostname, timeoutMs)
        } finally {
            runCatching {
                if (lock?.isHeld == true) lock.release()
            }
        }
    }

    private fun queryMdnsIpv4(hostname: String, timeoutMs: Int): String? {
        val query = buildMdnsAQuery(hostname)
        val mdnsAddr = InetAddress.getByName(MDNS_ADDRESS)

        // Try multicast socket on 5353 first so multicast-only responders are also captured;
        // if port 5353 cannot be bound on the box, fall back to an ephemeral unicast socket.
        val multicastIp = runCatching {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(MDNS_PORT))
                socket.soTimeout = timeoutMs
                socket.joinGroup(mdnsAddr)
                socket.send(DatagramPacket(query, query.size, mdnsAddr, MDNS_PORT))
                listenForMatchingAnswer(socket, hostname, timeoutMs)
            }
        }.getOrNull()

        if (!multicastIp.isNullOrBlank()) return multicastIp

        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.send(DatagramPacket(query, query.size, mdnsAddr, MDNS_PORT))
                listenForMatchingAnswer(socket, hostname, timeoutMs)
            }
        }.getOrNull()
    }

    private fun listenForMatchingAnswer(
        socket: DatagramSocket,
        hostname: String,
        timeoutMs: Int
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(1500)
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(100)
            socket.soTimeout = remaining
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                return null
            }
            val resolved = parseMdnsAResponse(packet.data, packet.length, hostname)
            if (!resolved.isNullOrBlank()) return resolved
        }
        return null
    }

    internal fun buildMdnsAQuery(hostname: String): ByteArray {
        val out = ByteArrayOutputStream()
        // DNS header: ID=0, Flags=0 (standard query), QDCOUNT=1, AN=0, NS=0, AR=0
        out.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        val cleanHost = hostname.trim().trimEnd('.')
        for (label in cleanHost.split('.')) {
            if (label.isEmpty()) continue
            val bytes = label.toByteArray(Charsets.UTF_8)
            out.write(bytes.size and 0x3F)
            out.write(bytes)
        }
        out.write(0) // root label
        // QTYPE = A (1)
        out.write(0)
        out.write(1)
        // QCLASS = IN (1) with QU bit (0x8001) requesting unicast reply when supported
        out.write(0x80)
        out.write(0x01)
        return out.toByteArray()
    }

    internal fun parseMdnsAResponse(
        data: ByteArray,
        length: Int,
        expectedHostname: String
    ): String? {
        if (length < 12) return null
        val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if ((flags and 0x8000) == 0) return null // Not a response

        val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val anCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        val nsCount = ((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)
        val arCount = ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)

        var offset = 12
        for (i in 0 until qdCount) {
            val (_, nextOffset) = readDnsName(data, length, offset) ?: return null
            offset = nextOffset + 4 // QTYPE + QCLASS
            if (offset > length) return null
        }

        val target = expectedHostname.trim().trimEnd('.').lowercase()
        val totalRecords = anCount + nsCount + arCount
        for (i in 0 until totalRecords) {
            val (name, nextOffset) = readDnsName(data, length, offset) ?: return null
            if (nextOffset + 10 > length) return null
            val type = ((data[nextOffset].toInt() and 0xFF) shl 8) or (data[nextOffset + 1].toInt() and 0xFF)
            val rdLength = ((data[nextOffset + 8].toInt() and 0xFF) shl 8) or (data[nextOffset + 9].toInt() and 0xFF)
            val rdataOffset = nextOffset + 10
            if (rdataOffset + rdLength > length) return null

            if (type == 1 && rdLength == 4 && name.trimEnd('.').lowercase() == target) {
                val b0 = data[rdataOffset].toInt() and 0xFF
                val b1 = data[rdataOffset + 1].toInt() and 0xFF
                val b2 = data[rdataOffset + 2].toInt() and 0xFF
                val b3 = data[rdataOffset + 3].toInt() and 0xFF
                return "$b0.$b1.$b2.$b3"
            }
            offset = rdataOffset + rdLength
        }
        return null
    }

    private fun readDnsName(data: ByteArray, length: Int, startOffset: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var pos = startOffset
        var jumped = false
        var endOffset = -1
        var steps = 0

        while (pos < length && steps < 128) {
            steps++
            val len = data[pos].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) endOffset = pos + 1
                return sb.toString() to endOffset
            }
            if ((len and 0xC0) == 0xC0) {
                if (pos + 1 >= length) return null
                val ptr = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                if (ptr >= length) return null
                if (!jumped) endOffset = pos + 2
                pos = ptr
                jumped = true
            } else {
                pos++
                if (pos + len > length) return null
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(data, pos, len, Charsets.UTF_8))
                pos += len
            }
        }
        return null
    }
}
