package com.localstream.localstream_mobile.server

import android.content.Context
import android.net.wifi.WifiManager
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Best-effort reverse mDNS hostname resolution. Sends a PTR query for
 * <reversed-ip>.in-addr.arpa to the mDNS multicast group (224.0.0.251:5353)
 * and extracts the device's ".local" hostname from the response. Used to label
 * VLC/desktop clients with their computer name on the same LAN or hotspot.
 *
 * Failures, timeouts and unsupported networks are non-fatal: the server never
 * blocks on resolution, and results are cached so repeated probes are cheap.
 */
class MdnsHostnameResolver(context: Context) {

    private class CachedName(val hostname: String, val expiresAtMs: Long)

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val cache = ConcurrentHashMap<String, CachedName>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val requestId = AtomicInteger(0)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "localstream-mdns-reverse").apply { isDaemon = true }
    }

    companion object {
        private const val MDNS_ADDR = "224.0.0.251"
        private const val MDNS_PORT = 5353
        private const val MDNS_TTL_MS = 5 * 60 * 1000L
        private const val QUERY_TIMEOUT_MS = 2000
        private const val QUERY_TRIES = 2
        private const val TYPE_PTR = 12
        private const val QCLASS_IN = 1
    }

    /**
     * Returns a cached hostname immediately, or schedules an asynchronous
     * probe and returns null for now. The hostname appears on the next
     * snapshot once resolved.
     */
    fun resolve(ip: String): String? {
        cache[ip]?.let { cached ->
            if (cached.expiresAtMs > System.currentTimeMillis()) return cached.hostname
        }
        if (inFlight.add(ip)) {
            executor.execute { queryAndCache(ip) }
        }
        return null
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun queryAndCache(ip: String) {
        try {
            queryOnce(ip)?.let { hostname ->
                cache[ip] = CachedName(hostname, System.currentTimeMillis() + MDNS_TTL_MS)
            }
        } catch (_: Exception) {
            // Never propagate resolver errors onto callers
        } finally {
            inFlight.remove(ip)
        }
    }

    private fun queryOnce(ip: String): String? {
        val lock = acquireMulticastLock() ?: return null
        var socket: DatagramSocket? = null
        try {
            val query = buildPtrQuery(ip, requestId.incrementAndGet())
            val group = InetAddress.getByName(MDNS_ADDR)
            socket = DatagramSocket()
            socket.soTimeout = QUERY_TIMEOUT_MS
            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)
            for (attempt in 0 until QUERY_TRIES) {
                socket.send(DatagramPacket(query, query.size, group, MDNS_PORT))
                val deadline = System.currentTimeMillis() + QUERY_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    socket.soTimeout = maxOf(1, (deadline - System.currentTimeMillis()).toInt())
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break // wait for the next query round instead of blocking
                    }
                    val name = parsePtrResponse(packet.data, packet.length)
                    if (name != null) return name
                }
            }
        } catch (_: Exception) {
            // Multicast unsupported (emulator, airplane mode) or no responder
        } finally {
            socket?.close()
            releaseMulticastLock(lock)
        }
        return null
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        return try {
            val lock = wifiManager?.createMulticastLock("localstream-mdns-reverse")
            lock?.setReferenceCounted(false)
            lock?.acquire()
            lock
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseMulticastLock(lock: WifiManager.MulticastLock?) {
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (_: Exception) {
            // Already released; harmless
        }
    }

    /**
     * Builds a DNS query packet asking for the PTR record of the reversed IP.
     */
    private fun buildPtrQuery(ip: String, id: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((id shr 8) and 0xFF)
        out.write(id and 0xFF)
        out.write(0); out.write(0)   // flags: standard query
        out.write(0); out.write(1)   // QDCOUNT = 1
        out.write(0); out.write(0)   // ANCOUNT
        out.write(0); out.write(0)   // NSCOUNT
        out.write(0); out.write(0)   // ARCOUNT
        for (label in reversePtrLabels(ip)) {
            out.write(label.size)
            out.write(label)
        }
        out.write(0)                 // root label
        out.write(0); out.write(TYPE_PTR)
        out.write(0); out.write(QCLASS_IN)
        return out.toByteArray()
    }

    private fun reversePtrLabels(ip: String): List<ByteArray> {
        val labels = ArrayList<ByteArray>()
        for (part in ip.split('.').reversed()) {
            labels.add(part.toByteArray(Charsets.US_ASCII))
        }
        labels.add("in-addr".toByteArray(Charsets.US_ASCII))
        labels.add("arpa".toByteArray(Charsets.US_ASCII))
        return labels
    }

    /**
     * Scans a DNS response and returns the first PTR target under the ".local"
     * domain (the device's mDNS hostname), or null.
     */
    private fun parsePtrResponse(data: ByteArray, length: Int): String? {
        if (length < 12) return null
        val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val anCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (anCount == 0) return null

        var pos = 12
        for (i in 0 until qdCount) {
            val end = parseNameField(data, pos, length)
            if (end < 0) return null
            pos = end + 4 // skip QTYPE + QCLASS
        }

        for (i in 0 until anCount) {
            val nameEnd = parseNameField(data, pos, length)
            if (nameEnd < 0 || nameEnd + 10 > length) return null
            val type = ((data[nameEnd].toInt() and 0xFF) shl 8) or (data[nameEnd + 1].toInt() and 0xFF)
            val rdLen = ((data[nameEnd + 8].toInt() and 0xFF) shl 8) or (data[nameEnd + 9].toInt() and 0xFF)
            val rdata = nameEnd + 10
            if (type == TYPE_PTR && rdLen > 2 && rdata + rdLen <= length) {
                val target = readName(data, rdata, length).trimEnd('.')
                if (target.isNotEmpty() && target.endsWith(".local", ignoreCase = true)) {
                    return target
                }
            }
            pos = rdata + rdLen
        }
        return null
    }

    /** Returns the byte offset just after a (possibly compressed) DNS name field, or -1. */
    private fun parseNameField(data: ByteArray, offset: Int, length: Int): Int {
        var p = offset
        var endPos = offset
        var jumped = false
        var guard = 0
        while (guard++ < 128) {
            if (p >= length) return -1
            val len = data[p].toInt() and 0xFF
            if (len == 0) {
                return if (jumped) endPos else p + 1
            }
            if (len and 0xC0 == 0xC0) {
                if (p + 1 >= length) return -1
                val ptr = ((len and 0x3F) shl 8) or (data[p + 1].toInt() and 0xFF)
                if (!jumped) {
                    endPos = p + 2
                    jumped = true
                }
                p = ptr
                continue
            }
            p += 1 + len
        }
        return -1
    }

    private fun readName(data: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder()
        var p = offset
        var guard = 0
        while (guard++ < 128) {
            if (p >= length) break
            val len = data[p].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) {
                if (p + 1 >= length) break
                val ptr = ((len and 0x3F) shl 8) or (data[p + 1].toInt() and 0xFF)
                p = ptr
                continue
            }
            if (p + 1 + len > length) break
            for (i in 1..len) {
                val c = data[p + i].toInt() and 0xFF
                sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
            }
            sb.append('.')
            p += 1 + len
        }
        return sb.toString()
    }
}