package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.ReadingProgress
import com.yinxi.edgereader.persistence.database.EdgeReaderDatabase

class SqliteProgressRepository(
    private val database: EdgeReaderDatabase,
) : ProgressRepository {
    override fun find(bookId: String): ReadingProgress? = database.read { connection ->
        connection.prepareStatement("SELECT * FROM reading_progress WHERE book_id=?").use { statement ->
            statement.setString(1, bookId)
            statement.executeQuery().use { result ->
                if (!result.next()) null else ReadingProgress(
                    bookId = result.getString("book_id"),
                    locatorJson = result.getString("locator_json"),
                    chapterTitle = result.getString("chapter_title"),
                    progressPercent = result.getDouble("progress_percent"),
                    updatedAt = result.getLong("updated_at"),
                )
            }
        }
    }

    override fun save(progress: ReadingProgress) {
        database.transaction { connection ->
            connection.prepareStatement(
            """
            INSERT INTO reading_progress(book_id, locator_json, chapter_title, progress_percent, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(book_id) DO UPDATE SET locator_json=excluded.locator_json,
                chapter_title=excluded.chapter_title, progress_percent=excluded.progress_percent,
                updated_at=excluded.updated_at
            """.trimIndent(),
            ).use { statement ->
                statement.setString(1, progress.bookId)
            statement.setString(2, progress.locatorJson)
            statement.setString(3, progress.chapterTitle)
            statement.setDouble(4, progress.progressPercent)
            statement.setLong(5, progress.updatedAt)
                statement.executeUpdate()
            }
        }
    }

    override fun delete(bookId: String) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM reading_progress WHERE book_id=?").use {
                it.setString(1, bookId)
                it.executeUpdate()
            }
        }
    }
}
