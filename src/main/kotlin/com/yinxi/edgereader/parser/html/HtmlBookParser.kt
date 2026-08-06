package com.yinxi.edgereader.parser.html

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.model.SearchOptions
import com.yinxi.edgereader.model.SearchResult
import com.yinxi.edgereader.parser.BookOpenContext
import com.yinxi.edgereader.parser.BookParser
import com.yinxi.edgereader.parser.ParsedBook
import com.yinxi.edgereader.security.HtmlSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

class HtmlBookParser : BookParser {
    override val format: BookFormat = BookFormat.HTML

    override fun supports(file: Path): Boolean {
        if (!Files.isRegularFile(file)) return false
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
        val header = runCatching { Files.newInputStream(file).use { it.readNBytes(1_024) } }.getOrNull() ?: return false
        if (0.toByte() in header) return false
        if (extension in EXTENSIONS) return true
        val text = header.toString(StandardCharsets.UTF_8).lowercase()
        return "<html" in text || "<!doctype html" in text || ("<?xml" in text && "<body" in text)
    }

    override suspend fun parseMetadata(file: Path): BookMetadata = withContext(Dispatchers.IO) {
        val source = Files.readString(file, StandardCharsets.UTF_8)
        val document = Jsoup.parse(source)
        BookMetadata(
            title = document.title().trim().takeIf(String::isNotBlank)
                ?: document.selectFirst("h1")?.text()?.trim()?.takeIf(String::isNotBlank)
                ?: fallbackTitle(file),
            author = document.selectFirst("meta[name=author]")?.attr("content")?.trim()?.takeIf(String::isNotBlank),
            format = BookFormat.HTML,
        )
    }

    override suspend fun open(file: Path, context: BookOpenContext): HtmlParsedBook = withContext(Dispatchers.IO) {
        val sanitizer = HtmlSanitizer(file.parent, allowLocalStyleSheets = false)
        val content = HtmlDocumentProcessor.process(sanitizer.sanitizeHtml(file), file)
        val document = Jsoup.parse(content.html)
        HtmlParsedBook(
            bookId = context.bookId,
            file = file,
            metadata = BookMetadata(
                title = HtmlDocumentProcessor.title(content, fallbackTitle(file)),
                author = document.selectFirst("meta[name=author]")?.attr("content")?.trim()?.takeIf(String::isNotBlank),
                format = BookFormat.HTML,
            ),
            content = content,
        )
    }

    override suspend fun buildNavigation(book: ParsedBook): List<BookNavigationItem> = html(book).content.navigation

    override suspend fun search(book: ParsedBook, query: String, options: SearchOptions): List<SearchResult> =
        searchHtml(html(book), query, options)

    private fun html(book: ParsedBook): HtmlParsedBook = book as? HtmlParsedBook
        ?: throw IllegalArgumentException("HTML parser received a different book format")

    companion object {
        private val EXTENSIONS = setOf("html", "htm", "xhtml")

        internal suspend fun searchHtml(book: HtmlParsedBook, query: String, options: SearchOptions): List<SearchResult> {
            if (query.isBlank()) return emptyList()
            val text = book.content.visibleText
            val results = mutableListOf<SearchResult>()
            var from = 0
            while (results.size < options.maxResults) {
                coroutineContext.ensureActive()
                val match = text.indexOf(query, from, ignoreCase = !options.caseSensitive)
                if (match < 0) break
                val start = (match - 48).coerceAtLeast(0)
                val end = (match + query.length + 72).coerceAtMost(text.length)
                results += SearchResult(
                    bookId = book.bookId,
                    locator = ReadingLocator.HtmlLocator(book.file.fileName.toString(), null, match, null),
                    title = book.content.navigation.lastOrNull {
                        (it.locator as? ReadingLocator.HtmlLocator)?.normalizedTextOffset?.let { offset -> offset <= match } == true
                    }?.title,
                    excerpt = text.substring(start, end).replace(Regex("\\s+"), " ").trim(),
                    matchStart = match - start,
                    matchLength = query.length,
                )
                from = match + maxOf(1, query.length)
            }
            return results
        }

        private fun fallbackTitle(file: Path): String = file.fileName.toString().substringBeforeLast('.').ifBlank { "Untitled HTML" }
    }
}
