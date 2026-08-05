package com.yinxi.edgereader.parser

import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.SearchOptions
import com.yinxi.edgereader.model.SearchResult
import java.io.Closeable
import java.nio.file.Path

data class BookOpenContext(
    val bookId: String,
    val encoding: String? = null,
    val indexCacheDirectory: Path? = null,
)

interface ParsedBook : Closeable {
    val bookId: String
    val file: Path
    val metadata: BookMetadata
}

interface BookParser {
    fun supports(file: Path): Boolean
    suspend fun parseMetadata(file: Path): BookMetadata
    suspend fun open(file: Path, context: BookOpenContext): ParsedBook
    suspend fun buildNavigation(book: ParsedBook): List<BookNavigationItem>
    suspend fun search(book: ParsedBook, query: String, options: SearchOptions): List<SearchResult>
    suspend fun close(book: ParsedBook) = book.close()
}
