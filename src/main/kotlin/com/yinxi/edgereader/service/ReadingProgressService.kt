package com.yinxi.edgereader.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.model.ReadingProgress
import com.yinxi.edgereader.persistence.database.EdgeReaderDatabaseService
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import com.yinxi.edgereader.persistence.repository.SqliteBookRepository
import com.yinxi.edgereader.persistence.repository.SqliteProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class ReadingProgressService(
    private val coroutineScope: CoroutineScope,
) : Disposable {
    private val database by lazy { service<EdgeReaderDatabaseService>().getDatabase() }
    private val progressRepository by lazy { SqliteProgressRepository(database) }
    private val bookRepository by lazy { SqliteBookRepository(database) }
    private val pending = ConcurrentHashMap<String, ReadingProgress>()
    private val lastPersistedJson = ConcurrentHashMap<String, String>()
    private val debounceJobs = ConcurrentHashMap<String, Job>()

    init {
        coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(FALLBACK_SAVE_MILLIS)
                flushAll()
            }
        }
    }

    fun update(
        bookId: String,
        locator: ReadingLocator,
        chapterTitle: String?,
        progressPercent: Double,
    ) {
        val locatorJson = ReadingLocatorCodec.encode(locator)
        if (lastPersistedJson[bookId] == locatorJson && pending[bookId] == null) return
        pending[bookId] = ReadingProgress(
            bookId = bookId,
            locatorJson = locatorJson,
            chapterTitle = chapterTitle,
            progressPercent = progressPercent.coerceIn(0.0, 100.0),
            updatedAt = System.currentTimeMillis(),
        )
        debounceJobs.remove(bookId)?.cancel()
        debounceJobs[bookId] = coroutineScope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_MILLIS)
            flush(bookId)
        }
    }

    fun flushAsync(bookId: String?) {
        coroutineScope.launch(Dispatchers.IO) {
            if (bookId == null) flushAll() else flush(bookId)
        }
    }

    fun deleteAsync(bookId: String) {
        pending.remove(bookId)
        debounceJobs.remove(bookId)?.cancel()
        coroutineScope.launch(Dispatchers.IO) { progressRepository.delete(bookId) }
    }

    private fun flush(bookId: String) {
        val value = pending.remove(bookId) ?: return
        if (lastPersistedJson[bookId] == value.locatorJson) return
        runCatching {
            progressRepository.save(value)
            bookRepository.updateProgressSummary(bookId, value.progressPercent, value.updatedAt)
            lastPersistedJson[bookId] = value.locatorJson
        }.onFailure {
            pending[bookId] = value
            logger<ReadingProgressService>().warn("Failed to save reading progress", it)
        }
    }

    private fun flushAll() {
        pending.keys.toList().forEach(::flush)
    }

    override fun dispose() {
        debounceJobs.values.forEach(Job::cancel)
        debounceJobs.clear()
        flushAll()
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 700L
        private const val FALLBACK_SAVE_MILLIS = 15_000L
    }
}
