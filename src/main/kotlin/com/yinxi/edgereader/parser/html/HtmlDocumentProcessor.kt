package com.yinxi.edgereader.parser.html

import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.security.SanitizedHtml
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.nio.file.Path

object HtmlDocumentProcessor {
    fun process(sanitized: SanitizedHtml, sourceFile: Path): HtmlDocumentContent {
        val document = Jsoup.parse(sanitized.html, sourceFile.toUri().toString(), Parser.htmlParser())
        val usedIds = document.select("[id]").mapTo(mutableSetOf()) { it.id() }
        val visibleText = document.body().text()
        var headingSearchFrom = 0
        val navigation = document.select("h1, h2, h3, h4, h5, h6").mapIndexed { index, heading ->
            val title = heading.text().ifBlank { "Untitled section" }
            val textOffset = visibleText.indexOf(title, headingSearchFrom).takeIf { it >= 0 }
            if (textOffset != null) headingSearchFrom = textOffset + title.length
            val id = heading.id().takeIf(String::isNotBlank) ?: uniqueId(slug(title).ifBlank { "section-${index + 1}" }, usedIds)
            heading.attr("id", id)
            usedIds += id
            BookNavigationItem(
                title = title,
                locator = ReadingLocator.HtmlLocator(sourceFile.fileName.toString(), id, textOffset, null),
                level = heading.tagName().removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1,
            )
        }
        return HtmlDocumentContent(document.outerHtml(), visibleText, navigation)
    }

    fun title(content: HtmlDocumentContent, fallback: String): String {
        val document = Jsoup.parse(content.html)
        return document.title().trim().takeIf(String::isNotBlank)
            ?: document.selectFirst("h1")?.text()?.trim()?.takeIf(String::isNotBlank)
            ?: fallback
    }

    private fun uniqueId(base: String, used: Set<String>): String {
        if (base !in used) return base
        var suffix = 2
        while ("$base-$suffix" in used) suffix++
        return "$base-$suffix"
    }

    private fun slug(value: String): String = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
}
