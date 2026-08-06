package com.yinxi.edgereader.parser.pdf

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.model.SearchOptions
import com.yinxi.edgereader.model.SearchResult
import com.yinxi.edgereader.parser.BookOpenContext
import com.yinxi.edgereader.parser.BookParser
import com.yinxi.edgereader.parser.ParsedBook
import kotlinx.coroutines.ensureActive
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

class PdfBookParser : BookParser {
    override val format: BookFormat = BookFormat.PDF

    override fun supports(file: Path): Boolean {
        if (!Files.isRegularFile(file)) return false
        return runCatching {
            Files.newInputStream(file).use { input ->
                val header = ByteArray(PDF_HEADER.size)
                input.read(header) == header.size && header.contentEquals(PDF_HEADER)
            }
        }.getOrDefault(false)
    }

    override suspend fun parseMetadata(file: Path): BookMetadata = loadDocument(file).use { document ->
        metadata(file, document)
    }

    override suspend fun open(file: Path, context: BookOpenContext): PdfParsedBook {
        val document = loadDocument(file)
        return try {
            if (document.numberOfPages == 0) throw InvalidPdfException("This PDF contains no pages.")
            val metadata = metadata(file, document)
            val navigation = readNavigation(document)
            PdfParsedBook(
                bookId = context.bookId,
                file = file,
                metadata = metadata,
                document = document,
                navigation = navigation,
                hasSearchableText = detectSearchableText(document),
            )
        } catch (exception: Exception) {
            document.close()
            throw exception
        }
    }

    override suspend fun buildNavigation(book: ParsedBook): List<BookNavigationItem> = pdf(book).navigation

    override suspend fun search(book: ParsedBook, query: String, options: SearchOptions): List<SearchResult> {
        val pdf = pdf(book)
        if (query.isBlank() || !pdf.hasSearchableText) return emptyList()
        val results = mutableListOf<SearchResult>()
        for (pageIndex in 0 until pdf.pageCount) {
            coroutineContext.ensureActive()
            val text = pdf.extractPageText(pageIndex)
            var searchFrom = 0
            while (results.size < options.maxResults) {
                val match = text.indexOf(query, searchFrom, ignoreCase = !options.caseSensitive)
                if (match < 0) break
                val excerptStart = (match - 48).coerceAtLeast(0)
                val excerptEnd = (match + query.length + 72).coerceAtMost(text.length)
                val excerpt = text.substring(excerptStart, excerptEnd).replace(WHITESPACE, " ").trim()
                results += SearchResult(
                    bookId = pdf.bookId,
                    locator = ReadingLocator.PdfLocator(pageIndex, 0.0, PdfZoomMode.FIT_WIDTH.name, null),
                    title = "Page ${pageIndex + 1}",
                    excerpt = excerpt,
                    matchStart = (match - excerptStart).coerceAtLeast(0),
                    matchLength = query.length,
                )
                searchFrom = match + maxOf(1, query.length)
            }
            if (results.size >= options.maxResults) break
        }
        return results
    }

    private fun loadDocument(file: Path): PDDocument = try {
        Loader.loadPDF(file.toFile())
    } catch (exception: InvalidPasswordException) {
        throw InvalidPdfException("This PDF is password protected and cannot be opened.", exception)
    } catch (exception: Exception) {
        throw InvalidPdfException("The PDF is damaged or cannot be read.", exception)
    }

    private fun metadata(file: Path, document: PDDocument): BookMetadata {
        val information = document.documentInformation
        val title = information.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: file.fileName.toString().substringBeforeLast('.').ifBlank { "Untitled PDF" }
        return BookMetadata(
            title = title,
            author = information.author?.trim().takeUnless { it.isNullOrBlank() },
            format = BookFormat.PDF,
        )
    }

    private fun readNavigation(document: PDDocument): List<BookNavigationItem> {
        val outline = document.documentCatalog.documentOutline ?: return emptyList()
        val result = mutableListOf<BookNavigationItem>()
        appendOutline(document, outline.firstChild, 1, result)
        return result
    }

    private fun appendOutline(
        document: PDDocument,
        first: PDOutlineItem?,
        level: Int,
        result: MutableList<BookNavigationItem>,
    ) {
        if (level > MAX_OUTLINE_DEPTH || result.size >= MAX_OUTLINE_ITEMS) return
        var item = first
        while (item != null && result.size < MAX_OUTLINE_ITEMS) {
            val title = item.title?.trim().takeUnless { it.isNullOrBlank() }
            val page = runCatching { item.findDestinationPage(document) }.getOrNull()
            val pageIndex = page?.let { document.pages.indexOf(it) } ?: -1
            if (title != null && pageIndex >= 0) {
                result += BookNavigationItem(
                    title = title,
                    locator = ReadingLocator.PdfLocator(pageIndex, 0.0, PdfZoomMode.FIT_WIDTH.name, null),
                    level = level,
                )
            }
            appendOutline(document, item.firstChild, level + 1, result)
            item = item.nextSibling
        }
    }

    private fun detectSearchableText(document: PDDocument): Boolean {
        if (!document.currentAccessPermission.canExtractContent() || document.numberOfPages == 0) return false
        val samples = linkedSetOf(0, document.numberOfPages / 2, document.numberOfPages - 1)
        return samples.any { pageIndex ->
            runCatching {
                PDFTextStripper().apply {
                    sortByPosition = true
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }.getText(document).any(Char::isLetterOrDigit)
            }.getOrDefault(false)
        }
    }

    private fun pdf(book: ParsedBook): PdfParsedBook = book as? PdfParsedBook
        ?: throw IllegalArgumentException("PDF parser received a different book format")

    companion object {
        private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val WHITESPACE = Regex("\\s+")
        private const val MAX_OUTLINE_DEPTH = 32
        private const val MAX_OUTLINE_ITEMS = 50_000
    }
}
