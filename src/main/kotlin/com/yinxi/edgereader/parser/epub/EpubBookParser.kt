package com.yinxi.edgereader.parser.epub

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
import com.yinxi.edgereader.security.SafeZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

class EpubBookParser(
    private val extractor: SafeZipExtractor = SafeZipExtractor(),
) : BookParser {
    override val format = BookFormat.EPUB

    override fun supports(file: Path): Boolean {
        if (!Files.isRegularFile(file) || Files.size(file) < 4) return false
        val signature = Files.newInputStream(file).use { it.readNBytes(4) }
        if (!(signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte())) return false
        if (file.fileName.toString().endsWith(".epub", true)) return true
        return runCatching { ZipFile(file.toFile()).use { it.getEntry("META-INF/container.xml") != null } }.getOrDefault(false)
    }

    override suspend fun parseMetadata(file: Path): BookMetadata = withContext(Dispatchers.IO) {
        val temporary = Files.createTempDirectory("edge-reader-epub-metadata-")
        try {
            extractor.extract(file, temporary.resolve("book"))
            EpubPackageReader(temporary.resolve("book")).read().metadata
        } finally {
            Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    override suspend fun open(file: Path, context: BookOpenContext): EpubParsedBook = withContext(Dispatchers.IO) {
        val baseCache = requireNotNull(context.bookCacheDirectory) { "EPUB cache directory is not configured" }
        val cacheKey = context.cacheKey?.take(24) ?: "current"
        val extractionRoot = baseCache.resolve(context.bookId).resolve(cacheKey)
        if (!extractor.isComplete(extractionRoot)) extractor.extract(file, extractionRoot)
        val sanitizer = HtmlSanitizer(extractionRoot)
        sanitizer.sanitizeStyleSheets()
        val epubPackage = EpubPackageReader(extractionRoot).read()
        EpubParsedBook(
            context.bookId,
            file,
            epubPackage.metadata,
            extractionRoot,
            epubPackage.packageFile,
            epubPackage.coverFile,
            epubPackage.manifest,
            epubPackage.spine,
            epubPackage.navigation,
            sanitizer,
        )
    }

    override suspend fun buildNavigation(book: ParsedBook): List<BookNavigationItem> =
        (book as EpubParsedBook).navigation

    override suspend fun search(book: ParsedBook, query: String, options: SearchOptions): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val epub = book as EpubParsedBook
            val needle = if (options.caseSensitive) query else query.lowercase()
            val results = mutableListOf<SearchResult>()
            for (index in epub.spine.indices) {
                coroutineContext.ensureActive()
                val chapter = epub.chapter(index)
                val haystack = if (options.caseSensitive) chapter.visibleText else chapter.visibleText.lowercase()
                var match = haystack.indexOf(needle)
                while (match >= 0 && results.size < options.maxResults) {
                    results += SearchResult(
                        bookId = epub.bookId,
                        locator = ReadingLocator.EpubLocator(chapter.spineItemId, chapter.chapterHref, null, match, null),
                        title = chapter.title,
                        excerpt = excerpt(chapter.visibleText, match, query.length),
                        matchStart = match,
                        matchLength = query.length,
                    )
                    match = haystack.indexOf(needle, match + maxOf(1, needle.length))
                }
                if (results.size >= options.maxResults) break
            }
            results
        }

    private fun excerpt(text: String, match: Int, length: Int): String {
        val start = maxOf(0, match - 40)
        val end = minOf(text.length, match + length + 80)
        return text.substring(start, end).replace(Regex("\\s+"), " ")
    }
}
