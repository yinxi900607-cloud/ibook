package com.yinxi.edgereader.service

import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.model.ReadingProgress
import com.yinxi.edgereader.parser.ParsedBook

data class OpenedBook(
    val record: BookRecord,
    val parsedBook: ParsedBook,
    val progress: ReadingProgress?,
)
