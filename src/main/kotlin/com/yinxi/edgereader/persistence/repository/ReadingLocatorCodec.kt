package com.yinxi.edgereader.persistence.repository

import com.yinxi.edgereader.model.ReadingLocator

object ReadingLocatorCodec {
    fun encode(locator: ReadingLocator): String = when (locator) {
        is ReadingLocator.TextLocator -> """{"type":"text","characterOffset":${locator.characterOffset},"paragraphIndex":${locator.paragraphIndex ?: "null"},"scrollRatio":${locator.scrollRatio ?: "null"}}"""
        is ReadingLocator.EpubLocator -> throw IllegalArgumentException("EPUB locators are not enabled in Phase 1")
        is ReadingLocator.PdfLocator -> throw IllegalArgumentException("PDF locators are not enabled in Phase 1")
        is ReadingLocator.HtmlLocator -> throw IllegalArgumentException("HTML locators are not enabled in Phase 1")
    }

    fun decode(json: String): ReadingLocator {
        val type = stringValue(json, "type")
        require(type == "text") { "Unsupported locator type: $type" }
        return ReadingLocator.TextLocator(
            characterOffset = longValue(json, "characterOffset") ?: 0,
            paragraphIndex = longValue(json, "paragraphIndex")?.toInt(),
            scrollRatio = doubleValue(json, "scrollRatio"),
        )
    }

    private fun stringValue(json: String, name: String): String? =
        Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(json)?.groupValues?.get(1)

    private fun longValue(json: String, name: String): Long? =
        Regex("\\\"$name\\\"\\s*:\\s*(-?[0-9]+|null)").find(json)?.groupValues?.get(1)?.takeUnless { it == "null" }?.toLong()

    private fun doubleValue(json: String, name: String): Double? =
        Regex("\\\"$name\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?|null)").find(json)?.groupValues?.get(1)?.takeUnless { it == "null" }?.toDouble()
}
