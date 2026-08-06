package com.yinxi.edgereader.parser.epub

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

class EpubBookParserTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `reads EPUB 3 metadata manifest spine nav and nested levels`() = runBlocking {
        val file = EpubTestBooks.create(tempDirectory, epub3 = true)
        val book = open(file)

        assertEquals(BookFormat.EPUB, book.metadata.format)
        assertEquals("Edge Reader EPUB 3", book.metadata.title)
        assertEquals("yinxi test", book.metadata.author)
        assertEquals(listOf("chapter-1", "chapter-2"), book.spine.map { it.idref })
        assertEquals("pixel.png", book.coverFile?.fileName?.toString())
        assertEquals(listOf("The Beginning", "A Nested Part", "The End"), book.navigation.map { it.title })
        assertEquals(listOf(1, 2, 1), book.navigation.map { it.level })
        val first = book.navigation.first().locator as ReadingLocator.EpubLocator
        assertEquals("OEBPS/text/ch1.xhtml", first.chapterHref)
        assertEquals("start", first.elementId)
    }

    @Test
    fun `reads EPUB 2 NCX in navigation order`() = runBlocking {
        val book = open(EpubTestBooks.create(tempDirectory, epub3 = false))
        assertEquals("Edge Reader EPUB 2", book.metadata.title)
        assertEquals("pixel.png", book.coverFile?.fileName?.toString())
        assertEquals(listOf("NCX Beginning", "NCX Nested", "NCX End"), book.navigation.map { it.title })
        assertEquals(listOf(1, 2, 1), book.navigation.map { it.level })
    }

    @Test
    fun `sanitizes scripts remote resources and dangerous CSS while keeping local resources`() = runBlocking {
        val book = open(EpubTestBooks.create(tempDirectory))
        val chapter = book.chapter(0)
        val lower = chapter.html.lowercase()

        assertFalse(lower.contains("<script"))
        assertFalse(lower.contains("<iframe"))
        assertFalse(lower.contains("onclick"))
        assertFalse(lower.contains("?xml"))
        assertTrue(chapter.html.trimStart().startsWith("<html", ignoreCase = true))
        assertFalse(lower.contains("https://example.invalid"))
        assertTrue(chapter.html.contains("pixel.png"))
        assertTrue(chapter.html.contains("main.css"))

        val css = Files.readString(book.extractionRoot.resolve("OEBPS/styles/main.css"))
        assertFalse(css.contains("https://"))
        assertTrue(css.contains("pixel.png"))
        assertFalse(css.contains("../"))
    }

    @Test
    fun `search returns chapter stable text locator`() = runBlocking {
        val parser = EpubBookParser()
        val book = open(EpubTestBooks.create(tempDirectory), parser)
        val results = parser.search(book, "Omega", SearchOptions())

        assertEquals(1, results.size)
        val locator = results.single().locator as ReadingLocator.EpubLocator
        assertEquals("chapter-2", locator.spineItemId)
        assertEquals("OEBPS/text/ch2.xhtml", locator.chapterHref)
        assertTrue((locator.normalizedTextOffset ?: -1) >= 0)
    }

    @Test
    fun `restoration resolves spine id before changed href`() = runBlocking {
        val book = open(EpubTestBooks.create(tempDirectory))
        assertEquals(1, book.chapterIndex("renamed.xhtml", "chapter-2"))
        assertEquals(0, book.chapterIndex("missing.xhtml", null))
    }

    @Test
    fun `registry recognizes EPUB content even after extension changes`() {
        val epub = EpubTestBooks.create(tempDirectory)
        val renamed = tempDirectory.resolve("book.data")
        Files.move(epub, renamed)
        assertEquals(BookFormat.EPUB, BookParserRegistry().findParser(renamed)?.format)
    }

    @Test
    fun `rejects an EPUB without a container document`() {
        val file = tempDirectory.resolve("broken.epub")
        EpubTestBooks.writeZip(file, mapOf("mimetype" to "application/epub+zip".toByteArray()))
        org.junit.jupiter.api.assertThrows<InvalidEpubException> {
            runBlocking { open(file) }
        }
    }

    @Test
    fun `rejects XML documents containing a doctype`() {
        val file = tempDirectory.resolve("xxe.epub")
        EpubTestBooks.writeZip(
            file,
            mapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <!DOCTYPE container [<!ENTITY xxe SYSTEM="file:///etc/passwd">]>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
                      <rootfile full-path="&xxe;" media-type="application/oebps-package+xml"/>
                    </rootfiles></container>
                """.trimIndent().toByteArray(),
            ),
        )
        org.junit.jupiter.api.assertThrows<InvalidEpubException> {
            runBlocking { open(file) }
        }
    }

    private suspend fun open(file: Path, parser: EpubBookParser = EpubBookParser()): EpubParsedBook = parser.open(
        file,
        BookOpenContext(
            bookId = "test-book",
            bookCacheDirectory = tempDirectory.resolve("cache"),
            cacheKey = file.fileName.toString(),
        ),
    )
}
