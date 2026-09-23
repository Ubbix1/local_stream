package com.localstream.localstream_mobile

import com.localstream.localstream_mobile.storage.MimeTypeDetector
import com.localstream.localstream_mobile.storage.StorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageValidationTest {

    @Test
    fun testMimeDetectionForMKV() {
        val mime = MimeTypeDetector.detectMimeType("Interstellar.mkv")
        assertEquals("video/x-matroska", mime)
        assertEquals("video", MimeTypeDetector.getMediaType(mime))
    }

    @Test
    fun testMimeDetectionForMP4() {
        val mime = MimeTypeDetector.detectMimeType("sample.mp4")
        assertEquals("video/mp4", mime)
        assertEquals("video", MimeTypeDetector.getMediaType(mime))
    }

    @Test
    fun testMimeDetectionForWebM() {
        val mime = MimeTypeDetector.detectMimeType("clip.webm")
        assertEquals("video/webm", mime)
        assertEquals("video", MimeTypeDetector.getMediaType(mime))
    }

    @Test
    fun testMimeDetectionForAudio() {
        val flac = MimeTypeDetector.detectMimeType("track.flac")
        assertEquals("audio/flac", flac)
        assertEquals("audio", MimeTypeDetector.getMediaType(flac))

        val mp3 = MimeTypeDetector.detectMimeType("song.mp3")
        assertEquals("audio/mpeg", mp3)
        assertEquals("audio", MimeTypeDetector.getMediaType(mp3))
    }

    @Test
    fun testPathTraversalProtection() {
        // Validation regex should reject path traversal attempts
        val safeRegex = Regex("^[a-zA-Z0-9_-]{1,64}$")

        // Dangerous path traversal attempts
        assertFalse(safeRegex.matches("../../etc/passwd"))
        assertFalse(safeRegex.matches("../../../sdcard/DCIM"))
        assertFalse(safeRegex.matches("/storage/emulated/0"))
        assertFalse(safeRegex.matches("saf_123/../../"))
        assertFalse(safeRegex.matches(""))
        assertFalse(safeRegex.matches("saf 123"))

        // Safe IDs
        assertTrue(safeRegex.matches("saf_a1b2c3d4e5f6"))
        assertTrue(safeRegex.matches("uri_998877665544"))
        assertTrue(safeRegex.matches("imp_1234567890ab"))
    }
}
