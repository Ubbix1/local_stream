package com.localstream.localstream_mobile.storage

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

class ContentUriMediaSource(
    override val id: String,
    val uri: Uri,
    initialDisplayName: String? = null,
    initialMimeType: String? = null,
    private val contentResolver: ContentResolver
) : MediaSource {

    override val sourceKind: String = "shared"
    override val contentUri: Uri get() = uri

    override val displayName: String by lazy {
        initialDisplayName ?: queryDisplayName() ?: "shared_media_${id.take(8)}"
    }

    override val mimeType: String by lazy {
        val detected = initialMimeType ?: contentResolver.getType(uri)
        MimeTypeDetector.detectMimeType(displayName, detected)
    }

    override val mediaType: String by lazy {
        MimeTypeDetector.getMediaType(mimeType)
    }

    override val sizeBytes: Long? by lazy {
        queryLength()
    }

    private fun queryDisplayName(): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryLength(): Long? {
        // Try OpenableColumns.SIZE first
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        val size = cursor.getLong(sizeIdx)
                        if (size > 0) return size
                    }
                }
            }
        } catch (_: Exception) {
            // OpenableColumns.SIZE query failed; fall through to asset descriptor
        }

        // Try openAssetFileDescriptor length
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
            ?: throw FileNotFoundException("Unable to open input stream for shared URI: $uri")
    }

    override fun readRange(start: Long, end: Long): InputStream {
        val totalLength = sizeBytes ?: Long.MAX_VALUE
        val effectiveEnd = minOf(end, totalLength - 1)
        val bytesToRead = (effectiveEnd - start + 1).coerceAtLeast(0L)

        // Try FileChannel seek first
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
            // FileChannel seek unsupported; fall back to stream skip
        }

        // Fallback: standard stream skip
        val rawStream = openInputStream()
        var skipped = 0L
        while (skipped < start) {
            val count = rawStream.skip(start - skipped)
            if (count <= 0) {
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
