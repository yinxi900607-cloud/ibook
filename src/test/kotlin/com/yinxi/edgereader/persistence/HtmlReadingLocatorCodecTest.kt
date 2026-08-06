package com.yinxi.edgereader.persistence

import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HtmlReadingLocatorCodecTest {
    @Test
    fun `round trips HTML stable locator`() {
        val locator = ReadingLocator.HtmlLocator("chapter.html", "part-二", 882, 0.61)
        assertEquals(locator, ReadingLocatorCodec.decode(ReadingLocatorCodec.encode(locator)))
    }
}
