package com.yinxi.edgereader.parser

import com.yinxi.edgereader.parser.epub.EpubBookParser
import com.yinxi.edgereader.parser.txt.TxtBookParser
import java.nio.file.Path

class BookParserRegistry(
    private val parsers: List<BookParser> = listOf(EpubBookParser(), TxtBookParser()),
) {
    fun findParser(file: Path): BookParser? = parsers.firstOrNull { it.supports(file) }
}
