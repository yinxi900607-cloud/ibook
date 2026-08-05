package com.yinxi.edgereader.service

import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.model.ReadingProgress
import com.yinxi.edgereader.parser.txt.TxtParsedBook

data class OpenedBook(
    val record: BookRecord,
    val parsedBook: TxtParsedBook,
    val progress: ReadingProgress?,
)
