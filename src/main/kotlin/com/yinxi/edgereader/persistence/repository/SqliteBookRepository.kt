package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.persistence.database.EdgeReaderDatabase
import java.sql.Connection
import java.sql.ResultSet

class SqliteBookRepository(
    private val database: EdgeReaderDatabase,
) : BookRepository {
    override fun findById(id: String): BookRecord? = findOne("id", id)

    override fun findByCanonicalPath(path: String): BookRecord? = findOne("canonical_path", path)

    override fun findByQuickFingerprint(fingerprint: String): BookRecord? = findOne("quick_fingerprint", fingerprint)

    override fun findByContentHash(hash: String): BookRecord? = findOne("content_hash", hash)

    override fun listAll(): List<BookRecord> = database.read { connection ->
        connection.prepareStatement("SELECT * FROM books ORDER BY COALESCE(last_read_at, imported_at) DESC").use { statement ->
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toBook()) } }
        }
    }

    override fun upsert(book: BookRecord) {
        database.transaction { connection ->
            connection.prepareStatement(
            """
            INSERT INTO books (
                id, title, author, format, current_path, canonical_path, file_name, file_size, modified_at,
                quick_fingerprint, content_hash, cover_cache_path, encoding, imported_at, last_opened_at,
                last_read_at, reading_duration_seconds, progress_percent, missing
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title=excluded.title, author=excluded.author, format=excluded.format,
                current_path=excluded.current_path, canonical_path=excluded.canonical_path,
                file_name=excluded.file_name, file_size=excluded.file_size, modified_at=excluded.modified_at,
                quick_fingerprint=excluded.quick_fingerprint, content_hash=COALESCE(excluded.content_hash, books.content_hash),
                cover_cache_path=excluded.cover_cache_path, encoding=COALESCE(excluded.encoding, books.encoding),
                last_opened_at=excluded.last_opened_at, last_read_at=excluded.last_read_at,
                reading_duration_seconds=excluded.reading_duration_seconds,
                progress_percent=excluded.progress_percent, missing=excluded.missing
            """.trimIndent(),
            ).use { statement ->
                statement.setString(1, book.id)
            statement.setString(2, book.title)
            statement.setString(3, book.author)
            statement.setString(4, book.format.name)
            statement.setString(5, book.currentPath)
            statement.setString(6, book.canonicalPath)
            statement.setString(7, book.fileName)
            statement.setLong(8, book.fileSize)
            statement.setLong(9, book.modifiedAt)
            statement.setString(10, book.quickFingerprint)
            statement.setString(11, book.contentHash)
            statement.setString(12, book.coverCachePath)
            statement.setString(13, book.encoding)
            statement.setLong(14, book.importedAt)
            statement.setNullableLong(15, book.lastOpenedAt)
            statement.setNullableLong(16, book.lastReadAt)
            statement.setLong(17, book.readingDurationSeconds)
            statement.setDouble(18, book.progressPercent)
            statement.setInt(19, if (book.missing) 1 else 0)
                statement.executeUpdate()
            }
        }
    }

    override fun updatePath(id: String, currentPath: String, canonicalPath: String?, fileSize: Long, modifiedAt: Long) =
        update("UPDATE books SET current_path=?, canonical_path=?, file_size=?, modified_at=?, missing=0 WHERE id=?") {
            setString(1, currentPath)
            setString(2, canonicalPath)
            setLong(3, fileSize)
            setLong(4, modifiedAt)
            setString(5, id)
        }

    override fun updateEncoding(id: String, encoding: String) =
        update("UPDATE books SET encoding=? WHERE id=?") { setString(1, encoding); setString(2, id) }

    override fun updateContentHash(id: String, contentHash: String) =
        update("UPDATE books SET content_hash=? WHERE id=?") { setString(1, contentHash); setString(2, id) }

    override fun updateMissing(id: String, missing: Boolean) =
        update("UPDATE books SET missing=? WHERE id=?") { setInt(1, if (missing) 1 else 0); setString(2, id) }

    override fun markOpened(id: String, openedAt: Long) =
        update("UPDATE books SET last_opened_at=?, missing=0 WHERE id=?") { setLong(1, openedAt); setString(2, id) }

    override fun updateProgressSummary(id: String, progressPercent: Double, readAt: Long) =
        update("UPDATE books SET progress_percent=?, last_read_at=? WHERE id=?") {
            setDouble(1, progressPercent); setLong(2, readAt); setString(3, id)
        }

    override fun replaceChapters(bookId: String, chapters: List<BookNavigationItem>) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM chapter_index WHERE book_id=?").use {
                it.setString(1, bookId)
                it.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO chapter_index(book_id, ordinal, title, locator_json, level) VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                chapters.forEachIndexed { index, chapter ->
                    statement.setString(1, bookId)
                    statement.setInt(2, index)
                    statement.setString(3, chapter.title)
                    statement.setString(4, ReadingLocatorCodec.encode(chapter.locator))
                    statement.setInt(5, chapter.level)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    override fun loadChapters(bookId: String): List<BookNavigationItem> = database.read { connection ->
        connection.prepareStatement(
            "SELECT title, locator_json, level FROM chapter_index WHERE book_id=? ORDER BY ordinal",
        ).use { statement ->
            statement.setString(1, bookId)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(BookNavigationItem(result.getString(1), ReadingLocatorCodec.decode(result.getString(2)), result.getInt(3)))
                    }
                }
            }
        }
    }

    override fun delete(id: String) = update("DELETE FROM books WHERE id=?") { setString(1, id) }

    private fun findOne(column: String, value: String): BookRecord? = database.read { connection ->
        connection.prepareStatement("SELECT * FROM books WHERE $column=? LIMIT 1").use { statement ->
            statement.setString(1, value)
            statement.executeQuery().use { result -> if (result.next()) result.toBook() else null }
        }
    }

    private fun update(sql: String, bind: java.sql.PreparedStatement.() -> Unit) {
        database.transaction { connection ->
            connection.prepareStatement(sql).use { statement -> statement.bind(); statement.executeUpdate() }
        }
    }

    private fun ResultSet.toBook() = BookRecord(
        id = getString("id"),
        title = getString("title"),
        author = getString("author"),
        format = BookFormat.valueOf(getString("format")),
        currentPath = getString("current_path"),
        canonicalPath = getString("canonical_path"),
        fileName = getString("file_name"),
        fileSize = getLong("file_size"),
        modifiedAt = getLong("modified_at"),
        quickFingerprint = getString("quick_fingerprint"),
        contentHash = getString("content_hash"),
        coverCachePath = getString("cover_cache_path"),
        encoding = getString("encoding"),
        importedAt = getLong("imported_at"),
        lastOpenedAt = getNullableLong("last_opened_at"),
        lastReadAt = getNullableLong("last_read_at"),
        readingDurationSeconds = getLong("reading_duration_seconds"),
        progressPercent = getDouble("progress_percent"),
        missing = getInt("missing") != 0,
    )

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)
    }

    private fun ResultSet.getNullableLong(column: String): Long? = getLong(column).let { if (wasNull()) null else it }
}
