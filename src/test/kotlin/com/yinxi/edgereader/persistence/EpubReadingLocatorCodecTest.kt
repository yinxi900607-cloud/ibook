package com.yinxi.edgereader.persistence

import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpubReadingLocatorCodecTest {
    @Test
    fun `round trips a stable EPUB locator including escaped values`() {
        val locator = ReadingLocator.EpubLocator(
            spineItemId = "chapter-1",
            chapterHref = "OEBPS/text/a \\\"chapter\\\".xhtml",
            elementId = "part-一",
            normalizedTextOffset = 1234,
            scrollRatio = 0.42,
        )
        assertEquals(locator, ReadingLocatorCodec.decode(ReadingLocatorCodec.encode(locator)))
    }
}
