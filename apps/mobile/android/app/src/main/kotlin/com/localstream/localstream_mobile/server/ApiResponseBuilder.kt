package com.localstream.localstream_mobile.server

import android.os.Build
import com.localstream.localstream_mobile.storage.MediaSource
import org.json.JSONArray
import org.json.JSONObject

object ApiResponseBuilder {

    const val PROTOCOL_VERSION = "1"
    const val SERVER_NAME = "LocalStream"
    const val SERVER_VERSION = "0.1.0"

    fun buildInfoJson(port: Int): String {
        val json = JSONObject()
        json.put("name", SERVER_NAME)
        json.put("version", SERVER_VERSION)
        json.put("deviceName", Build.MODEL ?: "Android Device")
        json.put("serverVersion", SERVER_VERSION)
        json.put("port", port)
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildStatusJson(status: ServerStatus): String {
        val json = JSONObject()
        json.put("state", status.state.name.lowercase())
        json.put("port", status.port)
        json.put("activeClients", status.activeClients)
        json.put("uptimeMs", status.uptimeMs)
        json.put("activeStreams", status.activeStreams)
        json.put("bytesTransferred", status.bytesTransferred)
        json.put("protocolVersion", PROTOCOL_VERSION)
        val addrArray = JSONArray()
        for (addr in status.addresses) {
            addrArray.put(addr)
        }
        json.put("addresses", addrArray)
        return json.toString()
    }

    fun buildFilesJson(sources: List<MediaSource>): String {
        val json = JSONObject()
        val itemsArray = JSONArray()
        for (source in sources) {
            val item = JSONObject()
            item.put("id", source.id)
            item.put("name", source.displayName)
            item.put("type", source.mediaType)
            item.put("mimeType", source.mimeType)
            item.put("size", source.sizeBytes ?: -1L)
            item.put("available", source.exists())
            itemsArray.put(item)
        }
        json.put("items", itemsArray)
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildFileDetailJson(source: MediaSource): String {
        val json = JSONObject()
        json.put("id", source.id)
        json.put("name", source.displayName)
        json.put("type", source.mediaType)
        json.put("mimeType", source.mimeType)
        json.put("size", source.sizeBytes ?: -1L)
        json.put("source", source.sourceKind)
        json.put("available", source.exists())
        json.put("streamUrl", "/api/v1/stream/${source.id}")
        json.put("protocolVersion", PROTOCOL_VERSION)
        return json.toString()
    }

    fun buildErrorJson(code: String, message: String): String {
        val root = JSONObject()
        val errorObj = JSONObject()
        errorObj.put("code", code)
        errorObj.put("message", message)
        root.put("error", errorObj)
        root.put("protocolVersion", PROTOCOL_VERSION)
        return root.toString()
    }

    fun buildWebIndexHtml(sources: List<MediaSource>): String {
        val itemsHtml = StringBuilder()
        for (s in sources) {
            val streamUrl = "/api/v1/stream/${s.id}"
            val watchUrl = "/watch/${s.id}"
            val sizeText = formatSize(s.sizeBytes)
            itemsHtml.append("""
                <div class="card">
                    <div class="card-info">
                        <span class="badge ${s.mediaType}">${s.mediaType.uppercase()}</span>
                        <h3>${escapeHtml(s.displayName)}</h3>
                        <p>${s.mimeType} &bull; $sizeText</p>
                    </div>
                    <div class="card-actions">
                        <a href="$watchUrl" class="btn btn-primary">Watch</a>
                        <a href="$streamUrl" class="btn btn-outline" download>Download</a>
                    </div>
                </div>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>LocalStream Media</title>
                <style>
                    :root {
                        --bg: #0d1117;
                        --surface: #161b22;
                        --border: #30363d;
                        --text: #f0f6fc;
                        --text-muted: #8b949e;
                        --accent: #58a6ff;
                        --video: #238636;
                        --audio: #8957e5;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        margin: 0;
                        padding: 24px;
                    }
                    .header {
                        max-width: 900px;
                        margin: 0 auto 32px;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                    }
                    .header h1 { margin: 0; font-size: 28px; color: var(--accent); }
                    .header span { color: var(--text-muted); font-size: 14px; }
                    .grid {
                        max-width: 900px;
                        margin: 0 auto;
                        display: grid;
                        gap: 16px;
                    }
                    .card {
                        background: var(--surface);
                        border: 1px solid var(--border);
                        border-radius: 10px;
                        padding: 16px 20px;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                    }
                    .card-info h3 { margin: 6px 0 4px; font-size: 16px; }
                    .card-info p { margin: 0; color: var(--text-muted); font-size: 13px; }
                    .badge {
                        display: inline-block;
                        padding: 2px 8px;
                        border-radius: 12px;
                        font-size: 11px;
                        font-weight: bold;
                        background: var(--border);
                    }
                    .badge.video { background: var(--video); color: white; }
                    .badge.audio { background: var(--audio); color: white; }
                    .card-actions { display: flex; gap: 8px; }
                    .btn {
                        padding: 8px 16px;
                        border-radius: 6px;
                        text-decoration: none;
                        font-size: 13px;
                        font-weight: 500;
                        transition: opacity 0.2s;
                    }
                    .btn-primary { background: var(--accent); color: #0d1117; }
                    .btn-outline { border: 1px solid var(--border); color: var(--text); }
                    .btn:hover { opacity: 0.85; }
                    .empty { text-align: center; color: var(--text-muted); padding: 48px; }
                    @media (max-width: 640px) {
                        body { padding: 16px; }
                        .header, .card { align-items: flex-start; flex-direction: column; gap: 12px; }
                        .card-actions { width: 100%; }
                        .btn { flex: 1; text-align: center; }
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <div>
                        <h1>LocalStream</h1>
                        <span>Local Media Server</span>
                    </div>
                    <span>${sources.size} items available</span>
                </div>
                <div class="grid">
                    ${if (sources.isEmpty()) "<div class='empty'>No media files shared yet. Add folders or share media from phone.</div>" else itemsHtml.toString()}
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun buildWebPlayerHtml(source: MediaSource): String {
        val streamUrl = "/api/v1/stream/${source.id}"
        val isVideo = source.mediaType == "video"
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${escapeHtml(source.displayName)} - LocalStream</title>
                <style>
                    :root {
                        color-scheme: dark;
                        --bg: #0b0e12;
                        --panel: #151a21;
                        --panel-soft: #1b222c;
                        --border: #2b3542;
                        --text: #edf2f7;
                        --muted: #94a3b3;
                        --accent: #67d4c0;
                        --good: #7ee787;
                    }
                    body {
                        background: var(--bg);
                        color: var(--text);
                        margin: 0;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        min-height: 100vh;
                        font-family: ui-sans-serif, system-ui, sans-serif;
                    }
                    .player-container {
                        width: min(100% - 32px, 1000px);
                        padding: 28px 0 44px;
                    }
                    video, audio {
                        width: 100%;
                        display: block;
                        border: 1px solid var(--border);
                        border-radius: 6px;
                        background: #000;
                        box-shadow: 0 16px 44px rgba(0,0,0,0.35);
                    }
                    .title-bar {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        gap: 16px;
                        margin-bottom: 14px;
                    }
                    a { color: var(--accent); text-decoration: none; font-size: 13px; }
                    h2 { margin: 0; font-size: 19px; overflow-wrap: anywhere; }
                    .meta { color: var(--muted); font-size: 12px; margin: 12px 0 20px; }
                    .diagnostics {
                        display: grid;
                        grid-template-columns: repeat(3, 1fr);
                        gap: 1px;
                        margin-top: 20px;
                        overflow: hidden;
                        border: 1px solid var(--border);
                        border-radius: 6px;
                        background: var(--border);
                    }
                    .section { background: var(--panel); padding: 16px; }
                    .section h3 {
                        margin: 0 0 12px;
                        color: var(--accent);
                        font-size: 11px;
                        letter-spacing: .08em;
                        text-transform: uppercase;
                    }
                    .metric {
                        display: flex;
                        justify-content: space-between;
                        gap: 12px;
                        padding: 7px 0;
                        border-top: 1px solid var(--panel-soft);
                        font-size: 12px;
                    }
                    .metric span:first-child { color: var(--muted); }
                    .metric span:last-child { text-align: right; overflow-wrap: anywhere; }
                    .status { color: var(--good); }
                    .help {
                        color: var(--muted);
                        font-size: 13px;
                        line-height: 1.5;
                        margin-top: 14px;
                    }
                    .help code {
                        color: var(--text);
                        overflow-wrap: anywhere;
                    }
                    @media (max-width: 720px) {
                        .diagnostics { grid-template-columns: 1fr; }
                        .player-container { padding-top: 16px; }
                        .title-bar { align-items: flex-start; flex-direction: column; }
                    }
                </style>
            </head>
            <body>
                <div class="player-container">
                    <div class="title-bar">
                        <h2>${escapeHtml(source.displayName)}</h2>
                        <a href="/">&larr; Back to Library</a>
                    </div>
                    ${if (isVideo) """
                        <video id="media" controls autoplay preload="metadata" playsinline>
                            <source src="$streamUrl" type="${source.mimeType}">
                            Your browser does not support HTML5 video streaming.
                        </video>
                    """ else """
                        <audio id="media" controls autoplay preload="metadata">
                            <source src="$streamUrl" type="${source.mimeType}">
                            Your browser does not support audio streaming.
                        </audio>
                    """}
                    <div class="meta">${escapeHtml(source.mimeType)} &bull; ${formatSize(source.sizeBytes)}</div>
                    <div class="diagnostics">
                        <section class="section">
                            <h3>Playback</h3>
                            <div class="metric"><span>Resolution</span><span id="resolution">-</span></div>
                            <div class="metric"><span>Codec</span><span id="codec">${escapeHtml(source.mimeType)}</span></div>
                            <div class="metric"><span>Duration</span><span id="duration">-</span></div>
                            <div class="metric"><span>Position</span><span id="position">-</span></div>
                        </section>
                        <section class="section">
                            <h3>Network</h3>
                            <div class="metric"><span>Current throughput</span><span id="throughput">-</span></div>
                            <div class="metric"><span>Average throughput</span><span id="average">-</span></div>
                            <div class="metric"><span>Movie bitrate</span><span id="bitrate">-</span></div>
                            <div class="metric"><span>Buffer</span><span id="buffer">-</span></div>
                            <div class="metric"><span>Connection</span><span id="connection" class="status">Waiting</span></div>
                            <div class="metric"><span>Status</span><span id="networkStatus" class="status">Waiting</span></div>
                            <div class="metric"><span>Server</span><span id="server"></span></div>
                        </section>
                        <section class="section">
                            <h3>Performance</h3>
                            <div class="metric"><span>FPS</span><span id="fps">-</span></div>
                            <div class="metric"><span>Dropped frames</span><span id="dropped">-</span></div>
                            <div class="metric"><span>Rendered frames</span><span id="rendered">-</span></div>
                        </section>
                    </div>
                    <div class="help">
                        If this file does not play in the browser, open the stream in VLC or another player that supports
                        <code>${escapeHtml(source.mimeType)}</code>.
                        <div class="vlcurl">
                            <input id="vlcUrl" type="text" readonly value="$streamUrl" aria-label="VLC stream URL">
                            <button id="vlcCopy" type="button">Copy</button>
                        </div>
                        <div id="vlcHint" class="hint">In VLC: press Ctrl&#43;V (macOS Cmd&#43;V) to open the copied &ldquo;network stream&rdquo; URL, then click Play.</div>
                    </div>
                </div>
                <script>
                    (() => {
                        const media = document.getElementById('media');
                        const mediaSizeBytes = ${source.sizeBytes ?: -1};
                        const vlcInput = document.getElementById('vlcUrl');
                        const vlcCopy = document.getElementById('vlcCopy');
                        if (vlcInput) {
                            const absoluteUrl = new URL(vlcInput.value, location.href).href;
                            vlcInput.value = absoluteUrl;
                            const copyVlcUrl = () => {
                                vlcInput.select();
                                if (navigator.clipboard) {
                                    navigator.clipboard.writeText(absoluteUrl).catch(() => {});
                                } else {
                                    document.execCommand('copy');
                                }
                            };
                            if (vlcCopy) vlcCopy.addEventListener('click', copyVlcUrl);
                            vlcInput.addEventListener('click', copyVlcUrl);
                        }
                        const text = (id, value) => document.getElementById(id).textContent = value;
                        const formatTime = (seconds) => {
                            if (!Number.isFinite(seconds)) return '-';
                            const total = Math.max(0, Math.floor(seconds));
                            const hours = Math.floor(total / 3600);
                            const minutes = Math.floor((total % 3600) / 60);
                            const secs = total % 60;
                            return [hours, minutes, secs].map((value, index) =>
                                index === 0 ? String(value).padStart(2, '0') : String(value).padStart(2, '0')
                            ).join(':');
                        };
                        const updatePlayback = () => {
                            if (media.videoWidth) text('resolution', media.videoWidth + ' × ' + media.videoHeight);
                            text('duration', formatTime(media.duration));
                            text('position', formatTime(media.currentTime));
                            if (mediaSizeBytes > 0 && Number.isFinite(media.duration) && media.duration > 0) {
                                text('bitrate', formatRate(mediaSizeBytes * 8 / media.duration));
                            }
                            if (media.buffered.length) {
                                const end = media.buffered.end(media.buffered.length - 1);
                                text('buffer', Math.max(0, end - media.currentTime).toFixed(1) + ' sec');
                            }
                        };
                        const updateQuality = () => {
                            if (!media.getVideoPlaybackQuality) return;
                            const quality = media.getVideoPlaybackQuality();
                            text('dropped', quality.droppedVideoFrames.toLocaleString());
                            text('rendered', quality.totalVideoFrames.toLocaleString());
                        };
                        const formatRate = (bitsPerSecond) => {
                            if (!Number.isFinite(bitsPerSecond) || bitsPerSecond <= 0) return '-';
                            return (bitsPerSecond / 1000000).toFixed(1) + ' Mbps';
                        };
                        let observedBytes = 0;
                        let lastObservedBytes = 0;
                        let measurementStart = performance.now();
                        let lastMeasurementTime = measurementStart;
                        const updateNetwork = () => {
                            const now = performance.now();
                            const streamUrl = new URL(media.currentSrc, location.href).href;
                            const entries = performance.getEntriesByName(streamUrl);
                            const bytes = entries.reduce((total, entry) => total + (entry.transferSize || entry.encodedBodySize || 0), 0);
                            if (bytes >= observedBytes) {
                                observedBytes = bytes;
                                const currentElapsed = Math.max((now - lastMeasurementTime) / 1000, 0.1);
                                const averageElapsed = Math.max((now - measurementStart) / 1000, 0.1);
                                text('throughput', formatRate((observedBytes - lastObservedBytes) * 8 / currentElapsed));
                                text('average', formatRate(observedBytes * 8 / averageElapsed));
                                lastObservedBytes = observedBytes;
                                lastMeasurementTime = now;
                            }
                            const bufferSeconds = media.buffered.length
                                ? Math.max(0, media.buffered.end(media.buffered.length - 1) - media.currentTime)
                                : 0;
                            const movieBitrate = mediaSizeBytes > 0 && Number.isFinite(media.duration) && media.duration > 0
                                ? mediaSizeBytes * 8 / media.duration
                                : 0;
                            const averageRate = observedBytes > 0
                                ? observedBytes * 8 / Math.max((now - measurementStart) / 1000, 0.1)
                                : 0;
                            text('connection', media.paused ? 'Paused' : (media.readyState >= 3 ? 'Stable' : 'Buffering'));
                            if (!movieBitrate || !averageRate) {
                                text('networkStatus', 'Measuring');
                            } else if (averageRate < movieBitrate && bufferSeconds < 2) {
                                text('networkStatus', 'INSUFFICIENT BANDWIDTH');
                            } else if (averageRate >= movieBitrate || bufferSeconds >= 5) {
                                text('networkStatus', 'GOOD');
                            } else {
                                text('networkStatus', 'BUFFERING RISK');
                            }
                        };
                        text('server', location.host);
                        let startFpsMeasurement = () => {};
                        let stopFpsMeasurement = () => {};
                        media.addEventListener('loadedmetadata', updatePlayback);
                        media.addEventListener('durationchange', updatePlayback);
                        media.addEventListener('timeupdate', updatePlayback);
                        media.addEventListener('progress', updatePlayback);
                        media.addEventListener('waiting', () => {
                            updateNetwork();
                            stopFpsMeasurement();
                        });
                        media.addEventListener('playing', () => {
                            updateNetwork();
                            startFpsMeasurement();
                        });
                        setInterval(() => { updatePlayback(); updateQuality(); updateNetwork(); }, 1000);
                        if (media.requestVideoFrameCallback) {
                            let fpsWindowStart = 0;
                            let frameCount = 0;
                            let measuringFps = false;
                            const frame = (now) => {
                                if (!measuringFps) return;
                                frameCount++;
                                if (now - fpsWindowStart >= 1000) {
                                    text('fps', (frameCount * 1000 / (now - fpsWindowStart)).toFixed(1));
                                    fpsWindowStart = now;
                                    frameCount = 0;
                                }
                                media.requestVideoFrameCallback(frame);
                            };
                            startFpsMeasurement = () => {
                                if (measuringFps) return;
                                measuringFps = true;
                                fpsWindowStart = performance.now();
                                frameCount = 0;
                                media.requestVideoFrameCallback(frame);
                            };
                            stopFpsMeasurement = () => {
                                measuringFps = false;
                                frameCount = 0;
                                text('fps', '-');
                            };
                            media.addEventListener('pause', stopFpsMeasurement);
                            media.addEventListener('ended', stopFpsMeasurement);
                            if (!media.paused) startFpsMeasurement();
                        }
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun formatSize(sizeBytes: Long?): String {
        val size = sizeBytes ?: return "Unknown size"
        if (size <= 0) return "Unknown size"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            size < 1024 -> "$size B"
            size < mb -> String.format("%.1f KB", size / kb)
            size < gb -> String.format("%.1f MB", size / mb)
            else -> String.format("%.2f GB", size / gb)
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
