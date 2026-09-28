package com.localstream.localstream_mobile.server

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks devices that have connected to the HTTP server. Each tracked client
 * keeps its IP, User-Agent (used to derive kind/platform/device), best-effort
 * resolved hostname, byte counts and per-media playback sessions, so the app
 * can show "which devices are playing right now". Thread-safe and prunable.
 */
class ClientTracker(private val resolver: MdnsHostnameResolver) {

    private class ClientInfo(val ip: String) {
        @Volatile var firstSeenMs: Long = System.currentTimeMillis()
        @Volatile var lastSeenMs: Long = firstSeenMs
        @Volatile var userAgent: String? = null
        @Volatile var hostname: String? = null
        val bytes = AtomicLong(0L)
        val playSessions = ConcurrentHashMap<String, PlaySession>()
    }

    private val clients = ConcurrentHashMap<String, ClientInfo>()

    companion object {
        /** A session is shown as "playing" while bytes have flowed within this window. */
        private const val SESSION_ACTIVE_MS = 30_000L
        /** Finished/abandoned sessions are dropped from the UI after this long. */
        private const val SESSION_PRUNE_MS = 30 * 60_000L
        /** Clients idle longer than this are removed from the tracked set. */
        private const val CLIENT_PRUNE_MS = 10 * 60_000L
        private const val ONLINE_MS = 15_000L
    }

    /** Records any HTTP request from a client (web pages, API calls, streams). */
    fun recordRequest(ip: String?, userAgent: String?) {
        val addr = ip ?: return
        val client = clients.getOrPut(addr) { ClientInfo(addr) }
        client.lastSeenMs = System.currentTimeMillis()
        if (!userAgent.isNullOrBlank()) {
            client.userAgent = userAgent
        }
        tryResolveHostname(client)
    }

    /** Registers (or reuses) the playback session for a media stream request. */
    fun startPlaySession(ip: String?, mediaId: String, mediaName: String): PlaySession? {
        if (mediaId.isEmpty() || mediaName.isEmpty()) return null
        val addr = ip ?: return null
        val client = clients.getOrPut(addr) { ClientInfo(addr) }
        client.lastSeenMs = System.currentTimeMillis()
        tryResolveHostname(client)
        return client.playSessions.getOrPut(mediaId) {
            PlaySession(mediaId, mediaName, System.currentTimeMillis())
        }
    }

    /** Feeds a streamed chunk count back into the owning client + session. */
    fun recordStreamBytes(ip: String?, session: PlaySession?, bytes: Long) {
        if (session == null) return
        session.bytes.addAndGet(bytes)
        session.lastActiveMs = System.currentTimeMillis()
        val addr = ip ?: return
        clients[addr]?.let { client ->
            client.bytes.addAndGet(bytes)
            client.lastSeenMs = System.currentTimeMillis()
        }
    }

    /** Marks a session as closed for this client (ignored if already gone). */
    fun finishPlaySession(ip: String?, session: PlaySession?) {
        val addr = ip ?: return
        val client = clients[addr] ?: return
        val stored = session?.mediaId?.let { client.playSessions[it] }
        if (stored === null || stored !== session) return
        stored.lastActiveMs = System.currentTimeMillis()
    }

    /** Forgets a device (its snapshots and all playback sessions) entirely. */
    fun removeClient(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        return clients.remove(ip) != null
    }

    private fun tryResolveHostname(client: ClientInfo) {
        if (client.hostname != null || client.ip == "127.0.0.1" || client.ip == "::1") return
        resolver.resolve(client.ip)?.let { client.hostname = it }
    }

    /**
     * Builds the current device list, pruning stale entries along the way.
     * Only clients that viewed pages or streamed are surfaced.
     */
    fun snapshots(): List<ClientSnapshot> {
        val now = System.currentTimeMillis()
        val out = ArrayList<ClientSnapshot>()
        for ((ip, client) in clients) {
            val idle = now - client.lastSeenMs
            if (idle > CLIENT_PRUNE_MS) {
                clients.remove(ip, client)
                continue
            }
            client.playSessions.entries.removeAll { (_, s) ->
                now - s.lastActiveMs > SESSION_PRUNE_MS
            }
            tryResolveHostname(client)

            val parsed = UserAgentParser.parse(client.userAgent)
            if (client.userAgent.isNullOrBlank() && client.playSessions.isEmpty()) continue

            val playing = client.playSessions.values
                .sortedBy { it.startedAtMs }
                .reversed()
                .map { session ->
                    PlaySnapshot(
                        mediaId = session.mediaId,
                        mediaName = session.mediaName,
                        startedAtMs = session.startedAtMs,
                        active = now - session.lastActiveMs < SESSION_ACTIVE_MS,
                        bytes = session.bytes.get()
                    )
                }

            val host = client.hostname?.removeSuffix(".local")
            out += ClientSnapshot(
                ip = ip,
                label = buildLabel(parsed, host, ip),
                kind = parsed.kind,
                browser = parsed.browser,
                browserVersion = parsed.browserVersion,
                platform = parsed.platform,
                device = parsed.device,
                hostname = client.hostname,
                userAgent = client.userAgent,
                online = idle < ONLINE_MS,
                firstSeenMs = client.firstSeenMs,
                lastSeenMs = client.lastSeenMs,
                bytes = client.bytes.get(),
                playing = playing
            )
        }
        return out.sortedByDescending { it.lastSeenMs }
    }

    fun snapshotsJson(): String {
        val clientsArr = JSONArray()
        for (snapshot in snapshots()) {
            val playing = JSONArray()
            for (p in snapshot.playing) {
                playing.put(
                    JSONObject()
                        .put("id", p.mediaId)
                        .put("name", p.mediaName)
                        .put("startedAtMs", p.startedAtMs)
                        .put("active", p.active)
                        .put("bytes", p.bytes)
                )
            }
            clientsArr.put(
                JSONObject()
                    .put("ip", snapshot.ip)
                    .put("label", snapshot.label)
                    .put("kind", snapshot.kind)
                    .put("browser", snapshot.browser)
                    .put("browserVersion", snapshot.browserVersion)
                    .put("platform", snapshot.platform)
                    .put("device", snapshot.device)
                    .put("hostname", snapshot.hostname)
                    .put("userAgent", snapshot.userAgent)
                    .put("online", snapshot.online)
                    .put("firstSeenMs", snapshot.firstSeenMs)
                    .put("lastSeenMs", snapshot.lastSeenMs)
                    .put("bytes", snapshot.bytes)
                    .put("playing", playing)
            )
        }
        return JSONObject().put("clients", clientsArr).toString()
    }

    private fun buildLabel(parsed: UserAgentParser.Parsed, host: String?, ip: String): String {
        return when (parsed.kind) {
            "vlc" -> host ?: "VLC (${parsed.platform ?: ip})"
            "browser" -> parsed.device ?: host ?: parsed.platform ?: "Browser"
            "app" -> host ?: parsed.platform ?: "App client"
            else -> host ?: "Device on $ip"
        }
    }
}

/** A client's active/finished playback of one media item. */
class PlaySession(
    val mediaId: String,
    val mediaName: String,
    val startedAtMs: Long
) {
    val bytes = AtomicLong(0L)
    @Volatile var lastActiveMs: Long = startedAtMs
}

data class PlaySnapshot(
    val mediaId: String,
    val mediaName: String,
    val startedAtMs: Long,
    val active: Boolean,
    val bytes: Long
)

data class ClientSnapshot(
    val ip: String,
    val label: String,
    val kind: String,
    val browser: String?,
    val browserVersion: String?,
    val platform: String?,
    val device: String?,
    val hostname: String?,
    val userAgent: String?,
    val online: Boolean,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val bytes: Long,
    val playing: List<PlaySnapshot>
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "ip" to ip,
        "label" to label,
        "kind" to kind,
        "browser" to browser,
        "browserVersion" to browserVersion,
        "platform" to platform,
        "device" to device,
        "hostname" to hostname,
        "userAgent" to userAgent,
        "online" to online,
        "firstSeenMs" to firstSeenMs,
        "lastSeenMs" to lastSeenMs,
        "bytes" to bytes,
        "playing" to playing.map { p ->
            mapOf(
                "id" to p.mediaId,
                "name" to p.mediaName,
                "startedAtMs" to p.startedAtMs,
                "active" to p.active,
                "bytes" to p.bytes
            )
        }
    )
}

/**
 * Lightweight User-Agent classification. Distinguishes VLC/desktop app playbacks
 * from browsers and fills in platform + device info used to make a friendly label.
 */
object UserAgentParser {

    data class Parsed(
        val kind: String,
        val browser: String?,
        val browserVersion: String?,
        val platform: String?,
        val device: String?
    )

    fun parse(userAgent: String?): Parsed {
        val original = userAgent ?: ""
        val ua = original.lowercase()
        if (ua.isBlank()) return Parsed("other", null, null, null, null)

        val platform = detectPlatform(ua)
        val device = detectDevice(original, platform)

        val isVlc = "vlc" in ua || "libvlc" in ua
        val isApp = !isVlc && (
            "okhttp" in ua ||
                "curl" in ua ||
                "python-requests" in ua ||
                "python-urllib" in ua ||
                "ktor" in ua
            )

        var browser: String? = null
        var version: String? = null

        val kind = when {
            isVlc -> {
                browser = "VLC"
                version = Regex("VLC/([\\d.]+)").find(original)?.groupValues?.get(1)
                "vlc"
            }
            isApp -> "app"
            else -> {
                val b = detectBrowser(ua, original)
                browser = b.first
                version = b.second
                if (browser != null) "browser" else "other"
            }
        }

        return Parsed(kind, browser, version, platform, device)
    }

    private fun detectBrowser(lower: String, original: String): Pair<String?, String?> {
        fun versionOf(pattern: Regex): String? = pattern.find(original)?.groupValues?.get(1)
        return when {
            "edg/" in lower -> "Edge" to versionOf(Regex("Edg/([\\d.]+)", RegexOption.IGNORE_CASE))
            "samsungbrowser/" in lower -> "Samsung Internet" to versionOf(Regex("SamsungBrowser/([\\d.]+)", RegexOption.IGNORE_CASE))
            "opr/" in lower || "opera" in lower -> "Opera" to versionOf(Regex("OPR/([\\d.]+)", RegexOption.IGNORE_CASE))
            "crios/" in lower -> "Chrome" to versionOf(Regex("CriOS/([\\d.]+)", RegexOption.IGNORE_CASE))
            "chrome/" in lower -> "Chrome" to versionOf(Regex("Chrome/([\\d.]+)", RegexOption.IGNORE_CASE))
            "fxios/" in lower || "firefox/" in lower -> "Firefox" to versionOf(Regex("(?:Firefox|FxiOS)/([\\d.]+)", RegexOption.IGNORE_CASE))
            "safari/" in lower -> "Safari" to versionOf(Regex("Version/([\\d.]+)", RegexOption.IGNORE_CASE))
            else -> null to null
        }
    }

    private fun detectPlatform(lower: String): String? = when {
        "android" in lower -> "Android"
        "windows phone" in lower -> "Windows Phone"
        "windows nt" in lower -> "Windows"
        "iphone" in lower || "ipad" in lower || "ipod" in lower -> "iOS"
        "mac os x" in lower || "macintosh" in lower -> "macOS"
        "cros" in lower -> "ChromeOS"
        "linux" in lower -> "Linux"
        else -> null
    }

    private fun detectDevice(original: String, platform: String?): String? {
        if (platform == "Android") {
            val m = Regex("Android [\\d.]+; ([^;()]*?)\\s+Build").find(original)
            if (m != null) {
                val model = m.groupValues[1].trim()
                if (model.isNotEmpty()) return model
            }
        }
        val lower = original.lowercase()
        if (platform == "iOS") {
            if ("ipad" in lower) return "iPad"
            if ("iphone" in lower) return "iPhone"
        }
        return null
    }
}