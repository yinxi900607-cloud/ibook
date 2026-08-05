package com.yinxi.edgereader.model

data class Bookmark(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val title: String?,
    val excerpt: String?,
    val createdAt: Long,
)
