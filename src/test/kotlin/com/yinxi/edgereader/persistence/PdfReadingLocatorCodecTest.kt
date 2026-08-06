package com.yinxi.edgereader.persistence

import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfReadingLocatorCodecTest {
    @Test
    fun `round trips PDF page vertical position and zoom`() {
        val locator = ReadingLocator.PdfLocator(
            pageIndex = 781,
            verticalRatio = 0.625,
            zoomMode = "CUSTOM",
            zoomScale = 1.75,
        )
        assertEquals(locator, ReadingLocatorCodec.decode(ReadingLocatorCodec.encode(locator)))
    }
}
