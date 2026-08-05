package com.yinxi.edgereader.util

import java.nio.file.Path

object SupportedBookFiles {
    private val supportedExtensions = setOf(
        "txt",
        "text",
        "epub",
        "pdf",
        "md",
        "markdown",
        "html",
        "htm",
        "xhtml",
    )

    fun isSupported(path: Path): Boolean = isSupportedName(path.fileName?.toString().orEmpty())

    fun isSupportedName(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return extension.lowercase() in supportedExtensions
    }
}
