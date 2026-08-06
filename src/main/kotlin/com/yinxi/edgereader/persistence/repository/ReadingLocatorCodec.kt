package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.ReadingLocator

object ReadingLocatorCodec {
    fun encode(locator: ReadingLocator): String = when (locator) {
        is ReadingLocator.TextLocator -> """{"type":"text","characterOffset":${locator.characterOffset},"paragraphIndex":${locator.paragraphIndex ?: "null"},"scrollRatio":${locator.scrollRatio ?: "null"}}"""
        is ReadingLocator.EpubLocator -> """{"type":"epub","spineItemId":${stringOrNull(locator.spineItemId)},"chapterHref":${quoted(locator.chapterHref)},"elementId":${stringOrNull(locator.elementId)},"normalizedTextOffset":${locator.normalizedTextOffset ?: "null"},"scrollRatio":${locator.scrollRatio ?: "null"}}"""
        is ReadingLocator.PdfLocator -> """{"type":"pdf","pageIndex":${locator.pageIndex},"verticalRatio":${locator.verticalRatio},"zoomMode":${stringOrNull(locator.zoomMode)},"zoomScale":${locator.zoomScale ?: "null"}}"""
        is ReadingLocator.HtmlLocator -> throw IllegalArgumentException("HTML locators are not enabled in Phase 1")
    }

    fun decode(json: String): ReadingLocator {
        val type = stringValue(json, "type")
        return when (type) {
            "text" -> ReadingLocator.TextLocator(
                characterOffset = longValue(json, "characterOffset") ?: 0,
                paragraphIndex = longValue(json, "paragraphIndex")?.toInt(),
                scrollRatio = doubleValue(json, "scrollRatio"),
            )
            "epub" -> ReadingLocator.EpubLocator(
                spineItemId = stringValue(json, "spineItemId"),
                chapterHref = requireNotNull(stringValue(json, "chapterHref")) { "EPUB locator has no chapter href" },
                elementId = stringValue(json, "elementId"),
                normalizedTextOffset = longValue(json, "normalizedTextOffset")?.toInt(),
                scrollRatio = doubleValue(json, "scrollRatio"),
            )
            "pdf" -> ReadingLocator.PdfLocator(
                pageIndex = longValue(json, "pageIndex")?.toInt() ?: 0,
                verticalRatio = doubleValue(json, "verticalRatio") ?: 0.0,
                zoomMode = stringValue(json, "zoomMode"),
                zoomScale = doubleValue(json, "zoomScale"),
            )
            else -> throw IllegalArgumentException("Unsupported locator type: $type")
        }
    }

    private fun stringValue(json: String, name: String): String? =
        Regex("\\\"$name\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(json)?.groupValues?.get(1)?.let(::unescape)

    private fun longValue(json: String, name: String): Long? =
        Regex("\\\"$name\\\"\\s*:\\s*(-?[0-9]+|null)").find(json)?.groupValues?.get(1)?.takeUnless { it == "null" }?.toLong()

    private fun doubleValue(json: String, name: String): Double? =
        Regex("\\\"$name\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?|null)").find(json)?.groupValues?.get(1)?.takeUnless { it == "null" }?.toDouble()

    private fun stringOrNull(value: String?): String = value?.let(::quoted) ?: "null"

    private fun quoted(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""

    private fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\' && index + 1 < value.length) {
                index++
                append(when (value[index]) { 'n' -> '\n'; 'r' -> '\r'; else -> value[index] })
            } else append(value[index])
            index++
        }
    }
}
