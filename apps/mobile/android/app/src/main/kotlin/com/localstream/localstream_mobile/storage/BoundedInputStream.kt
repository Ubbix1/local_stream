package com.localstream.localstream_mobile.storage

import java.io.InputStream

/**
 * An [InputStream] wrapper that reads at most [maxBytes] from the underlying stream.
 * Automatically closes the underlying stream when closed.
 */
open class BoundedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long
) : InputStream() {

    private var bytesRemaining: Long = maxBytes

    override fun read(): Int {
        if (bytesRemaining <= 0) return -1
        val b = delegate.read()
        if (b != -1) {
            bytesRemaining--
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bytesRemaining <= 0) return -1
        val toRead = minOf(len.toLong(), bytesRemaining).toInt()
        val readCount = delegate.read(b, off, toRead)
        if (readCount != -1) {
            bytesRemaining -= readCount
        }
        return readCount
    }

    override fun available(): Int {
        return minOf(delegate.available().toLong(), bytesRemaining).toInt()
    }

    open override fun close() {
        delegate.close()
    }
}
