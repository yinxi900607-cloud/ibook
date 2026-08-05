package com.yinxi.edgereader.model

data class ReadingProgress(
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String?,
    val progressPercent: Double,
    val updatedAt: Long,
)
