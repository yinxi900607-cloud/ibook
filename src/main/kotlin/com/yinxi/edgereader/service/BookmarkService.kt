package com.yinxi.edgereader.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.yinxi.edgereader.model.Bookmark
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.persistence.database.EdgeReaderDatabaseService
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import com.yinxi.edgereader.persistence.repository.SqliteBookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

@Service(Service.Level.APP)
class BookmarkService(
    private val coroutineScope: CoroutineScope,
) {
    private val repository by lazy {
        SqliteBookmarkRepository(service<EdgeReaderDatabaseService>().getDatabase())
    }

    fun add(
        bookId: String,
        locator: ReadingLocator,
        title: String?,
        excerpt: String?,
        callback: (Result<Bookmark>) -> Unit,
    ): Job = coroutineScope.launch(Dispatchers.IO) {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            locatorJson = ReadingLocatorCodec.encode(locator),
            title = title?.take(200),
            excerpt = excerpt?.replace(Regex("\\s+"), " ")?.trim()?.take(300),
            createdAt = System.currentTimeMillis(),
        )
        val result = runCatching {
            repository.save(bookmark)
            bookmark
        }.onFailure { logger<BookmarkService>().warn("Failed to add bookmark", it) }
        deliver(callback, result)
    }

    fun list(bookId: String, callback: (Result<List<Bookmark>>) -> Unit): Job = coroutineScope.launch(Dispatchers.IO) {
        val result = runCatching { repository.list(bookId) }
            .onFailure { logger<BookmarkService>().warn("Failed to list bookmarks", it) }
        deliver(callback, result)
    }

    fun delete(bookmarkId: String, callback: (Result<Unit>) -> Unit): Job = coroutineScope.launch(Dispatchers.IO) {
        val result = runCatching { repository.delete(bookmarkId) }
            .onFailure { logger<BookmarkService>().warn("Failed to delete bookmark", it) }
        deliver(callback, result)
    }

    private fun <T> deliver(callback: (Result<T>) -> Unit, result: Result<T>) {
        ApplicationManager.getApplication().invokeLater { callback(result) }
    }
}
