package com.localstream.localstream_mobile.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Lazily extracts media metadata (duration) and generates cached thumbnails.
 * Keys are derived from the stable resource location (content uri / file path),
 * so cached artwork survives the per-scan source id regeneration.
 */
class MediaMetadataCache(private val context: Context) {

    private val durationCache = ConcurrentHashMap<String, Long>()

    private val thumbsRoot: File =
        File(context.cacheDir, "thumbs").apply { mkdirs() }

    /** Cached thumbnail files keyed by stable location key. */
    private val thumbCache = ConcurrentHashMap<String, File>()

    fun clear() {
        durationCache.clear()
        thumbCache.clear()
        thumbsRoot.listFiles()?.forEach { it.delete() }
    }

    fun removeLocation(location: String?) {
        val key = stableKey(location)
        if (key == null) return
        durationCache.remove(key)
        thumbCache.remove(key)?.delete()
    }

    /**
     * Duration in milliseconds for the source, or null when unknown
     * (images) or not extractable.
     */
    fun durationMs(source: MediaSource): Long? {
        val key = stableKey(source.contentUri?.toString() ?: source.filePath) ?: return null
        durationCache[key]?.let { return if (it > 0) it else null }
        val ms = queryDurationMs(source)
        durationCache[key] = ms ?: 0L
        return ms
    }

    private fun queryDurationMs(source: MediaSource): Long? {
        if (source.mediaType != "video" && source.mediaType != "audio") return null
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                if (source.contentUri != null) {
                    retriever.setDataSource(context, source.contentUri)
                } else if (source.filePath != null) {
                    retriever.setDataSource(source.filePath)
                } else {
                    return null
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns a JPEG thumbnail file for the source, generating + caching it on first use.
     * Returns null when no artwork can be produced.
     */
    fun thumbnailFile(source: MediaSource, maxDim: Int = 880): File? {
        val key = stableKey(source.contentUri?.toString() ?: source.filePath) ?: return null
        thumbCache[key]?.takeIf { it.exists() }?.let { return it }

        val file = generateThumbnail(source, maxDim) ?: return null
        thumbCache[key] = file
        return file
    }

    /**
     * Fills the duration cache for a list of sources (background warm-up).
     */
    fun warmDurations(sources: List<MediaSource>) {
        for (source in sources) {
            if (source.mediaType == "video" || source.mediaType == "audio") {
                try {
                    durationMs(source)
                } catch (_: Exception) {
                    // Metadata extraction must never break the warm-up sweep
                }
            }
        }
    }

    private fun generateThumbnail(source: MediaSource, maxDim: Int): File? {
        return when (source.mediaType) {
            "video" -> extractVideoFrame(source, maxDim)
            "audio" -> extractEmbeddedArtwork(source, maxDim)
            "image" -> downscaleImage(source, maxDim)
            else -> null
        }
    }

    private fun extractVideoFrame(source: MediaSource, maxDim: Int): File? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                if (source.contentUri != null) {
                    retriever.setDataSource(context, source.contentUri)
                } else if (source.filePath != null) {
                    retriever.setDataSource(source.filePath)
                } else {
                    return null
                }
                val frame = retriever.getFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.getFrameAtTime(0)
                writeJpeg(frame, keyOf(source), maxDim)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractEmbeddedArtwork(source: MediaSource, maxDim: Int): File? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                if (source.contentUri != null) {
                    retriever.setDataSource(context, source.contentUri)
                } else if (source.filePath != null) {
                    retriever.setDataSource(source.filePath)
                } else {
                    return null
                }
                val bytes = retriever.embeddedPicture
                val raw = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                writeJpeg(raw, keyOf(source), maxDim)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downscaleImage(source: MediaSource, maxDim: Int): File? {
        val key = keyOf(source)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            source.openInputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sample = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDim * 2)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = source.openInputStream().use { BitmapFactory.decodeStream(it, null, opts) }
            writeJpeg(bitmap, key, maxDim)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeJpeg(bitmap: Bitmap?, key: String, maxDim: Int): File? {
        if (bitmap == null) return null
        val scaled = scaleDown(bitmap, maxDim)
        if (scaled !== bitmap) bitmap.recycle()
        val file = File(thumbsRoot, key)
        return try {
            FileOutputStream(file).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)) {
                    null
                } else {
                    file
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            scaled.recycle()
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun computeSampleSize(width: Int, height: Int, targetMax: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / (sample * 2) >= targetMax && h / (sample * 2) >= targetMax) {
            sample *= 2
        }
        return sample
    }

    private fun stableKey(location: String?): String? {
        if (location.isNullOrBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(location.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return digest
    }

    private fun keyOf(source: MediaSource): String {
        return stableKey(source.contentUri?.toString() ?: source.filePath)
            ?: source.id
    }
}