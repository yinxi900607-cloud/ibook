package com.yinxi.edgereader.parser.epub

import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.parser.ParsedBook
import com.yinxi.edgereader.security.HtmlSanitizer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: Set<String>,
    val file: Path,
)

data class EpubSpineItem(
    val idref: String,
    val manifestItem: EpubManifestItem,
    val linear: Boolean,
)

data class EpubChapterContent(
    val spineItemId: String,
    val chapterHref: String,
    val title: String,
    val html: String,
    val visibleText: String,
    val sourceFile: Path,
)

class EpubParsedBook(
    override val bookId: String,
    override val file: Path,
    override val metadata: BookMetadata,
    val extractionRoot: Path,
    val packageFile: Path,
    val coverFile: Path?,
    val manifest: Map<String, EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    val navigation: List<BookNavigationItem>,
    private val sanitizer: HtmlSanitizer,
) : ParsedBook {
    private val chapterCache = ConcurrentHashMap<Int, EpubChapterContent>()

    fun chapter(index: Int): EpubChapterContent {
        require(index in spine.indices) { "EPUB chapter index is out of range" }
        return chapterCache.computeIfAbsent(index) {
            val item = spine[index]
            val sanitized = sanitizer.sanitize(item.manifestItem.file)
            EpubChapterContent(
                spineItemId = item.idref,
                chapterHref = item.manifestItem.href,
                title = titleFor(item.manifestItem.href, index),
                html = sanitized.html,
                visibleText = sanitized.visibleText,
                sourceFile = item.manifestItem.file,
            )
        }
    }

    fun chapterIndex(href: String, spineItemId: String? = null): Int {
        if (spineItemId != null) spine.indexOfFirst { it.idref == spineItemId }.takeIf { it >= 0 }?.let { return it }
        val normalized = href.substringBefore('#').replace('\\', '/')
        return spine.indexOfFirst { it.manifestItem.href.substringBefore('#').replace('\\', '/') == normalized }
            .coerceAtLeast(0)
    }

    private fun titleFor(href: String, index: Int): String = navigation.firstOrNull {
        val locator = it.locator as? com.yinxi.edgereader.model.ReadingLocator.EpubLocator
        locator?.chapterHref?.substringBefore('#') == href.substringBefore('#')
    }?.title ?: "Chapter ${index + 1}"

    override fun close() {
        chapterCache.clear()
    }
}
