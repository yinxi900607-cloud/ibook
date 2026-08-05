package com.yinxi.edgereader.parser.txt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class TxtIndexCacheTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `round trips a valid index and invalidates it when the source changes`() {
        val source = tempDirectory.resolve("book.txt")
        val cache = tempDirectory.resolve("cache/book.index")
        Files.writeString(source, "第一章 开始\n正文\n第二章 继续\n")
        val index = TxtBookIndexer.build(source, StandardCharsets.UTF_8, 0)

        TxtIndexCache.save(cache, index)
        val restored = TxtIndexCache.load(cache, source, StandardCharsets.UTF_8, 0)

        assertEquals(index, restored)
        assertTrue(Files.size(cache) < Files.size(source) * 10 + 1024)

        Thread.sleep(5)
        Files.writeString(source, "第一章 已修改\n正文")
        assertNull(TxtIndexCache.load(cache, source, StandardCharsets.UTF_8, 0))
    }

    @Test
    fun `rejects a corrupted cache`() {
        val source = tempDirectory.resolve("source.txt").also { Files.writeString(it, "text") }
        val cache = tempDirectory.resolve("broken.index").also { Files.writeString(it, "not an index") }
        assertNull(TxtIndexCache.load(cache, source, StandardCharsets.UTF_8, 0))
    }
}
