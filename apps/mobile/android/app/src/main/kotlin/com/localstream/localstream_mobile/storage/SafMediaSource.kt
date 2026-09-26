package com.localstream.localstream_mobile.storage

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

class SafMediaSource(
    override val id: String,
    val uri: Uri,
    override val displayName: String,
    override val mimeType: String,
    private val contentResolver: ContentResolver,
    sizeHint: Long? = null,
    override val folderId: String? = null,
    override val folderPath: String? = null,
    override val hidden: Boolean = false
) : MediaSource {

    override val sourceKind: String = "saf"
    override val mediaType: String = MimeTypeDetector.getMediaType(mimeType)
    override val contentUri: Uri get() = uri

    override val sizeBytes: Long? by lazy {
        sizeHint?.takeIf { it > 0 } ?: queryLength()
    }

    private fun queryLength(): Long? {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) afd.length else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun exists(): Boolean {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun openInputStream(): InputStream {
        return contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Unable to open input stream for $uri")
    }

    override fun readRange(start: Long, end: Long): InputStream {
        val totalLength = sizeBytes ?: Long.MAX_VALUE
        val effectiveEnd = minOf(end, totalLength - 1)
        val bytesToRead = (effectiveEnd - start + 1).coerceAtLeast(0L)

        // Try FileChannel seek first for high-performance direct I/O
        try {
            val afd = contentResolver.openAssetFileDescriptor(uri, "r")
            if (afd != null) {
                val fis = FileInputStream(afd.fileDescriptor)
                val channel = fis.channel
                val actualStart = afd.startOffset + start
                channel.position(actualStart)
                return AssetFileDescriptorBoundedInputStream(fis, afd, bytesToRead)
            }
        } catch (_: Exception) {
            // Channel seek unsupported; fall back to stream skip
        }

        // Fallback: standard stream with robust skip loop
        val rawStream = openInputStream()
        var skipped = 0L
        while (skipped < start) {
            val count = rawStream.skip(start - skipped)
            if (count <= 0) {
                // Read and discard one byte if skip returned 0
                if (rawStream.read() == -1) break
                skipped += 1
            } else {
                skipped += count
            }
        }

        return BoundedInputStream(rawStream, bytesToRead)
    }

    private class AssetFileDescriptorBoundedInputStream(
        delegate: InputStream,
        private val afd: AssetFileDescriptor,
        maxBytes: Long
    ) : BoundedInputStream(delegate, maxBytes) {
        override fun close() {
            try {
                super.close()
            } finally {
                afd.close()
            }
        }
    }
}