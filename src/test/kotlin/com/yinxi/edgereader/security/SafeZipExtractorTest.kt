package com.yinxi.edgereader.security

import com.yinxi.edgereader.parser.epub.EpubTestBooks
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SafeZipExtractorTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `rejects zip slip and does not write outside destination`() {
        val archive = tempDirectory.resolve("slip.epub")
        EpubTestBooks.writeZip(archive, mapOf("../escaped.txt" to "bad".toByteArray()))

        assertThrows(UnsafeArchiveException::class.java) {
            SafeZipExtractor().extract(archive, tempDirectory.resolve("cache/book"))
        }
        assertFalse(Files.exists(tempDirectory.resolve("cache/escaped.txt")))
        assertFalse(Files.exists(tempDirectory.resolve("escaped.txt")))
    }

    @Test
    fun `rejects excessive entry count`() {
        val archive = tempDirectory.resolve("entries.epub")
        EpubTestBooks.writeZip(archive, mapOf("one" to byteArrayOf(1), "two" to byteArrayOf(2)))
        val limits = EpubSecurityLimits(maxEntries = 1)
        assertThrows(UnsafeArchiveException::class.java) {
            SafeZipExtractor(limits).extract(archive, tempDirectory.resolve("entry-cache"))
        }
    }

    @Test
    fun `rejects suspicious compression ratio`() {
        val archive = tempDirectory.resolve("ratio.epub")
        EpubTestBooks.writeZip(archive, mapOf("large.txt" to ByteArray(64 * 1024)))
        val limits = EpubSecurityLimits(maxCompressionRatio = 2.0)
        assertThrows(UnsafeArchiveException::class.java) {
            SafeZipExtractor(limits).extract(archive, tempDirectory.resolve("ratio-cache"))
        }
    }

    @Test
    fun `rejects one expanded entry over configured size`() {
        val archive = tempDirectory.resolve("large.epub")
        EpubTestBooks.writeZip(archive, mapOf("large.txt" to ByteArray(32)))
        val limits = EpubSecurityLimits(maxEntryBytes = 16, maxCompressionRatio = 1000.0)
        assertThrows(UnsafeArchiveException::class.java) {
            SafeZipExtractor(limits).extract(archive, tempDirectory.resolve("large-cache"))
        }
    }
}
