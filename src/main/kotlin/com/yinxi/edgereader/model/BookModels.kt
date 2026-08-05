package com.yinxi.edgereader.model

data class BookMetadata(
    val title: String,
    val author: String?,
    val format: BookFormat,
)

data class BookNavigationItem(
    val title: String,
    val locator: ReadingLocator,
    val level: Int = 1,
)

data class SearchOptions(
    val caseSensitive: Boolean = false,
    val maxResults: Int = 200,
)

data class SearchResult(
    val bookId: String,
    val locator: ReadingLocator,
    val title: String?,
    val excerpt: String,
    val matchStart: Int?,
    val matchLength: Int?,
)
