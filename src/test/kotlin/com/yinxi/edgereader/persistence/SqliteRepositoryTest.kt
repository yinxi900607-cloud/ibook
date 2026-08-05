package com.yinxi.edgereader.persistence

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.model.ReadingProgress
import com.yinxi.edgereader.persistence.database.EdgeReaderDatabase
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import com.yinxi.edgereader.persistence.repository.SqliteBookRepository
import com.yinxi.edgereader.persistence.repository.SqliteProgressRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SqliteRepositoryTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `initializes and repeatedly opens the latest schema`() {
        val path = tempDirectory.resolve("edge-reader.db")
        EdgeReaderDatabase(path).use { assertEquals(2, it.schemaVersion()) }
        EdgeReaderDatabase(path).use { assertEquals(2, it.schemaVersion()) }
    }

    @Test
    fun `stores updates and deletes a book with its progress and chapters`() {
        EdgeReaderDatabase(tempDirectory.resolve("repository.db")).use { database ->
            val books = SqliteBookRepository(database)
            val progress = SqliteProgressRepository(database)
            val book = sampleBook()
            books.upsert(book)

            assertEquals(book, books.findById(book.id))
            books.updatePath(book.id, "/moved/book.txt", "/moved/book.txt", 42, 200)
            books.updateEncoding(book.id, "GB18030")
            books.updateContentHash(book.id, "full-hash")
            val updated = books.findById(book.id)!!
            assertEquals("/moved/book.txt", updated.currentPath)
            assertEquals("GB18030", updated.encoding)
            assertEquals("full-hash", updated.contentHash)
            assertEquals(book.id, books.findByContentHash("full-hash")?.id)

            val locator = ReadingLocator.TextLocator(1234, null, 0.25)
            val first = ReadingProgress(book.id, ReadingLocatorCodec.encode(locator), "第一章", 20.0, 1000)
            val second = first.copy(progressPercent = 40.0, updatedAt = 2000)
            progress.save(first)
            progress.save(second)
            assertEquals(second, progress.find(book.id))

            val chapters = listOf(BookNavigationItem("第一章", locator))
            books.replaceChapters(book.id, chapters)
            assertEquals(chapters, books.loadChapters(book.id))

            books.delete(book.id)
            assertNull(books.findById(book.id))
            assertNull(progress.find(book.id))
        }
    }

    private fun sampleBook() = BookRecord(
        id = "book-1",
        title = "Test Book",
        author = null,
        format = BookFormat.TXT,
        currentPath = "/books/book.txt",
        canonicalPath = "/books/book.txt",
        fileName = "book.txt",
        fileSize = 10,
        modifiedAt = 100,
        quickFingerprint = "quick",
        contentHash = null,
        coverCachePath = null,
        encoding = "UTF-8",
        importedAt = 100,
        lastOpenedAt = null,
        lastReadAt = null,
        readingDurationSeconds = 0,
        progressPercent = 0.0,
        missing = false,
    )
}
