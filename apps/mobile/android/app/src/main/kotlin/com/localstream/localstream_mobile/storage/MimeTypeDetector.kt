package com.localstream.localstream_mobile.storage

import android.webkit.MimeTypeMap
import java.util.Locale

object MimeTypeDetector {

    private val EXTENSION_TO_MIME = mapOf(
        // Video
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "ts" to "video/mp2t",
        "3gp" to "video/3gpp",

        // Audio
        "mp3" to "audio/mpeg",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "opus" to "audio/opus",

        // Images
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",

        // Subtitles
        "srt" to "text/plain",
        "vtt" to "text/vtt"
    )

    fun detectMimeType(fileName: String?, fallbackMime: String? = null): String {
        if (!fallbackMime.isNullOrBlank() && fallbackMime != "application/octet-stream" && fallbackMime != "*/*") {
            return fallbackMime
        }

        if (fileName.isNullOrBlank()) {
            return "application/octet-stream"
        }

        val ext = getExtension(fileName).lowercase(Locale.US)
        EXTENSION_TO_MIME[ext]?.let { return it }

        val webkitMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!webkitMime.isNullOrBlank()) {
            return webkitMime
        }

        return fallbackMime ?: "application/octet-stream"
    }

    fun getMediaType(mimeType: String): String {
        return when {
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            mimeType.startsWith("image/") -> "image"
            else -> "other"
        }
    }

    private fun getExtension(name: String): String {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < name.length - 1) {
            name.substring(lastDot + 1)
        } else {
            ""
        }
    }
}
