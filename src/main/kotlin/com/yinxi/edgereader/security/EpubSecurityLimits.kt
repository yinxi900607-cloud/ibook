package com.yinxi.edgereader.security

data class EpubSecurityLimits(
    val maxArchiveBytes: Long = 500L * 1024 * 1024,
    val maxEntries: Int = 20_000,
    val maxEntryBytes: Long = 100L * 1024 * 1024,
    val maxTotalBytes: Long = 1024L * 1024 * 1024,
    val maxCompressionRatio: Double = 200.0,
) {
    init {
        require(maxArchiveBytes > 0 && maxEntries > 0 && maxEntryBytes > 0 && maxTotalBytes > 0)
        require(maxCompressionRatio > 0)
    }
}
