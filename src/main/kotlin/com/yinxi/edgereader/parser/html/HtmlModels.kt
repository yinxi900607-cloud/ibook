package com.yinxi.edgereader.parser.html

import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.parser.ParsedBook
import java.nio.file.Path

data class HtmlDocumentContent(
    val html: String,
    val visibleText: String,
    val navigation: List<BookNavigationItem>,
)

data class HtmlParsedBook(
    override val bookId: String,
    override val file: Path,
    override val metadata: BookMetadata,
    val content: HtmlDocumentContent,
) : ParsedBook {
    override fun close() = Unit
}
