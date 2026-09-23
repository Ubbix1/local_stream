package com.localstream.localstream_mobile

import com.localstream.localstream_mobile.server.RangeRequestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeRequestParserTest {

    private val sampleFileSize = 1000000L // 1,000,000 bytes

    @Test
    fun testNullOrEmptyRangeReturnsFull() {
        val r1 = RangeRequestParser.parse(null, sampleFileSize)
        assertTrue(r1 is RangeRequestParser.RangeResult.Full)

        val r2 = RangeRequestParser.parse("", sampleFileSize)
        assertTrue(r2 is RangeRequestParser.RangeResult.Full)

        val r3 = RangeRequestParser.parse("   ", sampleFileSize)
        assertTrue(r3 is RangeRequestParser.RangeResult.Full)
    }

    @Test
    fun testValidBoundedRange() {
        val result = RangeRequestParser.parse("bytes=0-499", sampleFileSize)
        assertTrue(result is RangeRequestParser.RangeResult.Partial)
        val partial = result as RangeRequestParser.RangeResult.Partial
        assertEquals(0L, partial.start)
        assertEquals(499L, partial.end)
        assertEquals(500L, partial.length)
    }

    @Test
    fun testValidOpenEndedRange() {
        val result = RangeRequestParser.parse("bytes=5000-", sampleFileSize)
        assertTrue(result is RangeRequestParser.RangeResult.Partial)
        val partial = result as RangeRequestParser.RangeResult.Partial
        assertEquals(5000L, partial.start)
        assertEquals(sampleFileSize - 1, partial.end)
        assertEquals(sampleFileSize - 5000, partial.length)
    }

    @Test
    fun testValidSuffixRange() {
        val result = RangeRequestParser.parse("bytes=-500", sampleFileSize)
        assertTrue(result is RangeRequestParser.RangeResult.Partial)
        val partial = result as RangeRequestParser.RangeResult.Partial
        assertEquals(sampleFileSize - 500, partial.start)
        assertEquals(sampleFileSize - 1, partial.end)
        assertEquals(500L, partial.length)
    }

    @Test
    fun testClampedEndRange() {
        // When client requests beyond file size, end is clamped to fileSize - 1
        val result = RangeRequestParser.parse("bytes=0-2000000", sampleFileSize)
        assertTrue(result is RangeRequestParser.RangeResult.Partial)
        val partial = result as RangeRequestParser.RangeResult.Partial
        assertEquals(0L, partial.start)
        assertEquals(sampleFileSize - 1, partial.end)
    }

    @Test
    fun testInvalidStartBeyondFileSize() {
        val result = RangeRequestParser.parse("bytes=1500000-", sampleFileSize)
        assertTrue(result is RangeRequestParser.RangeResult.Invalid)
    }

    @Test
    fun testInvalidStartGreaterThanEnd() {
        val result = RangeRequestParser.parse("bytes=500-200", sampleFileSize)
        assertTrue(result is RangeRequestParser.RangeResult.Invalid)
    }

    @Test
    fun testInvalidUnit() {
        val result = RangeRequestParser.parse("seconds=0-10", sampleFileSize)
        assertTrue(result is RangeRequestParser.RangeResult.Invalid)
    }

    @Test
    fun testMalformedRangeDoesNotCrash() {
        val r1 = RangeRequestParser.parse("bytes=abc-def", sampleFileSize)
        assertTrue(r1 is RangeRequestParser.RangeResult.Invalid)

        val r2 = RangeRequestParser.parse("bytes=-", sampleFileSize)
        assertTrue(r2 is RangeRequestParser.RangeResult.Invalid)

        val r3 = RangeRequestParser.parse("bytes=---", sampleFileSize)
        assertTrue(r3 is RangeRequestParser.RangeResult.Invalid)
    }
}
