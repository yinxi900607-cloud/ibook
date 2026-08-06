package com.yinxi.edgereader.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class BookCoverCacheTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `creates a bounded PNG thumbnail in the cover cache`() {
        val source = tempDirectory.resolve("cover.jpg")
        val image = BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(40, 80, 120)
            fillRect(0, 0, image.width, image.height)
            dispose()
        }
        assertTrue(ImageIO.write(image, "jpg", source.toFile()))

        val thumbnail = BookCoverCache(tempDirectory.resolve("covers")).createThumbnail("book-1", source)
        assertNotNull(thumbnail)
        val cachedImage = ImageIO.read(thumbnail!!.toFile())
        assertTrue(cachedImage.width <= BookCoverCache.MAX_WIDTH)
        assertTrue(cachedImage.height <= BookCoverCache.MAX_HEIGHT)
        assertEquals("book-1.png", thumbnail.fileName.toString())
    }

    @Test
    fun `ignores unsupported cover data`() {
        val source = tempDirectory.resolve("cover.bin")
        Files.writeString(source, "not an image")
        assertNull(BookCoverCache(tempDirectory.resolve("covers")).createThumbnail("book-2", source))
    }
}
