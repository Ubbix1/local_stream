package com.localstream.localstream_mobile.server

import com.localstream.localstream_mobile.storage.MediaMetadataCache
import com.localstream.localstream_mobile.storage.StorageManager
import com.localstream.localstream_mobile.server.ApiResponseBuilder.PROTOCOL_VERSION
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException

class HttpRequestHandler(
    private val socket: Socket,
    private val storageManager: StorageManager,
    private val stateHolder: ServerStateHolder,
    private val port: Int,
    private val serverEvents: ServerEvents,
    private val accessControl: AccessControl,
    private val metadataCache: MediaMetadataCache
) {
    fun handle() {
        stateHolder.incrementClients()
        try {
            // Set socket timeout to 30s to prevent stale connections from holding resources indefinitely
            socket.soTimeout = 30000

            val inputStream = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.US_ASCII), 4096)

            val requestLine = reader.readLine()
            if (requestLine.isNullOrBlank()) {
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendErrorResponse(400, "BAD_REQUEST", "Malformed HTTP request line")
                return
            }

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore('?')

            // Read HTTP headers with safety limit
            val headers = mutableMapOf<String, String>()
            var headerLine: String?
            var headerCount = 0
            val maxHeaders = 100

            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrEmpty()) break
                headerCount++
                if (headerCount > maxHeaders) {
                    sendErrorResponse(431, "REQUEST_HEADER_FIELDS_TOO_LARGE", "Too many header fields")
                    return
                }
                val colonIdx = headerLine!!.indexOf(':')
                if (colonIdx > 0) {
                    val name = headerLine!!.substring(0, colonIdx).trim().lowercase()
                    val value = headerLine!!.substring(colonIdx + 1).trim()
                    headers[name] = value
                }
            }

            val isAuthBypass = authBypass(path, method)
            if (!isAuthBypass && !authorized(headers, fullPath)) {
                sendErrorResponse(401, "AUTH_REQUIRED", "Access PIN required. Verify your PIN at /api/v1/auth/verify.")
                return
            }

            // Parse query parameters (only what we consume: parent)
            val parent = queryParam(fullPath, "parent")

            // Route dispatch
            when {
                method == "OPTIONS" -> {
                    sendCorsPreflightResponse()
                }

                (method == "GET" || method == "HEAD") && path.startsWith("/api/v1/stream/") -> {
                    val id = path.removePrefix("/api/v1/stream/")
                    handleMediaStream(id, headers["range"], isHeadOnly = (method == "HEAD"))
                }

                (method == "GET" || method == "HEAD") && path.startsWith("/api/v1/thumb/") -> {
                    val id = path.removePrefix("/api/v1/thumb/")
                    handleThumbnail(id, isHeadOnly = (method == "HEAD"))
                }

                method == "GET" && path == "/api/v1/events" -> {
                    handleEvents()
                }

                method == "GET" && path == "/api/v1/info" -> {
                    val body = ApiResponseBuilder.buildInfoJson(port)
                    sendJsonResponse(200, "OK", body)
                }

                method == "GET" && path == "/api/v1/status" -> {
                    val body = ApiResponseBuilder.buildStatusJson(stateHolder.buildSnapshot())
                    sendJsonResponse(200, "OK", body)
                }

                method == "GET" && path == "/api/v1/folders" -> {
                    if (parent != null && !storageManager.isValidFolderId(parent)) {
                        sendErrorResponse(400, "INVALID_ID", "Invalid folder ID")
                    } else {
                        val folders = if (parent == null) {
                            storageManager.getRootFolders()
                        } else {
                            storageManager.getChildFolders(parent)
                        }
                        sendJsonResponse(200, "OK", ApiResponseBuilder.buildFoldersJson(folders))
                    }
                }

                method == "GET" && path == "/api/v1/files" -> {
                    if (parent != null) {
                        if (!storageManager.isValidFolderId(parent)) {
                            sendErrorResponse(400, "INVALID_ID", "Invalid folder ID")
                        } else {
                            val folders = storageManager.getChildFolders(parent)
                            val items = storageManager.getSourcesInFolder(parent)
                            sendJsonResponse(200, "OK", buildFolderView(folders, items))
                        }
                    } else {
                        val sources = storageManager.getAllSources()
                        val body = ApiResponseBuilder.buildFilesJson(sources, ::durationOf, ::subtitlesOf)
                        sendJsonResponse(200, "OK", body)
                    }
                }

                method == "GET" && path.startsWith("/api/v1/files/") -> {
                    val id = path.removePrefix("/api/v1/files/")
                    val source = storageManager.getSource(id)
                    if (source == null) {
                        sendErrorResponse(404, "MEDIA_NOT_FOUND", "Media item not found: $id")
                    } else {
                        val body = ApiResponseBuilder.buildFileDetailJson(
                            source,
                            durationMs = durationOf(source),
                            subtitles = subtitlesOf(source)
                        )
                        sendJsonResponse(200, "OK", body)
                    }
                }

                method == "GET" && path.startsWith("/api/v1/transcode/") -> {
                    val id = path.removePrefix("/api/v1/transcode/")
                    val source = storageManager.getSource(id)
                    if (source == null || !storageManager.isValidId(id)) {
                        sendErrorResponse(404, "MEDIA_NOT_FOUND", "Media item not found: $id")
                    } else {
                        val body = ApiResponseBuilder.buildTranscodeStatusJson(id)
                        sendJsonResponse(501, "Not Implemented", body)
                    }
                }

                method == "GET" && path == "/api/v1/auth/config" -> {
                    val json = JSONObject()
                    json.put("pinRequired", accessControl.pinRequired)
                    json.put("serverName", ApiResponseBuilder.SERVER_NAME)
                    json.put("protocolVersion", PROTOCOL_VERSION)
                    sendJsonResponse(200, "OK", json.toString())
                }

                method == "POST" && path == "/api/v1/auth/verify" -> {
                    handlePinVerify(reader, headers)
                }

                method == "POST" && path == "/api/v1/auth/logout" -> {
                    val token = extractToken(headers)
                    accessControl.invalidateToken(token)
                    val json = JSONObject()
                    json.put("ok", true)
                    sendJsonResponseWithCookie(200, "OK", json.toString(), AccessControl.buildClearCookie())
                }

                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = ApiResponseBuilder.buildWebIndexHtml()
                    sendHtmlResponse(200, "OK", html)
                }

                method == "GET" && path.startsWith("/watch/") -> {
                    val id = path.removePrefix("/watch/")
                    val source = storageManager.getSource(id)
                    if (source == null) {
                        sendErrorResponse(404, "MEDIA_NOT_FOUND", "Media item not found for player: $id")
                    } else {
                        val html = ApiResponseBuilder.buildWebPlayerHtml(source)
                        sendHtmlResponse(200, "OK", html)
                    }
                }

                else -> {
                    sendErrorResponse(404, "NOT_FOUND", "Endpoint not found: $path")
                }
            }

        } catch (_: SocketException) {
            // Connection closed or reset by client; cleanup handled in finally
        } catch (e: InterruptedException) {
            // Server shutdown interrupted an SSE/blocking handler
        } catch (e: Exception) {
            try {
                sendErrorResponse(500, "INTERNAL_ERROR", "An unexpected server error occurred")
            } catch (_: Exception) {}
        } finally {
            stateHolder.decrementClients()
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------

    private fun authBypass(path: String, method: String): Boolean {
        if (method == "OPTIONS") return true
        if (path == "/api/v1/info") return true
        if (path == "/api/v1/auth/config" ||
            path == "/api/v1/auth/verify" ||
            path == "/api/v1/auth/logout"
        ) {
            return true
        }
        return false
    }

    private fun authorized(headers: Map<String, String>, fullPath: String): Boolean {
        if (!accessControl.pinRequired) return true
        // Query string (?token=...) lets external players such as VLC, which
        // cannot send the auth header or cookie, stream a PIN-protected
        // library via a shareable URL (the web client appends it in the player).
        val fromQuery = queryParam(fullPath, "token")?.takeIf { it.isNotBlank() }
        if (fromQuery != null && accessControl.isValidToken(fromQuery)) return true
        return accessControl.isValidToken(extractToken(headers))
    }

    private fun extractToken(headers: Map<String, String>): String? {
        headers["x-localstream-token"]?.takeIf { it.isNotBlank() }?.let { return it }
        val cookie = headers["cookie"] ?: return null
        for (part in cookie.split(';')) {
            val trimmed = part.trim()
            if (trimmed.startsWith("ls_token=")) {
                return trimmed.removePrefix("ls_token=")
            }
        }
        return null
    }

    private fun handlePinVerify(reader: BufferedReader, headers: Map<String, String>) {
        val contentLength = headers["content-length"]?.toLongOrNull()
        if (contentLength == null || contentLength <= 0 || contentLength > 2048) {
            sendErrorResponse(400, "BAD_REQUEST", "Missing or oversized request body")
            return
        }
        val bodyChars = CharArray(contentLength.toInt())
        var read = 0
        while (read < bodyChars.size) {
            val n = reader.read(bodyChars, read, bodyChars.size - read)
            if (n == -1) break
            read += n
        }
        val pin = try {
            JSONObject(String(bodyChars, 0, read)).optString("pin", "").trim()
        } catch (e: Exception) {
            ""
        }

        if (!accessControl.verifyPin(pin)) {
            sendErrorResponse(401, "AUTH_INVALID_PIN", "Incorrect PIN")
            return
        }

        val token = accessControl.issueToken()
        val json = JSONObject()
        json.put("token", token)
        json.put("expiresInMs", 30L * 24 * 60 * 60 * 1000)
        json.put("protocolVersion", PROTOCOL_VERSION)
        sendJsonResponseWithCookie(200, "OK", json.toString(), AccessControl.buildCookie(token))
    }

    // ------------------------------------------------------------------
    // Media streaming / thumbnails / folders
    // ------------------------------------------------------------------

    private fun durationOf(source: com.localstream.localstream_mobile.storage.MediaSource): Long? {
        return try {
            metadataCache.durationMs(source)
        } catch (_: Exception) {
            null
        }
    }

    private fun subtitlesOf(source: com.localstream.localstream_mobile.storage.MediaSource): List<com.localstream.localstream_mobile.storage.SubtitleTrack> {
        return if (source.mediaType == "video") storageManager.getSubtitleTracks(source.id) else emptyList()
    }

    private fun buildFolderView(
        folders: List<com.localstream.localstream_mobile.storage.FolderNode>,
        items: List<com.localstream.localstream_mobile.storage.MediaSource>
    ): String {
        return ApiResponseBuilder.buildFolderViewJson(folders, items, ::durationOf, ::subtitlesOf)
    }

    private fun handleMediaStream(id: String, rangeHeader: String?, isHeadOnly: Boolean) {
        if (!storageManager.isValidId(id)) {
            sendErrorResponse(400, "INVALID_ID", "Invalid media item ID")
            return
        }

        val source = storageManager.getSource(id)
        if (source == null) {
            sendErrorResponse(404, "MEDIA_NOT_FOUND", "File is not in library")
            return
        }

        if (!source.exists()) {
            sendErrorResponse(410, "MEDIA_UNAVAILABLE", "File is no longer available on device")
            return
        }

        val fileSize = source.sizeBytes ?: -1L
        val rangeResult = if (fileSize > 0) {
            RangeRequestParser.parse(rangeHeader, fileSize)
        } else {
            RangeRequestParser.RangeResult.Full
        }

        StreamingResponseWriter.writeMediaResponse(
            outputStream = socket.getOutputStream(),
            source = source,
            rangeResult = rangeResult,
            stateHolder = stateHolder,
            isHeadOnly = isHeadOnly
        )
    }

    private fun handleThumbnail(id: String, isHeadOnly: Boolean) {
        if (!storageManager.isValidId(id)) {
            sendErrorResponse(400, "INVALID_ID", "Invalid media item ID")
            return
        }
        val source = storageManager.getSource(id)
        if (source == null) {
            sendErrorResponse(404, "MEDIA_NOT_FOUND", "File is not in library")
            return
        }
        if (!source.exists()) {
            sendErrorResponse(410, "MEDIA_UNAVAILABLE", "File is no longer available on device")
            return
        }

        val thumb: File = try {
            metadataCache.thumbnailFile(source) ?: run {
                sendErrorResponse(404, "THUMB_UNAVAILABLE", "No thumbnail available for $id")
                return
            }
        } catch (e: Exception) {
            sendErrorResponse(404, "THUMB_UNAVAILABLE", "No thumbnail available for $id")
            return
        }

        sendImageResponse(thumb, isHeadOnly)
    }

    private fun handleEvents() {
        val out = socket.getOutputStream()
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\n")
            append("X-Accel-Buffering: no\r\n")
            append("\r\n")
        }
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.flush()

        val hello = JSONObject()
        hello.put("type", "hello")
        out.write("event: hello\ndata: ${hello}\n\n".toByteArray(Charsets.UTF_8))
        out.flush()

        serverEvents.register(out)
        try {
            var beats = 0
            while (true) {
                Thread.sleep(20000)
                // Keep-alive comment (SSE ignore directive) while holding the channel open
                out.write(": ping $beats\n\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                beats++
            }
        } finally {
            serverEvents.unregister(out)
        }
    }

    // ------------------------------------------------------------------
    // Responses
    // ------------------------------------------------------------------

    private fun sendJsonResponse(statusCode: Int, statusText: String, jsonBody: String) {
        sendJsonResponseWithCookie(statusCode, statusText, jsonBody, null)
    }

    private fun sendJsonResponseWithCookie(statusCode: Int, statusText: String, jsonBody: String, setCookie: String?) {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("X-LocalStream-Protocol-Version: $PROTOCOL_VERSION\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Range, Content-Type, X-LocalStream-Token\r\n")
            append("Access-Control-Allow-Credentials: true\r\n")
            append("Access-Control-Expose-Headers: X-LocalStream-Protocol-Version\r\n")
            if (setCookie != null) append("Set-Cookie: $setCookie\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = socket.getOutputStream()
        out.write(response.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun sendImageResponse(file: File, isHeadOnly: Boolean) {
        val length = file.length()
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: image/jpeg\r\n")
            append("Content-Length: $length\r\n")
            append("Cache-Control: public, max-age=86400\r\n")
            append("ETag: \"thumb-${file.name}\"\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = socket.getOutputStream()
        out.write(headers.toByteArray(Charsets.US_ASCII))
        if (!isHeadOnly) {
            file.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        out.flush()
    }

    private fun sendHtmlResponse(statusCode: Int, statusText: String, htmlBody: String) {
        val bytes = htmlBody.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = socket.getOutputStream()
        out.write(response.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun sendErrorResponse(statusCode: Int, errorCode: String, message: String) {
        val body = ApiResponseBuilder.buildErrorJson(errorCode, message)
        sendJsonResponse(statusCode, getStatusText(statusCode), body)
    }

    private fun sendCorsPreflightResponse() {
        val response = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Range, Content-Type, X-LocalStream-Token\r\n")
            append("Access-Control-Allow-Credentials: true\r\n")
            append("Access-Control-Max-Age: 86400\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = socket.getOutputStream()
        out.write(response.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun getStatusText(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        206 -> "Partial Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        410 -> "Gone"
        416 -> "Range Not Satisfiable"
        431 -> "Request Header Fields Too Large"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        else -> "Error"
    }

    private fun queryParam(fullPath: String, name: String): String? {
        val query = fullPath.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (part in query.split('&')) {
            val idx = part.indexOf('=')
            val key = if (idx >= 0) part.substring(0, idx) else part
            if (key == name) {
                return if (idx >= 0) part.substring(idx + 1) else ""
            }
        }
        return null
    }
}