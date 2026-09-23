package com.localstream.localstream_mobile.storage

import java.io.Closeable
import java.io.InputStream

/**
 * Common abstraction for all media sources (SAF documents, shared content URIs, app-managed local files).
 * The HTTP server interacts strictly with this interface.
 */
interface MediaSource : Closeable {
    val id: String
    val displayName: String
    val mimeType: String
    val sizeBytes: Long?
    val mediaType: String
    val sourceKind: String // "saf", "shared", "imported"

    /**
     * Checks if the underlying file/resource still exists and is accessible.
     */
    fun exists(): Boolean

    /**
     * Opens a stream from the beginning of the media resource.
     */
    fun openInputStream(): InputStream

    /**
     * Opens an input stream positioned at byte offset [start] and bounded up to byte offset [end] inclusive.
     */
    fun readRange(start: Long, end: Long): InputStream

    /**
     * Serializes media metadata for JSON response (/api/v1/files).
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to displayName,
            "mimeType" to mimeType,
            "size" to (sizeBytes ?: -1L),
            "type" to mediaType,
            "source" to sourceKind,
            "available" to exists()
        )
    }

    override fun close() {
        // Default no-op if no persistent handle is held
    }
}
