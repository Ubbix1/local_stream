package com.localstream.localstream_mobile.storage

import android.net.Uri
import java.io.Closeable
import java.io.InputStream

/**
 * A subtitle track attached to a video source.
 * The track itself is streamed through the same /api/v1/stream/:id endpoint.
 */
data class SubtitleTrack(
    val id: String,
    val name: String,
    val mimeType: String
)

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
     * Opens the underlying resource through the content provider, when applicable.
     */
    val contentUri: Uri? get() = null

    /**
     * Absolute file path for sources backed by a plain file, otherwise null.
     */
    val filePath: String? get() = null

    /**
     * Stable id of the parent folder this source belongs to (null = library root of a tree-less source).
     */
    val folderId: String? get() = null

    /**
     * Human readable relative folder path, e.g. "Movies / Sci-Fi".
     */
    val folderPath: String? get() = null

    /**
     * Subtitle sidecar files found next to this source (videos only).
     */
    val subtitleTracks: List<SubtitleTrack> get() = emptyList()

    /**
     * Hidden sources (e.g. subtitle sidecars) are streamable but excluded from library listings.
     */
    val hidden: Boolean get() = false

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
            "folderId" to folderId,
            "folderPath" to folderPath,
            "available" to exists()
        )
    }

    override fun close() {
        // Default no-op if no persistent handle is held
    }
}