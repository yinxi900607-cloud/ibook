package com.yinxi.edgereader.parser.markdown

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.SearchOptions
import com.yinxi.edgereader.model.SearchResult
import com.yinxi.edgereader.parser.BookOpenContext
import com.yinxi.edgereader.parser.BookParser
import com.yinxi.edgereader.parser.ParsedBook
import com.yinxi.edgereader.parser.html.HtmlBookParser
import com.yinxi.edgereader.parser.html.HtmlDocumentProcessor
import com.yinxi.edgereader.parser.html.HtmlParsedBook
import com.yinxi.edgereader.security.HtmlSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class MarkdownBookParser : BookParser {
    override val format: BookFormat = BookFormat.MARKDOWN

    override fun supports(file: Path): Boolean {
        if (!Files.isRegularFile(file)) return false
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension !in setOf("md", "markdown")) return false
        val header = runCatching { Files.newInputStream(file).use { it.readNBytes(1_024) } }.getOrNull() ?: return false
        return 0.toByte() !in header
    }

    override suspend fun parseMetadata(file: Path): BookMetadata = withContext(Dispatchers.IO) {
        val content = render(file)
        BookMetadata(HtmlDocumentProcessor.title(content, fallbackTitle(file)), null, BookFormat.MARKDOWN)
    }

    override suspend fun open(file: Path, context: BookOpenContext): HtmlParsedBook = withContext(Dispatchers.IO) {
        val content = render(file)
        HtmlParsedBook(
            context.bookId,
            file,
            BookMetadata(HtmlDocumentProcessor.title(content, fallbackTitle(file)), null, BookFormat.MARKDOWN),
            content,
        )
    }

    override suspend fun buildNavigation(book: ParsedBook): List<BookNavigationItem> = (book as HtmlParsedBook).content.navigation

    override suspend fun search(book: ParsedBook, query: String, options: SearchOptions): List<SearchResult> =
        HtmlBookParser.searchHtml(book as HtmlParsedBook, query, options)

    private fun render(file: Path): com.yinxi.edgereader.parser.html.HtmlDocumentContent {
        val markdown = Files.readString(file, StandardCharsets.UTF_8)
        val node = Parser.builder().build().parse(markdown)
        val body = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build().render(node)
        val generated = "<html><head><meta charset=\"UTF-8\"></head><body>$body</body></html>"
        val sanitized = HtmlSanitizer(file.parent, allowLocalStyleSheets = false).sanitizeGeneratedHtml(generated, file)
        return HtmlDocumentProcessor.process(sanitized, file)
    }

    private fun fallbackTitle(file: Path): String = file.fileName.toString().substringBeforeLast('.').ifBlank { "Untitled Markdown" }
}
