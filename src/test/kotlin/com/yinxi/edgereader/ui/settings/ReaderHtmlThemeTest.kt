package com.yinxi.edgereader.ui.settings

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderHtmlThemeTest {
    @Test
    fun `reader colors override EPUB body stylesheet and inline black text`() {
        val html = """
            <html>
              <head>
                <link href="file:///book/cover.css" rel="stylesheet">
                <style>body { background-color: #FFFFFF; } p { color: #000; }</style>
              </head>
              <body>
                <h1>Heading</h1>
                <p><span style="font-size:16px;color:rgb(0, 0, 0)">Text</span></p>
                <img src="file:///book/image.jpg">
              </body>
            </html>
        """.trimIndent()

        val themed = ReaderHtmlTheme.apply(html, ReaderTheme.DARK.palette())
        val document = Jsoup.parse(themed)
        val bodyStyle = document.body().attr("style")
        val paragraphStyle = document.selectFirst("p")!!.attr("style")
        val spanStyle = document.selectFirst("span")!!.attr("style")

        assertTrue(bodyStyle.endsWith("background-color: #2b2d30; background-image: none; color: #dfe1e5"))
        assertEquals("#2b2d30", document.body().attr("bgcolor"))
        assertEquals("#dfe1e5", document.body().attr("text"))
        assertTrue(paragraphStyle.endsWith("color: #dfe1e5"))
        assertTrue(spanStyle.endsWith("color: #dfe1e5"))
        assertTrue(spanStyle.contains("font-size:16px"))
        assertTrue(document.selectFirst("link")!!.attr("href").endsWith("cover.css"))
        assertTrue(document.selectFirst("img")!!.attr("src").endsWith("image.jpg"))
    }
}
