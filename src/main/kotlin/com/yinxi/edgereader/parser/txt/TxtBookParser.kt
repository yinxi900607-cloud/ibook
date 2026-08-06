package com.yinxi.edgereader.parser.txt

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.model.SearchOptions
import com.yinxi.edgereader.model.SearchResult
import com.yinxi.edgereader.parser.BookOpenContext
import com.yinxi.edgereader.parser.BookParser
import com.yinxi.edgereader.parser.ParsedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

class EncodingSelectionRequiredException(
    val candidates: List<Charset>,
) : Exception("Text encoding could not be detected reliably")

data class TxtParsedBook(
    override val bookId: String,
    override val file: Path,
    override val metadata: BookMetadata,
    val index: TxtBookIndex,
    val source: RandomAccessTextSource,
) : ParsedBook {
    override fun close() = Unit
}

class TxtBookParser : BookParser {
    override val format = BookFormat.TXT

    override fun supports(file: Path): Boolean {
        if (!Files.isRegularFile(file)) return false
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension in setOf("txt", "text")) return true
        if (extension in setOf("epub", "pdf", "md", "markdown", "html", "htm", "xhtml")) return false
        val header = Files.newInputStream(file).use { it.readNBytes(8) }
        return header.isNotEmpty() && !header.startsWith("%PDF".toByteArray()) && !header.startsWith("PK".toByteArray())
    }

    override suspend fun parseMetadata(file: Path): BookMetadata = withContext(Dispatchers.IO) {
        BookMetadata(
            title = file.fileName.toString().substringBeforeLast('.'),
            author = null,
            format = BookFormat.TXT,
        )
    }

    override suspend fun open(file: Path, context: BookOpenContext): TxtParsedBook = withContext(Dispatchers.IO) {
        val detection = TxtEncodingDetector.detect(file)
        val charset = context.encoding?.let(Charset::forName) ?: detection.charset
        if (context.encoding == null && !detection.reliable) {
            throw EncodingSelectionRequiredException(detection.candidates)
        }
        val bomLength = if (context.encoding == null || charset == detection.charset) detection.bomLength else 0
        val metadata = parseMetadata(file)
        val index = context.indexCacheDirectory?.let { cacheDirectory ->
            TxtIndexCache.load(cacheDirectory.resolve("${context.bookId}.txt-index"), file, charset, bomLength)
        } ?: TxtBookIndexer.build(file, charset, bomLength)
        context.indexCacheDirectory?.let { cacheDirectory ->
            TxtIndexCache.save(cacheDirectory.resolve("${context.bookId}.txt-index"), index)
        }
        TxtParsedBook(context.bookId, file, metadata, index, RandomAccessTextSource(file, index))
    }

    override suspend fun buildNavigation(book: ParsedBook): List<BookNavigationItem> =
        (book as TxtParsedBook).index.chapters

    override suspend fun search(
        book: ParsedBook,
        query: String,
        options: SearchOptions,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val txtBook = book as TxtParsedBook
        val results = mutableListOf<SearchResult>()
        var offset = 0L
        val chunkSize = 64 * 1024
        while (offset < txtBook.index.totalCharacters && results.size < options.maxResults) {
            coroutineContext.ensureActive()
            val slice = txtBook.source.read(offset, chunkSize)
            if (slice.text.isEmpty()) break
            val haystack = if (options.caseSensitive) slice.text else slice.text.lowercase()
            val needle = if (options.caseSensitive) query else query.lowercase()
            var match = haystack.indexOf(needle)
            while (match >= 0 && results.size < options.maxResults) {
                val globalOffset = slice.startOffset + match
                results += SearchResult(
                    bookId = txtBook.bookId,
                    locator = ReadingLocator.TextLocator(globalOffset, null, null),
                    title = chapterAt(txtBook.index.chapters, globalOffset),
                    excerpt = excerpt(slice.text, match, query.length),
                    matchStart = match,
                    matchLength = query.length,
                )
                match = haystack.indexOf(needle, match + maxOf(1, needle.length))
            }
            offset += slice.text.length
        }
        results
    }

    private fun chapterAt(chapters: List<BookNavigationItem>, offset: Long): String? = chapters
        .lastOrNull { (it.locator as ReadingLocator.TextLocator).characterOffset <= offset }
        ?.title

    private fun excerpt(text: String, match: Int, length: Int): String {
        val start = maxOf(0, match - 40)
        val end = minOf(text.length, match + length + 80)
        return text.substring(start, end).replace('\n', ' ')
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
