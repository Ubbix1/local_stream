package com.localstream.localstream_mobile.server

import com.localstream.localstream_mobile.storage.MediaSource
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.net.SocketException
import java.nio.charset.StandardCharsets

object StreamingResponseWriter {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk size

    fun writeMediaResponse(
        outputStream: OutputStream,
        source: MediaSource,
        rangeResult: RangeRequestParser.RangeResult,
        stateHolder: ServerStateHolder,
        isHeadOnly: Boolean = false
    ) {
        val bufferedOut = BufferedOutputStream(outputStream, BUFFER_SIZE)
        val totalSize = source.sizeBytes ?: -1L

        when (rangeResult) {
            is RangeRequestParser.RangeResult.Invalid -> {
                writeInvalidRangeResponse(bufferedOut, totalSize)
            }
            is RangeRequestParser.RangeResult.Partial -> {
                writePartialContentResponse(
                    bufferedOut,
                    source,
                    rangeResult.start,
                    rangeResult.end,
                    totalSize,
                    stateHolder,
                    isHeadOnly
                )
            }
            is RangeRequestParser.RangeResult.Full -> {
                writeFullContentResponse(
                    bufferedOut,
                    source,
                    totalSize,
                    stateHolder,
                    isHeadOnly
                )
            }
        }
    }

    private fun writeFullContentResponse(
        out: BufferedOutputStream,
        source: MediaSource,
        totalSize: Long,
        stateHolder: ServerStateHolder,
        isHeadOnly: Boolean
    ) {
        val headers = StringBuilder()
        headers.append("HTTP/1.1 200 OK\r\n")
        headers.append("Content-Type: ${source.mimeType}\r\n")
        if (totalSize >= 0) {
            headers.append("Content-Length: $totalSize\r\n")
        }
        appendDownloadName(headers, source.displayName)
        headers.append("Accept-Ranges: bytes\r\n")
        headers.append("Access-Control-Allow-Origin: *\r\n")
        headers.append("Access-Control-Allow-Headers: Range, Content-Type, Accept\r\n")
        headers.append("Access-Control-Expose-Headers: Content-Range, Content-Length, Accept-Ranges, X-LocalStream-Protocol-Version\r\n")
        headers.append("X-LocalStream-Protocol-Version: 1\r\n")
        headers.append("Connection: close\r\n")
        headers.append("\r\n")

        out.write(headers.toString().toByteArray(Charsets.US_ASCII))
        out.flush()

        if (isHeadOnly) return

        stateHolder.incrementStreams()
        var stream: InputStream? = null
        try {
            stream = source.openInputStream()
            pipeStream(stream, out, stateHolder)
        } catch (_: SocketException) {
            // Client disconnected mid-stream; cleanup in finally
        } catch (_: Exception) {
            // Stream error isolated to this response only
        } finally {
            stateHolder.decrementStreams()
            try {
                stream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun writePartialContentResponse(
        out: BufferedOutputStream,
        source: MediaSource,
        start: Long,
        end: Long,
        totalSize: Long,
        stateHolder: ServerStateHolder,
        isHeadOnly: Boolean
    ) {
        val contentLength = end - start + 1
        val totalSizeStr = if (totalSize >= 0) totalSize.toString() else "*"

        val headers = StringBuilder()
        headers.append("HTTP/1.1 206 Partial Content\r\n")
        headers.append("Content-Type: ${source.mimeType}\r\n")
        headers.append("Content-Length: $contentLength\r\n")
        headers.append("Content-Range: bytes $start-$end/$totalSizeStr\r\n")
        appendDownloadName(headers, source.displayName)
        headers.append("Accept-Ranges: bytes\r\n")
        headers.append("Access-Control-Allow-Origin: *\r\n")
        headers.append("Access-Control-Allow-Headers: Range, Content-Type, Accept\r\n")
        headers.append("Access-Control-Expose-Headers: Content-Range, Content-Length, Accept-Ranges, X-LocalStream-Protocol-Version\r\n")
        headers.append("X-LocalStream-Protocol-Version: 1\r\n")
        headers.append("Connection: close\r\n")
        headers.append("\r\n")

        out.write(headers.toString().toByteArray(Charsets.US_ASCII))
        out.flush()

        if (isHeadOnly) return

        stateHolder.incrementStreams()
        var stream: InputStream? = null
        try {
            stream = source.readRange(start, end)
            pipeStream(stream, out, stateHolder)
        } catch (_: SocketException) {
            // Client disconnected mid-stream; cleanup in finally
        } catch (_: Exception) {
            // Range stream error isolated to this response only
        } finally {
            stateHolder.decrementStreams()
            try {
                stream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun writeInvalidRangeResponse(out: BufferedOutputStream, totalSize: Long) {
        val totalStr = if (totalSize >= 0) totalSize.toString() else "*"
        val body = ApiResponseBuilder.buildErrorJson("INVALID_RANGE", "The requested byte range is unsatisfiable")
        val bodyBytes = body.toByteArray(Charsets.UTF_8)

        val headers = StringBuilder()
        headers.append("HTTP/1.1 416 Range Not Satisfiable\r\n")
        headers.append("Content-Type: application/json; charset=utf-8\r\n")
        headers.append("Content-Range: bytes */$totalStr\r\n")
        headers.append("Content-Length: ${bodyBytes.size}\r\n")
        headers.append("Access-Control-Allow-Origin: *\r\n")
        headers.append("X-LocalStream-Protocol-Version: 1\r\n")
        headers.append("Connection: close\r\n")
        headers.append("\r\n")

        out.write(headers.toString().toByteArray(Charsets.US_ASCII))
        out.write(bodyBytes)
        out.flush()
    }

    private fun appendDownloadName(headers: StringBuilder, displayName: String) {
        val fallback = displayName
            .replace(Regex("[\\\"\\r\\n]"), "_")
            .replace(Regex("[^\\x20-\\x7E]"), "_")
        val encoded = URLEncoder.encode(displayName, StandardCharsets.UTF_8.name()).replace("+", "%20")
        headers.append("Content-Disposition: inline; filename=\"$fallback\"; filename*=UTF-8''$encoded\r\n")
    }

    private fun pipeStream(input: InputStream, out: BufferedOutputStream, stateHolder: ServerStateHolder) {
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
            stateHolder.addBytesTransferred(read.toLong())
        }
        out.flush()
    }
}
