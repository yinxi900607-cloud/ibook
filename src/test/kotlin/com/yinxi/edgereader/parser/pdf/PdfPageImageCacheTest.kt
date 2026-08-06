package com.yinxi.edgereader.parser.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class PdfPageImageCacheTest {
    @Test
    fun `evicts least recently used images by count`() {
        val cache = PdfPageImageCache(maximumEntries = 2, maximumPixels = 1_000)
        val first = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        val second = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        val third = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        cache.put(PdfPageCacheKey(0, 100), first)
        cache.put(PdfPageCacheKey(1, 100), second)
        assertSame(first, cache.get(PdfPageCacheKey(0, 100)))
        cache.put(PdfPageCacheKey(2, 100), third)

        assertNull(cache.get(PdfPageCacheKey(1, 100)))
        assertEquals(2, cache.size())
        cache.close()
        assertEquals(0, cache.size())
    }
}
