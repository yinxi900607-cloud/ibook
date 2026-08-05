package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.Bookmark
import com.yinxi.edgereader.persistence.database.EdgeReaderDatabase

class SqliteBookmarkRepository(
    private val database: EdgeReaderDatabase,
) : BookmarkRepository {
    override fun list(bookId: String): List<Bookmark> = database.read { connection ->
        connection.prepareStatement("SELECT * FROM bookmarks WHERE book_id=? ORDER BY created_at DESC").use { statement ->
            statement.setString(1, bookId)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            Bookmark(
                                id = result.getString("id"),
                                bookId = result.getString("book_id"),
                                locatorJson = result.getString("locator_json"),
                                title = result.getString("title"),
                                excerpt = result.getString("excerpt"),
                                createdAt = result.getLong("created_at"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun save(bookmark: Bookmark) {
        database.transaction { connection ->
            connection.prepareStatement(
            "INSERT OR REPLACE INTO bookmarks(id, book_id, locator_json, title, excerpt, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, bookmark.id)
            statement.setString(2, bookmark.bookId)
            statement.setString(3, bookmark.locatorJson)
            statement.setString(4, bookmark.title)
            statement.setString(5, bookmark.excerpt)
            statement.setLong(6, bookmark.createdAt)
                statement.executeUpdate()
            }
        }
    }

    override fun delete(id: String) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM bookmarks WHERE id=?").use {
                it.setString(1, id)
                it.executeUpdate()
            }
        }
    }
}
