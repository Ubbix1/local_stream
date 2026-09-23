package com.localstream.localstream_mobile.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ImportProgress(
    val name: String,
    val bytes: Long,
    val totalBytes: Long,
    val done: Boolean = false,
    val error: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to "import_progress",
        "name" to name,
        "bytes" to bytes,
        "totalBytes" to totalBytes,
        "done" to done,
        "error" to error
    )
}

class StorageManager(private val context: Context) {

    private val contentResolver = context.contentResolver

    // Thread-safe map of media sources keyed by safe ID
    private val sources = ConcurrentHashMap<String, MediaSource>()

    // Track folder URIs and the IDs belonging to them for clean revocation
    private val folderToSourceIds = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile
    var onLibraryChanged: (() -> Unit)? = null

    @Volatile
    var onImportProgress: ((ImportProgress) -> Unit)? = null

    @Volatile
    var currentImportProgress: ImportProgress? = null
        private set

    companion object {
        private const val PREFS_NAME = "localstream_storage"
        private const val KEY_SAF_TREES = "saf_tree_uris"

        // Disallow path traversal characters or invalid patterns
        private val SAFE_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
    }

    /**
     * Validates that an ID is syntactically safe and prevents path traversal.
     */
    fun isValidId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return SAFE_ID_REGEX.matches(id)
    }

    /**
     * Looks up a registered media source by ID.
     */
    fun getSource(id: String): MediaSource? {
        if (!isValidId(id)) return null
        return sources[id]
    }

    /**
     * Returns a snapshot list of all currently registered sources.
     */
    fun getAllSources(): List<MediaSource> {
        return sources.values.toList()
    }

    /**
     * Adds and persists a user-selected SAF directory tree URI.
     * Scans the folder recursively for media files.
     */
    suspend fun addSafTreeUri(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val uriStr = treeUri.toString()
        try {
            // Persist permission so the server keeps access across reboots/UI closure
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: Exception) {
            // Folder still usable while app holds the grant
        }

        savePersistedTreeUri(uriStr)

        val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (folderDoc == null || !folderDoc.canRead()) {
            return@withContext 0
        }

        val addedIds = mutableSetOf<String>()
        scanDocumentFile(folderDoc, addedIds)

        folderToSourceIds[uriStr] = addedIds
        onLibraryChanged?.invoke()
        addedIds.size
    }

    private fun scanDocumentFile(dir: DocumentFile, collector: MutableSet<String>) {
        val files = try {
            dir.listFiles()
        } catch (_: Exception) {
            emptyArray()
        }

        for (file in files) {
            if (file.isDirectory) {
                scanDocumentFile(file, collector)
            } else if (file.isFile) {
                val mimeType = MimeTypeDetector.detectMimeType(file.name, file.type)
                val mediaType = MimeTypeDetector.getMediaType(mimeType)
                // Filter to media types or common media containers
                if (mediaType == "video" || mediaType == "audio" || mediaType == "image") {
                    val id = "saf_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    val source = SafMediaSource(
                        id = id,
                        uri = file.uri,
                        displayName = file.name ?: "media_$id",
                        mimeType = mimeType,
                        contentResolver = contentResolver,
                        sizeHint = file.length()
                    )
                    sources[id] = source
                    collector.add(id)
                }
            }
        }
    }

    /**
     * Registers a shared content URI (STREAM_REFERENCE mode).
     */
    fun addSharedUri(uri: Uri, displayName: String? = null, mimeType: String? = null): MediaSource {
        val id = "uri_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val source = ContentUriMediaSource(
            id = id,
            uri = uri,
            initialDisplayName = displayName,
            initialMimeType = mimeType,
            contentResolver = contentResolver
        )
        sources[id] = source
        onLibraryChanged?.invoke()
        return source
    }

    /**
     * Imports a shared URI by copying it into the app's private storage (IMPORT_TO_LIBRARY mode).
     */
    suspend fun importUri(uri: Uri, targetName: String? = null): MediaSource = withContext(Dispatchers.IO) {
        val id = "imp_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val metadata = queryUriMetadata(uri)
        val name = uniqueImportName(sanitizeFileName(targetName ?: metadata.displayName ?: fallbackFileName(uri, id)))
        val storageDir = File(context.filesDir, "imported_media").apply { mkdirs() }
        val targetFile = File(storageDir, name)
        val totalBytes = metadata.sizeBytes ?: -1L

        emitImportProgress(ImportProgress(name = name, bytes = 0L, totalBytes = totalBytes))
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    var lastEmitAt = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read.toLong()
                        val now = System.currentTimeMillis()
                        if (now - lastEmitAt >= 250 || copied == totalBytes) {
                            emitImportProgress(ImportProgress(name = name, bytes = copied, totalBytes = totalBytes))
                            lastEmitAt = now
                        }
                    }
                    output.flush()
                    emitImportProgress(ImportProgress(name = name, bytes = copied, totalBytes = totalBytes, done = true))
                }
            } ?: throw IllegalStateException("Could not open input stream to import $uri")
        } catch (e: Exception) {
            emitImportProgress(ImportProgress(name = name, bytes = targetFile.takeIf { it.exists() }?.length() ?: 0L, totalBytes = totalBytes, error = true))
            targetFile.delete()
            throw e
        }

        val source = ImportedMediaSource(id = id, file = targetFile, displayName = name)
        sources[id] = source
        onLibraryChanged?.invoke()
        source
    }

    /** Restores imported files that already exist in the app's private storage. */
    suspend fun reloadPersistedImports() = withContext(Dispatchers.IO) {
        val storageDir = File(context.filesDir, "imported_media")
        val files = storageDir.listFiles()?.filter { it.isFile && it.canRead() } ?: emptyList()
        for (file in files) {
            val mimeType = MimeTypeDetector.detectMimeType(file.name)
            val mediaType = MimeTypeDetector.getMediaType(mimeType)
            if (mediaType != "video" && mediaType != "audio" && mediaType != "image") continue

            val id = "imp_" + UUID.randomUUID().toString().replace("-", "").take(16)
            sources[id] = ImportedMediaSource(
                id = id,
                file = file,
                displayName = file.name,
                mimeType = mimeType
            )
        }
        onLibraryChanged?.invoke()
    }

    private data class UriMetadata(
        val displayName: String?,
        val sizeBytes: Long?
    )

    private fun queryUriMetadata(uri: Uri): UriMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null

        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        val size = cursor.getLong(sizeIdx)
                        if (size > 0) sizeBytes = size
                    }
                }
            }
        } catch (_: Exception) {
            // Metadata query failure is non-fatal
        }

        if (sizeBytes == null) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) sizeBytes = afd.length
                }
            } catch (_: Exception) {
                // Length probe failure is non-fatal
            }
        }

        return UriMetadata(displayName = displayName, sizeBytes = sizeBytes)
    }

    private fun fallbackFileName(uri: Uri, id: String): String {
        val lastSegment = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
        return lastSegment ?: "imported_${id}.media"
    }

    private fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
        return cleaned.takeIf { it.isNotBlank() } ?: "imported_media"
    }

    private fun uniqueImportName(baseName: String): String {
        val storageDir = File(context.filesDir, "imported_media")
        val dotIndex = baseName.lastIndexOf('.')
        val base = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
        val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""

        var candidate = baseName
        var index = 1
        while (File(storageDir, candidate).exists()) {
            candidate = String.format(Locale.US, "%s (%d)%s", base, index, ext)
            index++
        }
        return candidate
    }

    private fun emitImportProgress(progress: ImportProgress) {
        currentImportProgress = if (progress.done || progress.error) null else progress
        onImportProgress?.invoke(progress)
    }

    /**
     * Removes a source by ID and closes it.
     */
    fun removeSource(id: String): Boolean {
        val removed = sources.remove(id)
        if (removed != null) {
            try {
                removed.close()
            } catch (_: Exception) {
                // Closing a source must never break library removal
            }
            onLibraryChanged?.invoke()
            return true
        }
        return false
    }

    /**
     * Reloads previously persisted SAF folder trees on app start.
     */
    suspend fun reloadPersistedTrees() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUris = prefs.getStringSet(KEY_SAF_TREES, emptySet()) ?: emptySet()

        for (uriStr in savedUris) {
            try {
                val uri = Uri.parse(uriStr)
                val folderDoc = DocumentFile.fromTreeUri(context, uri)
                if (folderDoc != null && folderDoc.canRead()) {
                    val addedIds = mutableSetOf<String>()
                    scanDocumentFile(folderDoc, addedIds)
                    folderToSourceIds[uriStr] = addedIds
                }
            } catch (_: Exception) {
                // A broken persisted tree must not block restoring the others
            }
        }
        onLibraryChanged?.invoke()
    }

    private fun savePersistedTreeUri(uriStr: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_SAF_TREES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(uriStr)
        prefs.edit().putStringSet(KEY_SAF_TREES, current).apply()
    }

    /**
     * Clears all sources and closes resources.
     */
    fun clearAll() {
        for ((_, source) in sources) {
            try {
                source.close()
            } catch (_: Exception) {}
        }
        sources.clear()
        folderToSourceIds.clear()
        onLibraryChanged?.invoke()
    }
}
