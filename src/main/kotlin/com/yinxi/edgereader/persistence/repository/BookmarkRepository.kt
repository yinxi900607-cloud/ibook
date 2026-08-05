package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.Bookmark

interface BookmarkRepository {
    fun list(bookId: String): List<Bookmark>
    fun save(bookmark: Bookmark)
    fun delete(id: String)
}
