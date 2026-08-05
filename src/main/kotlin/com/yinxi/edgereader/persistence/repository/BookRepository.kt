package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.BookRecord

interface BookRepository {
    fun findById(id: String): BookRecord?
    fun findByCanonicalPath(path: String): BookRecord?
    fun findByQuickFingerprint(fingerprint: String): BookRecord?
    fun findByContentHash(hash: String): BookRecord?
    fun listAll(): List<BookRecord>
    fun upsert(book: BookRecord)
    fun updatePath(id: String, currentPath: String, canonicalPath: String?, fileSize: Long, modifiedAt: Long)
    fun updateEncoding(id: String, encoding: String)
    fun updateContentHash(id: String, contentHash: String)
    fun updateMissing(id: String, missing: Boolean)
    fun markOpened(id: String, openedAt: Long)
    fun updateProgressSummary(id: String, progressPercent: Double, readAt: Long)
    fun replaceChapters(bookId: String, chapters: List<BookNavigationItem>)
    fun loadChapters(bookId: String): List<BookNavigationItem>
    fun delete(id: String)
}
