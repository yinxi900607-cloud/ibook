package com.yinxi.edgereader.parser.txt

import com.yinxi.edgereader.model.ReadingLocator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class TxtBookIndexerTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `recognizes Chinese English and numeric chapter styles`() {
        val file = write("chapters.txt", """
            序言
            第一章 开始
            正文
            Chapter IV Return
            正文
            12、最后一节
        """.trimIndent(), StandardCharsets.UTF_8)

        val index = TxtBookIndexer.build(file, StandardCharsets.UTF_8, 0)

        assertEquals(listOf("第一章 开始", "Chapter IV Return", "12、最后一节"), index.chapters.map { it.title })
        val firstOffset = (index.chapters.first().locator as ReadingLocator.TextLocator).characterOffset
        assertEquals("第一章 开始", RandomAccessTextSource(file, index).read(firstOffset, 6).text)
    }

    @Test
    fun `handles a long line without retaining it as a chapter`() {
        val longLine = "x".repeat(200_000)
        val file = write("long.txt", "$longLine\n第二章 合法章节\n正文", StandardCharsets.UTF_8)

        val index = TxtBookIndexer.build(file, StandardCharsets.UTF_8, 0)

        assertEquals(1, index.chapters.size)
        assertEquals("第二章 合法章节", index.chapters.single().title)
        assertEquals(longLine.length + "\n第二章 合法章节\n正文".length, index.totalCharacters.toInt())
    }

    @Test
    fun `supports files without chapters and restores an exact character offset`() {
        val text = buildString {
            repeat(20_000) { append("段落").append(it).append("：这是测试内容。\n") }
        }
        val file = write("plain.txt", text, StandardCharsets.UTF_8)
        val index = TxtBookIndexer.build(file, StandardCharsets.UTF_8, 0)
        val expectedOffset = text.indexOf("段落15000").toLong()

        val slice = RandomAccessTextSource(file, index).read(expectedOffset, 40)

        assertTrue(index.chapters.isEmpty())
        assertTrue(index.checkpoints.size > 2)
        assertEquals(text.substring(expectedOffset.toInt(), expectedOffset.toInt() + 40), slice.text)
    }

    @Test
    fun `indexes UTF16 GB18030 GBK and Big5`() {
        val charsets = listOf(
            StandardCharsets.UTF_16LE,
            StandardCharsets.UTF_16BE,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("Big5"),
        )
        charsets.forEachIndexed { index, charset ->
            val text = "Chapter 1 Start\n內容${index}測試\n"
            val file = write("encoded-$index.txt", text, charset)
            val bookIndex = TxtBookIndexer.build(file, charset, 0)
            assertEquals(text, RandomAccessTextSource(file, bookIndex).read(0, text.length + 10).text, charset.name())
            assertEquals(1, bookIndex.chapters.size, charset.name())
        }
    }

    @Test
    fun `skips a UTF8 BOM without changing character offsets`() {
        val file = tempDirectory.resolve("bom.txt")
        Files.write(
            file,
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "第一章\n正文".toByteArray(StandardCharsets.UTF_8),
        )
        val index = TxtBookIndexer.build(file, StandardCharsets.UTF_8, 3)
        assertEquals("第一章\n正文", RandomAccessTextSource(file, index).read(0, 20).text)
        assertEquals(0, (index.chapters.single().locator as ReadingLocator.TextLocator).characterOffset)
    }

    private fun write(name: String, text: String, charset: Charset): Path =
        tempDirectory.resolve(name).also { Files.write(it, text.toByteArray(charset)) }
}
