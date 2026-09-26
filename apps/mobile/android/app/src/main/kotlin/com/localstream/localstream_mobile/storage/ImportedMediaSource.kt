package com.localstream.localstream_mobile.storage

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.RandomAccessFile

class ImportedMediaSource(
    override val id: String,
    val file: File,
    override val displayName: String = file.name,
    override val mimeType: String = MimeTypeDetector.detectMimeType(file.name)
) : MediaSource {

    override val sourceKind: String = "imported"
    override val mediaType: String = MimeTypeDetector.getMediaType(mimeType)
    override val filePath: String get() = file.absolutePath
    override val hidden: Boolean get() = MimeTypeDetector.isSubtitle(mimeType, displayName)
    override val sizeBytes: Long get() = if (file.exists()) file.length() else 0L

    override fun exists(): Boolean = file.exists() && file.canRead()

    override fun openInputStream(): InputStream {
        if (!exists()) throw FileNotFoundException("Imported file does not exist: ${file.absolutePath}")
        return FileInputStream(file)
    }

    override fun readRange(start: Long, end: Long): InputStream {
        if (!exists()) throw FileNotFoundException("Imported file does not exist: ${file.absolutePath}")
        val effectiveEnd = minOf(end, file.length() - 1)
        val bytesToRead = (effectiveEnd - start + 1).coerceAtLeast(0L)

        val raf = RandomAccessFile(file, "r")
        raf.seek(start)
        val fis = FileInputStream(raf.fd)
        return object : BoundedInputStream(fis, bytesToRead) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    try {
                        raf.close()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
