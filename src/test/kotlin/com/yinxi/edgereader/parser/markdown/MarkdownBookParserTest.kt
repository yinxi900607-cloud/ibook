package com.yinxi.edgereader.parser.markdown

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.model.SearchOptions
import com.yinxi.edgereader.parser.BookOpenContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MarkdownBookParserTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `renders headings formatting code images links and search safely`() = runBlocking {
        val image = tempDirectory.resolve("diagram.png")
        Files.write(image, byteArrayOf(1, 2, 3))
        Files.writeString(tempDirectory.resolve("chapter.html"), "<html></html>")
        val file = tempDirectory.resolve("guide.md")
        Files.writeString(
            file,
            """
            # Edge Guide

            This is **important** and searchable.

            ## Code

            ```kotlin
            val answer = 42
            ```

            ![diagram](diagram.png)
            [Local chapter](chapter.html)
            [Remote](https://example.invalid/track)
            <script>alert('raw html')</script>
            """.trimIndent(),
        )

        val parser = MarkdownBookParser()
        val book = parser.open(file, BookOpenContext("markdown-book"))
        val lower = book.content.html.lowercase()
        assertEquals(BookFormat.MARKDOWN, book.metadata.format)
        assertEquals("Edge Guide", book.metadata.title)
        assertTrue(lower.contains("<strong>important</strong>"))
        assertTrue(lower.contains("<pre><code"))
        assertTrue(book.content.html.contains(image.toUri().toASCIIString()))
        assertTrue(book.content.html.contains(tempDirectory.resolve("chapter.html").toUri().toASCIIString()))
        assertFalse(lower.contains("https://example.invalid"))
        assertFalse(lower.contains("<script"))
        assertTrue(lower.contains("&lt;script&gt;"))
        assertEquals(listOf("Edge Guide", "Code"), book.content.navigation.map { it.title })
        assertEquals(listOf(1, 2), book.content.navigation.map { it.level })
        assertEquals("edge-guide", (book.content.navigation.first().locator as ReadingLocator.HtmlLocator).elementId)

        val result = parser.search(book, "SEARCHABLE", SearchOptions()).single()
        assertTrue(result.locator is ReadingLocator.HtmlLocator)
        assertTrue(result.excerpt.contains("searchable"))
    }

    @Test
    fun `rejects binary content disguised with a markdown extension`() {
        val file = tempDirectory.resolve("binary.md")
        Files.write(file, byteArrayOf(0, 1, 2, 3))
        assertFalse(MarkdownBookParser().supports(file))
    }
}
