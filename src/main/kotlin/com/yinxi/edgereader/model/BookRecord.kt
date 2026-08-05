package com.yinxi.edgereader.model

data class BookRecord(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val currentPath: String,
    val canonicalPath: String?,
    val fileName: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val quickFingerprint: String,
    val contentHash: String?,
    val coverCachePath: String?,
    val encoding: String?,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val lastReadAt: Long?,
    val readingDurationSeconds: Long,
    val progressPercent: Double,
    val missing: Boolean,
)
