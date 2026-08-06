package com.yinxi.edgereader.parser.pdf

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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PdfBookParserTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `opens metadata pages outline text and search`() = runBlocking {
        val parser = PdfBookParser()
        val book = open(PdfTestBooks.createTextPdf(tempDirectory), parser)
        book.use {
            assertEquals(BookFormat.PDF, book.metadata.format)
            assertEquals("Edge Reader PDF", book.metadata.title)
            assertEquals("yinxi test", book.metadata.author)
            assertEquals(3, book.pageCount)
            assertTrue(book.hasSearchableText)
            assertEquals("Second Page", book.navigation.single().title)
            assertEquals(1, (book.navigation.single().locator as ReadingLocator.PdfLocator).pageIndex)
            assertTrue(book.extractPageText(1).contains("Omega searchable phrase"))

            val results = parser.search(book, "omega", SearchOptions())
            assertEquals(1, results.size)
            assertEquals(1, (results.single().locator as ReadingLocator.PdfLocator).pageIndex)
            assertTrue(results.single().excerpt.contains("Omega"))
        }
    }

    @Test
    fun `renders one page and bounds image cache while preloading is caller controlled`() {
        val book = runBlocking { open(PdfTestBooks.createTextPdf(tempDirectory)) }
        book.use {
            val rendered = book.renderPage(0, 1f)
            assertEquals(0, rendered.pageIndex)
            assertTrue(rendered.image.width > 0)
            assertTrue(rendered.image.height > 0)
            assertEquals(1, book.cachedPageCount())
            repeat(12) { book.renderPage(it % book.pageCount, 0.75f + it * 0.01f) }
            assertTrue(book.cachedPageCount() <= 5)
        }
    }

    @Test
    fun `blank and scanned PDFs remain readable without searchable text`() = runBlocking {
        open(PdfTestBooks.createBlankPdf(tempDirectory)).use { blank ->
            assertEquals(1, blank.pageCount)
            assertFalse(blank.hasSearchableText)
            assertTrue(blank.extractPageText(0).isBlank())
            assertTrue(blank.renderPage(0, 0.5f).image.width > 0)
        }
        open(PdfTestBooks.createScannedPdf(tempDirectory)).use { scan ->
            assertEquals(1, scan.pageCount)
            assertFalse(scan.hasSearchableText)
            assertTrue(scan.renderPage(0, 0.5f).image.width > 0)
        }
    }

    @Test
    fun `opens a thousand page PDF without rendering every page`() = runBlocking {
        open(PdfTestBooks.createBlankPdf(tempDirectory, 1_000)).use { book ->
            assertEquals(1_000, book.pageCount)
            assertEquals(0, book.cachedPageCount())
            book.renderPage(499, 0.25f)
            assertEquals(1, book.cachedPageCount())
        }
    }

    @Test
    fun `rejects damaged and password protected PDFs with understandable errors`() {
        val damaged = tempDirectory.resolve("damaged.pdf")
        Files.writeString(damaged, "%PDF-this is not a PDF")
        assertThrows<InvalidPdfException> { runBlocking { open(damaged) } }
        assertThrows<InvalidPdfException> { runBlocking { open(PdfTestBooks.createPasswordProtectedPdf(tempDirectory)) } }
    }

    @Test
    fun `registry checks the PDF header rather than extension alone`() {
        val renamed = tempDirectory.resolve("book.data")
        Files.move(PdfTestBooks.createTextPdf(tempDirectory), renamed)
        assertEquals(BookFormat.PDF, BookParserRegistry().findParser(renamed)?.format)

        val fake = tempDirectory.resolve("fake.pdf")
        Files.writeString(fake, "ordinary text")
        assertFalse(PdfBookParser().supports(fake))
    }

    private suspend fun open(file: Path, parser: PdfBookParser = PdfBookParser()): PdfParsedBook = parser.open(
        file,
        BookOpenContext(bookId = "pdf-test-book"),
    )
}
