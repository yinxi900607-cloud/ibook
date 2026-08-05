package com.yinxi.edgereader.parser.txt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TxtEncodingDetectorTest {
    @Test
    fun `detects UTF BOM variants`() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "text".toByteArray()
        val utf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "文本".toByteArray(StandardCharsets.UTF_16LE)
        val utf16Be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "文本".toByteArray(StandardCharsets.UTF_16BE)

        assertEquals(StandardCharsets.UTF_8, TxtEncodingDetector.detect(utf8).charset)
        assertEquals(3, TxtEncodingDetector.detect(utf8).bomLength)
        assertEquals(StandardCharsets.UTF_16LE, TxtEncodingDetector.detect(utf16Le).charset)
        assertEquals(StandardCharsets.UTF_16BE, TxtEncodingDetector.detect(utf16Be).charset)
    }

    @Test
    fun `detects UTF8 without BOM`() {
        val result = TxtEncodingDetector.detect("第一章\nHello UTF-8 世界".toByteArray(StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, result.charset)
        assertTrue(result.reliable)
    }

    @Test
    fun `offers every required legacy charset when detection is ambiguous`() {
        val names = TxtEncodingDetector.supportedCharsets().map { it.name().uppercase() }.toSet()
        assertTrue("GB18030" in names)
        assertTrue("GBK" in names)
        assertTrue(names.any { it == "BIG5" })

        val legacyBytes = "第一章 测试文字".toByteArray(Charset.forName("GB18030"))
        val detection = TxtEncodingDetector.detect(legacyBytes)
        assertTrue(detection.candidates.any { it.name().equals("GB18030", ignoreCase = true) })
        assertFalse(detection.charset == StandardCharsets.UTF_8)
    }
}
