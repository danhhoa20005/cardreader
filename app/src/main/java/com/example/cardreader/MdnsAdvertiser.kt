package com.example.cardreader

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

data class MdnsQuestion(
    val qname: String,
    val qtype: Int,
    val qclass: Int
)

/**
 * Advertises the TV Box on the local LAN as `<mdnsHostname>` (default `diemdanh.local`)
 * using both a UDP mDNS A-record responder (224.0.0.251:5353) and Android NsdManager (_http._tcp).
 */
class MdnsAdvertiser(
    context: Context,
    private val httpPort: Int,
    private val onLog: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val running = AtomicBoolean(false)

    @Volatile
    private var hostname: String = BuildConfig.DEFAULT_MDNS_HOSTNAME

    @Volatile
    private var responderSocket: MulticastSocket? = null

    @Volatile
    private var responderThread: Thread? = null

    @Volatile
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var nsdListener: NsdManager.RegistrationListener? = null

    fun start(initialHostname: String) {
        hostname = AppConfig.normalizeMdnsHostname(initialHostname)
        if (!running.compareAndSet(false, true)) {
            announceNow()
            return
        }

        acquireMulticastLock()
        startUdpResponder()
        registerNsdService()
    }

    fun updateHostname(newHostname: String) {
        val normalized = AppConfig.normalizeMdnsHostname(newHostname)
        if (normalized == hostname && running.get()) {
            announceNow()
            return
        }
        hostname = normalized
        unregisterNsdService()
        if (running.get()) {
            registerNsdService()
            announceNow()
        }
    }

    fun announceNow() {
        if (!running.get()) return
        val ip = findLocalIpv4Address() ?: return
        val packetBytes = buildMdnsAResponse(hostname, ip, ttlSeconds = 120)
        Thread({
            sendMulticastResponse(packetBytes)
        }, "mdns-announce").start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        unregisterNsdService()
        runCatching { responderSocket?.close() }
        responderSocket = null
        responderThread?.interrupt()
        responderThread = null
        releaseMulticastLock()
    }

    fun isRunning(): Boolean = running.get()

    private fun startUdpResponder() {
        val thread = Thread({
            val mdnsGroup = InetAddress.getByName(MDNS_ADDRESS)
            try {
                val socket = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(MDNS_PORT))
                    timeToLive = 255
                }
                responderSocket = socket
                joinActiveLanInterfaces(socket, mdnsGroup)
                onLog("mDNS đang phát tên miền $hostname trên cổng $httpPort")

                // Send initial gratuitous announcement so LAN hosts cache it quickly
                findLocalIpv4Address()?.let { ip ->
                    sendMulticastResponse(buildMdnsAResponse(hostname, ip))
                }

                val buf = ByteArray(2048)
                while (running.get() && !socket.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketException) {
                        break
                    } catch (_: Exception) {
                        continue
                    }

                    val questions = extractMdnsQuestions(packet.data, packet.length)
                    if (questions.isEmpty()) continue

                    val targetHost = hostname.trimEnd('.').lowercase()
                    val matching = questions.firstOrNull { q ->
                        q.qname.trimEnd('.').lowercase() == targetHost &&
                            (q.qtype == DNS_TYPE_A || q.qtype == DNS_TYPE_ANY)
                    } ?: continue

                    val localIp = findLocalIpv4Address() ?: continue
                    val responseBytes = buildMdnsAResponse(hostname, localIp, ttlSeconds = 120)

                    val wantsUnicast = (matching.qclass and 0x8000) != 0 || packet.port != MDNS_PORT
                    if (wantsUnicast) {
                        runCatching {
                            socket.send(
                                DatagramPacket(
                                    responseBytes,
                                    responseBytes.size,
                                    packet.address,
                                    packet.port
                                )
                            )
                        }
                    }
                    sendMulticastResponse(responseBytes)
                }
            } catch (e: Exception) {
                onLog("mDNS UDP responder cảnh báo: ${e.message ?: "Không mở được cổng 5353"}")
            }
        }, "mdns-responder")
        responderThread = thread
        thread.start()
    }

    private fun joinActiveLanInterfaces(socket: MulticastSocket, mdnsGroup: InetAddress) {
        var joinedAny = false
        val groupSock = InetSocketAddress(mdnsGroup, MDNS_PORT)
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || !iface.supportsMulticast()) continue
                val hasSiteIpv4 = iface.inetAddresses.toList().any {
                    it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress
                }
                if (!hasSiteIpv4) continue
                runCatching {
                    socket.joinGroup(groupSock, iface)
                    joinedAny = true
                }
            }
        }
        if (!joinedAny) {
            @Suppress("DEPRECATION")
            runCatching { socket.joinGroup(mdnsGroup) }
        }
    }

    private fun sendMulticastResponse(responseBytes: ByteArray) {
        val socket = responderSocket ?: return
        if (socket.isClosed) return
        val mdnsGroup = InetAddress.getByName(MDNS_ADDRESS)
        runCatching {
            val lanIface = findActiveLanInterface()
            if (lanIface != null) {
                socket.networkInterface = lanIface
            }
            socket.send(DatagramPacket(responseBytes, responseBytes.size, mdnsGroup, MDNS_PORT))
        }
    }

    private fun registerNsdService() {
        val mgr = nsdManager ?: return
        val serviceName = hostname.substringBefore('.').ifBlank { "diemdanh" }
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = "_http._tcp."
            this.port = httpPort
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                onLog("NsdManager đã đăng ký ${info.serviceName}._http._tcp:$httpPort")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onLog("NsdManager đăng ký thất bại (mã $errorCode)")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        nsdListener = listener
        runCatching {
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsdService() {
        val listener = nsdListener ?: return
        nsdListener = null
        runCatching { nsdManager?.unregisterService(listener) }
    }

    private fun acquireMulticastLock() {
        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        if (multicastLock == null) {
            multicastLock = wm.createMulticastLock("CardReaderMdnsResponder").apply {
                setReferenceCounted(false)
            }
        }
        runCatching {
            if (multicastLock?.isHeld == false) multicastLock?.acquire()
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
    }

    companion object {
        private const val MDNS_ADDRESS = "224.0.0.251"
        private const val MDNS_PORT = 5353
        private const val DNS_TYPE_A = 1
        private const val DNS_TYPE_ANY = 255

        fun findLocalIpv4Address(): String? {
            val candidates = mutableListOf<Pair<Int, String>>()
            val interfaces = runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            }.getOrDefault(emptyList())

            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("dummy")) {
                    continue
                }
                val priority = when {
                    name.startsWith("eth") -> 0
                    name.startsWith("wlan") -> 1
                    else -> 2
                }
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (addr.isSiteLocalAddress) {
                            candidates.add(priority to ip)
                        } else {
                            candidates.add((priority + 10) to ip)
                        }
                    }
                }
            }
            return candidates.minByOrNull { it.first }?.second
        }

        private fun findActiveLanInterface(): NetworkInterface? {
            val interfaces = runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            }.getOrDefault(emptyList())
            return interfaces
                .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
                .firstOrNull { iface ->
                    val name = iface.name.lowercase()
                    !name.startsWith("tun") && !name.startsWith("dummy") &&
                        iface.inetAddresses.toList().any {
                            it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress
                        }
                }
        }

        internal fun extractMdnsQuestions(data: ByteArray, length: Int): List<MdnsQuestion> {
            if (length < 12) return emptyList()
            val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            if ((flags and 0x8000) != 0) return emptyList() // Response packet, ignore

            val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            if (qdCount <= 0 || qdCount > 32) return emptyList()

            val result = mutableListOf<MdnsQuestion>()
            var offset = 12
            for (i in 0 until qdCount) {
                val (qname, nextOffset) = readDnsName(data, length, offset) ?: break
                if (nextOffset + 4 > length) break
                val qtype = ((data[nextOffset].toInt() and 0xFF) shl 8) or
                    (data[nextOffset + 1].toInt() and 0xFF)
                val qclass = ((data[nextOffset + 2].toInt() and 0xFF) shl 8) or
                    (data[nextOffset + 3].toInt() and 0xFF)
                result.add(MdnsQuestion(qname, qtype, qclass))
                offset = nextOffset + 4
            }
            return result
        }

        internal fun buildMdnsAResponse(
            hostname: String,
            ipv4: String,
            ttlSeconds: Int = 120
        ): ByteArray {
            val octets = ipv4.split('.').mapNotNull { it.toIntOrNull() }
            require(octets.size == 4) { "Invalid IPv4 address: $ipv4" }

            val out = ByteArrayOutputStream()
            // Header: ID=0, Flags=0x8400 (Response + Authoritative), QD=0, AN=1, NS=0, AR=0
            out.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 0))

            val cleanHost = hostname.trim().trimEnd('.')
            for (label in cleanHost.split('.')) {
                if (label.isEmpty()) continue
                val bytes = label.toByteArray(Charsets.UTF_8)
                out.write(bytes.size and 0x3F)
                out.write(bytes)
            }
            out.write(0)

            // TYPE = A (1)
            out.write(0)
            out.write(1)
            // CLASS = IN (1) + cache-flush bit (0x8001)
            out.write(0x80)
            out.write(0x01)
            // TTL (4 bytes)
            out.write((ttlSeconds ushr 24) and 0xFF)
            out.write((ttlSeconds ushr 16) and 0xFF)
            out.write((ttlSeconds ushr 8) and 0xFF)
            out.write(ttlSeconds and 0xFF)
            // RDLENGTH = 4
            out.write(0)
            out.write(4)
            // RDATA = IPv4 octets
            for (b in octets) {
                out.write(b and 0xFF)
            }
            return out.toByteArray()
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
}
