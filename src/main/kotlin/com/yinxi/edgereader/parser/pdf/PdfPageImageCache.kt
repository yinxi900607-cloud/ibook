package com.yinxi.edgereader.parser.pdf

import java.awt.image.BufferedImage
import java.io.Closeable
import java.util.LinkedHashMap

data class PdfPageCacheKey(
    val pageIndex: Int,
    val scaleKey: Int,
)

class PdfPageImageCache(
    private val maximumEntries: Int = 5,
    private val maximumPixels: Long = 48_000_000,
) : Closeable {
    private val images = LinkedHashMap<PdfPageCacheKey, BufferedImage>(16, 0.75f, true)
    private var pixelCount = 0L

    @Synchronized
    fun get(key: PdfPageCacheKey): BufferedImage? = images[key]

    @Synchronized
    fun put(key: PdfPageCacheKey, image: BufferedImage) {
        images.remove(key)?.let { previous ->
            removePixels(previous)
            if (previous !== image) previous.flush()
        }
        images[key] = image
        pixelCount += pixels(image)
        evictIfNecessary()
    }

    @Synchronized
    fun size(): Int = images.size

    @Synchronized
    fun currentPixels(): Long = pixelCount

    @Synchronized
    override fun close() {
        images.values.forEach(BufferedImage::flush)
        images.clear()
        pixelCount = 0
    }

    private fun evictIfNecessary() {
        val iterator = images.entries.iterator()
        while ((images.size > maximumEntries || pixelCount > maximumPixels) && iterator.hasNext()) {
            val entry = iterator.next()
            iterator.remove()
            removePixels(entry.value)
            entry.value.flush()
        }
    }

    private fun removePixels(image: BufferedImage) {
        pixelCount = (pixelCount - pixels(image)).coerceAtLeast(0)
    }

    private fun pixels(image: BufferedImage): Long = image.width.toLong() * image.height.toLong()
}
