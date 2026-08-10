package com.yinxi.edgereader.ui.settings

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Applies reading colors directly to the display-only HTML passed to Swing.
 *
 * Swing's CSS implementation does not support `!important`, so EPUB author
 * styles can otherwise override the reader theme. Inline declarations have
 * the precedence needed here and do not mutate the extracted EPUB files.
 */
object ReaderHtmlTheme {
    private const val TEXT_ELEMENTS =
        "p, div, span, h1, h2, h3, h4, h5, h6, li, dt, dd, td, th, blockquote, pre, code, aside, " +
            "figcaption, caption, strong, b, em, i, u, s, small, big, sup, sub"

    fun apply(html: String, palette: ReaderPalette): String {
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)

        val body = document.body()
        body.appendStyle(
            "background-color: ${palette.backgroundCss()}; background-image: none; color: ${palette.foregroundCss()}",
        )
        body.attr("bgcolor", palette.backgroundCss())
        body.attr("text", palette.foregroundCss())
        body.select(TEXT_ELEMENTS).forEach { element ->
            element.appendStyle("color: ${palette.foregroundCss()}")
        }

        return document.outerHtml()
    }

    private fun Element.appendStyle(declarations: String) {
        val current = attr("style").trim().trimEnd(';')
        attr("style", if (current.isEmpty()) declarations else "$current; $declarations")
    }
}
