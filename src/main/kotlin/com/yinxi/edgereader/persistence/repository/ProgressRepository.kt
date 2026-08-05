package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.ReadingProgress

interface ProgressRepository {
    fun find(bookId: String): ReadingProgress?
    fun save(progress: ReadingProgress)
    fun delete(bookId: String)
}
