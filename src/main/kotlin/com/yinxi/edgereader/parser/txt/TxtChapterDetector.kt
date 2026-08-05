package com.yinxi.edgereader.parser.txt

object TxtChapterDetector {
    private val patterns = listOf(
        Regex("^\\s*第[零一二三四五六七八九十百千万0-9]+[章节卷部回].*$"),
        Regex("^\\s*Chapter\\s+[0-9IVXLCDM]+.*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*[0-9]+\\s*[.、]\\s*.+$"),
    )

    fun isChapterTitle(line: String): Boolean {
        if (line.length > 512) return false
        return patterns.any { it.matches(line) }
    }
}
