package com.localstream.localstream_mobile.server

/**
 * Parses the HTTP `Range: bytes=<start>-<end>` header.
 *
 * Handles:
 *  - Full range (no header)          → [RangeResult.Full]
 *  - `bytes=start-end`               → [RangeResult.Partial]
 *  - `bytes=start-`  (open end)      → [RangeResult.Partial] with end = fileSize-1
 *  - `bytes=-suffix` (suffix range)  → [RangeResult.Partial] tail of file
 *  - Invalid / out-of-bounds range   → [RangeResult.Invalid]
 *
 * Invalid ranges NEVER crash the server — they return [RangeResult.Invalid]
 * so the caller can respond with HTTP 416.
 */
object RangeRequestParser {

    sealed class RangeResult {
        /** Serve the entire file. */
        object Full : RangeResult()

        /** Serve bytes [start]..[end] inclusive; end < fileSize. */
        data class Partial(val start: Long, val end: Long) : RangeResult() {
            val length: Long get() = end - start + 1
        }

        /** Range header was present but unsatisfiable. */
        data class Invalid(val reason: String) : RangeResult()
    }

    /**
     * Parse a Range header value.
     *
     * @param rangeHeader The raw value of the `Range` request header, or null if absent.
     * @param fileSize    Total size of the resource in bytes; required to validate/clamp ranges.
     * @return            A [RangeResult] describing how to serve the response.
     */
    fun parse(rangeHeader: String?, fileSize: Long): RangeResult {
        if (rangeHeader.isNullOrBlank()) return RangeResult.Full
        if (fileSize <= 0) return RangeResult.Invalid("Resource has no content (size=$fileSize)")

        val header = rangeHeader.trim()
        if (!header.startsWith("bytes=", ignoreCase = true)) {
            return RangeResult.Invalid("Only 'bytes' range unit is supported")
        }

        val spec = header.substringAfter('=').trim()

        // Multiple ranges (e.g. "bytes=0-100,200-300") — not supported; serve full
        if (spec.contains(',')) {
            return RangeResult.Full
        }

        return try {
            when {
                // Suffix range: bytes=-N
                spec.startsWith('-') -> {
                    val suffixLen = spec.substring(1).toLong()
                    if (suffixLen <= 0) return RangeResult.Invalid("Suffix length must be > 0")
                    val start = maxOf(0L, fileSize - suffixLen)
                    RangeResult.Partial(start, fileSize - 1)
                }

                // Open or bounded range: bytes=N- or bytes=N-M
                else -> {
                    val parts = spec.split('-')
                    if (parts.size != 2) return RangeResult.Invalid("Malformed range spec: $spec")

                    val start = parts[0].trim().toLong()
                    val endStr = parts[1].trim()
                    val end = if (endStr.isEmpty()) fileSize - 1 else endStr.toLong()

                    when {
                        start < 0 -> RangeResult.Invalid("Range start < 0: $start")
                        end < start -> RangeResult.Invalid("Range end ($end) < start ($start)")
                        start >= fileSize -> RangeResult.Invalid("Range start ($start) >= fileSize ($fileSize)")
                        else -> RangeResult.Partial(start, minOf(end, fileSize - 1))
                    }
                }
            }
        } catch (_: NumberFormatException) {
            return RangeResult.Invalid("Non-numeric range value")
        } catch (_: Exception) {
            return RangeResult.Invalid("Internal range parse error")
        }
    }
}
