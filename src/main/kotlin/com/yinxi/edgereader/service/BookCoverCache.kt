package com.yinxi.edgereader.service

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO

class BookCoverCache(
    private val coversDirectory: Path,
) {
    fun createThumbnail(bookId: String, source: Path): Path? {
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_SOURCE_BYTES) return null
        val image = readBounded(source) ?: return null
        val scale = minOf(MAX_WIDTH.toDouble() / image.width, MAX_HEIGHT.toDouble() / image.height, 1.0)
        val width = maxOf(1, (image.width * scale).toInt())
        val height = maxOf(1, (image.height * scale).toInt())
        val thumbnail = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        thumbnail.createGraphics().use { graphics ->
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(image, 0, 0, width, height, null)
        }
        Files.createDirectories(coversDirectory)
        val destination = coversDirectory.resolve("$bookId.png")
        val temporary = coversDirectory.resolve(".$bookId-${UUID.randomUUID()}.tmp")
        return try {
            if (!ImageIO.write(thumbnail, "png", temporary.toFile())) return null
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
            destination
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun readBounded(source: Path): BufferedImage? = ImageIO.createImageInputStream(source.toFile())?.use { input ->
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) return@use null
        val reader = readers.next()
        try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            if (width <= 0 || height <= 0 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION ||
                width.toLong() * height > MAX_SOURCE_PIXELS
            ) return@use null
            reader.read(0)
        } finally {
            reader.dispose()
        }
    }

    private inline fun <T : java.awt.Graphics2D, R> T.use(block: (T) -> R): R = try {
        block(this)
    } finally {
        dispose()
    }

    companion object {
        const val MAX_WIDTH = 64
        const val MAX_HEIGHT = 88
        private const val MAX_SOURCE_BYTES = 25L * 1024 * 1024
        private const val MAX_SOURCE_DIMENSION = 20_000
        private const val MAX_SOURCE_PIXELS = 40_000_000L
    }
}
