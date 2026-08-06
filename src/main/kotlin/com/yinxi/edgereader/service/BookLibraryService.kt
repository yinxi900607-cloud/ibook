package com.yinxi.edgereader.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.parser.BookOpenContext
import com.yinxi.edgereader.parser.BookParserRegistry
import com.yinxi.edgereader.parser.epub.EpubChapterContent
import com.yinxi.edgereader.parser.epub.EpubParsedBook
import com.yinxi.edgereader.parser.txt.TxtParsedBook
import com.yinxi.edgereader.parser.txt.TextSlice
import com.yinxi.edgereader.persistence.database.EdgeReaderDatabaseService
import com.yinxi.edgereader.persistence.repository.SqliteBookRepository
import com.yinxi.edgereader.persistence.repository.SqliteProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.APP)
class BookLibraryService(
    private val coroutineScope: CoroutineScope,
) {
    private val databaseService = service<EdgeReaderDatabaseService>()
    private val database by lazy { databaseService.getDatabase() }
    private val books by lazy { SqliteBookRepository(database) }
    private val progress by lazy { SqliteProgressRepository(database) }
    private val identity by lazy { BookIdentityManager(books) }
    private val coverCache by lazy { BookCoverCache(databaseService.dataDirectory().resolve("cache/covers")) }
    private val registry = BookParserRegistry()

    fun loadLibrary(callback: (Result<List<BookRecord>>) -> Unit): Job = coroutineScope.launch(Dispatchers.IO) {
        val result = runCatching {
            books.listAll().map { book ->
                val missing = !Files.isRegularFile(Path.of(book.currentPath))
                if (missing != book.missing) books.updateMissing(book.id, missing)
                book.copy(missing = missing)
            }
        }
        deliver(callback, result)
    }

    fun openBook(
        file: Path,
        encodingOverride: String? = null,
        callback: (Result<OpenedBook>) -> Unit,
    ): Job = coroutineScope.launch(Dispatchers.IO) {
        val result = runCatching { openBookInternal(file, encodingOverride) }.onFailure { exception ->
            logger<BookLibraryService>().warn("Failed to open book format", exception)
        }
        deliver(callback, result)
    }

    fun openBook(record: BookRecord, callback: (Result<OpenedBook>) -> Unit): Job {
        val file = Path.of(record.currentPath)
        if (!Files.isRegularFile(file)) {
            deliver(callback, Result.failure(IllegalStateException("The original file is missing")))
            return coroutineScope.launch { }
        }
        return openBook(file, record.encoding, callback)
    }

    fun removeBook(bookId: String, callback: (Result<Unit>) -> Unit): Job = coroutineScope.launch(Dispatchers.IO) {
        deliver(callback, runCatching { books.delete(bookId) })
    }

    fun relocateBook(
        record: BookRecord,
        newFile: Path,
        callback: (Result<OpenedBook>) -> Unit,
    ): Job = coroutineScope.launch(Dispatchers.IO) {
        val result = runCatching {
            val quickMatch = identity.quickFingerprint(newFile) == record.quickFingerprint
            val fullMatch = !quickMatch && record.contentHash != null && identity.fullHash(newFile) == record.contentHash
            require(quickMatch || fullMatch) { "The selected file does not match this book" }
            openBookInternal(newFile, record.encoding)
        }
        deliver(callback, result)
    }

    fun readTextSlice(
        book: OpenedBook,
        startOffset: Long,
        maxCharacters: Int,
        callback: (Result<TextSlice>) -> Unit,
    ): Job = coroutineScope.launch(Dispatchers.IO) {
        deliver(callback, runCatching { (book.parsedBook as TxtParsedBook).source.read(startOffset, maxCharacters) })
    }

    fun readEpubChapter(
        book: OpenedBook,
        chapterIndex: Int,
        callback: (Result<EpubChapterContent>) -> Unit,
    ): Job = coroutineScope.launch(Dispatchers.IO) {
        deliver(callback, runCatching { (book.parsedBook as EpubParsedBook).chapter(chapterIndex) })
    }

    private suspend fun openBookInternal(file: Path, encodingOverride: String?): OpenedBook {
        val parser = requireNotNull(registry.findParser(file)) { "Unsupported book format" }
        val record = identity.resolve(file, parser.format, encodingOverride)
        val parsed = parser.open(
            file,
            BookOpenContext(
                bookId = record.id,
                encoding = encodingOverride ?: record.encoding,
                indexCacheDirectory = databaseService.dataDirectory().resolve("cache/indexes"),
                bookCacheDirectory = databaseService.dataDirectory().resolve("cache/epub"),
                cacheKey = record.quickFingerprint,
            ),
        )
        if (parsed is TxtParsedBook) books.updateEncoding(record.id, parsed.index.charsetName)
        val navigation = parser.buildNavigation(parsed)
        books.replaceChapters(record.id, navigation)
        val currentRecord = books.findById(record.id) ?: record
        val thumbnail = (parsed as? EpubParsedBook)?.coverFile?.let { coverCache.createThumbnail(record.id, it) }
        books.upsert(
            currentRecord.copy(
                title = parsed.metadata.title,
                author = parsed.metadata.author,
                format = parsed.metadata.format,
                coverCachePath = thumbnail?.toString() ?: currentRecord.coverCachePath,
                encoding = (parsed as? TxtParsedBook)?.index?.charsetName ?: record.encoding,
            ),
        )
        books.markOpened(record.id, System.currentTimeMillis())
        if (record.contentHash == null) scheduleFullHash(record.id, file)
        val refreshed = books.findById(record.id) ?: record
        return OpenedBook(refreshed, parsed, progress.find(record.id))
    }

    private fun scheduleFullHash(bookId: String, file: Path) {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                if (Files.isRegularFile(file)) books.updateContentHash(bookId, identity.fullHash(file))
            }.onFailure { logger<BookLibraryService>().warn("Failed to calculate full book hash", it) }
        }
    }

    private fun <T> deliver(callback: (Result<T>) -> Unit, result: Result<T>) {
        ApplicationManager.getApplication().invokeLater { callback(result) }
    }
}
