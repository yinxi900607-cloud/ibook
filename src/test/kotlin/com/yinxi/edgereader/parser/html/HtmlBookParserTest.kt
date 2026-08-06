package com.yinxi.edgereader.parser.html

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.model.SearchOptions
import com.yinxi.edgereader.parser.BookOpenContext
import com.yinxi.edgereader.parser.BookParserRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class HtmlBookParserTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `sanitizes local HTML while retaining safe structure resources and headings`() = runBlocking {
        val image = tempDirectory.resolve("cover.png")
        Files.write(image, byteArrayOf(1, 2, 3))
        Files.writeString(tempDirectory.resolve("theme.css"), "body { color: red }")
        val file = tempDirectory.resolve("book.html")
        Files.writeString(
            file,
            """
            <!doctype html><html><head><title>Safe Local Book</title>
              <meta name="author" content="Local Author"><link rel="stylesheet" href="theme.css">
              <style>p { color: blue; background: url(https://example.invalid/a.png) }</style>
            </head><body onclick="bad()" background="https://example.invalid/bg.png"><h1>Opening</h1><p>Alpha searchable sentence.</p>
              <img src="cover.png"><img src="https://example.invalid/tracker.png">
              <script>alert(1)</script><iframe src="file:///etc/passwd"></iframe>
            </body></html>
            """.trimIndent(),
        )

        val parser = HtmlBookParser()
        val book = parser.open(file, BookOpenContext("html-book"))
        val lower = book.content.html.lowercase()
        assertEquals(BookFormat.HTML, book.metadata.format)
        assertEquals("Safe Local Book", book.metadata.title)
        assertEquals("Local Author", book.metadata.author)
        assertFalse(lower.contains("<script"))
        assertFalse(lower.contains("<iframe"))
        assertFalse(lower.contains("onclick"))
        assertFalse(lower.contains("background="))
        assertFalse(lower.contains("https://"))
        assertFalse(lower.contains("rel=\"stylesheet\""))
        assertTrue(book.content.html.contains(image.toUri().toASCIIString()))
        assertEquals("Opening", book.content.navigation.single().title)
        val locator = book.content.navigation.single().locator as ReadingLocator.HtmlLocator
        assertEquals("opening", locator.elementId)
        assertEquals(0, locator.normalizedTextOffset)

        val results = parser.search(book, "searchable", SearchOptions())
        assertEquals(1, results.size)
        assertTrue(results.single().excerpt.contains("searchable"))
    }

    @Test
    fun `registry recognizes HTML content without an HTML extension`() {
        val file = tempDirectory.resolve("document.data")
        Files.writeString(file, "<!doctype html><html><body>hello</body></html>")
        assertEquals(BookFormat.HTML, BookParserRegistry().findParser(file)?.format)
    }
}
