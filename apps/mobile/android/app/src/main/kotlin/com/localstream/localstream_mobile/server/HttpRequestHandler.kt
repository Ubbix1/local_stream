package com.localstream.localstream_mobile.server

import com.localstream.localstream_mobile.storage.StorageManager
import com.localstream.localstream_mobile.server.ApiResponseBuilder.PROTOCOL_VERSION
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException

class HttpRequestHandler(
    private val socket: Socket,
    private val storageManager: StorageManager,
    private val stateHolder: ServerStateHolder,
    private val port: Int
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

            // Route dispatch
            when {
                method == "OPTIONS" -> {
                    sendCorsPreflightResponse()
                }

                (method == "GET" || method == "HEAD") && path.startsWith("/api/v1/stream/") -> {
                    val id = path.removePrefix("/api/v1/stream/")
                    handleMediaStream(id, headers["range"], isHeadOnly = (method == "HEAD"))
                }

                method == "GET" && path == "/api/v1/info" -> {
                    val body = ApiResponseBuilder.buildInfoJson(port)
                    sendJsonResponse(200, "OK", body)
                }

                method == "GET" && path == "/api/v1/status" -> {
                    val body = ApiResponseBuilder.buildStatusJson(stateHolder.buildSnapshot())
                    sendJsonResponse(200, "OK", body)
                }

                method == "GET" && path == "/api/v1/files" -> {
                    val sources = storageManager.getAllSources()
                    val body = ApiResponseBuilder.buildFilesJson(sources)
                    sendJsonResponse(200, "OK", body)
                }

                method == "GET" && path.startsWith("/api/v1/files/") -> {
                    val id = path.removePrefix("/api/v1/files/")
                    val source = storageManager.getSource(id)
                    if (source == null) {
                        sendErrorResponse(404, "MEDIA_NOT_FOUND", "Media item not found: $id")
                    } else {
                        val body = ApiResponseBuilder.buildFileDetailJson(source)
                        sendJsonResponse(200, "OK", body)
                    }
                }

                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val sources = storageManager.getAllSources()
                    val html = ApiResponseBuilder.buildWebIndexHtml(sources)
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

    private fun sendJsonResponse(statusCode: Int, statusText: String, jsonBody: String) {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("X-LocalStream-Protocol-Version: $PROTOCOL_VERSION\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Range, Content-Type\r\n")
            append("Access-Control-Expose-Headers: X-LocalStream-Protocol-Version\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = socket.getOutputStream()
        out.write(response.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
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
            append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Range, Content-Type, Accept\r\n")
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
        404 -> "Not Found"
        410 -> "Gone"
        416 -> "Range Not Satisfiable"
        431 -> "Request Header Fields Too Large"
        500 -> "Internal Server Error"
        else -> "Error"
    }
}
