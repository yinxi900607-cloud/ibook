package com.yinxi.edgereader.parser.pdf

import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.parser.ParsedBook
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.math.roundToInt

enum class PdfZoomMode {
    FIT_WIDTH,
    CUSTOM,
}

data class PdfPageMetrics(
    val widthPoints: Float,
    val heightPoints: Float,
)

data class PdfRenderedPage(
    val pageIndex: Int,
    val image: BufferedImage,
    val renderScale: Float,
)

class PdfParsedBook(
    override val bookId: String,
    override val file: Path,
    override val metadata: BookMetadata,
    internal val document: PDDocument,
    val navigation: List<BookNavigationItem>,
    val hasSearchableText: Boolean,
    private val imageCache: PdfPageImageCache = PdfPageImageCache(),
) : ParsedBook {
    private val lock = Any()
    private val renderer = PDFRenderer(document)
    @Volatile
    private var closed = false

    val pageCount: Int = synchronized(lock) { document.numberOfPages }

    fun pageMetrics(pageIndex: Int): PdfPageMetrics = synchronized(lock) {
        ensureOpen()
        requirePage(pageIndex)
        val box = document.getPage(pageIndex).cropBox
        PdfPageMetrics(box.width, box.height)
    }

    fun renderPage(pageIndex: Int, requestedScale: Float): PdfRenderedPage {
        require(requestedScale.isFinite() && requestedScale > 0) { "Invalid PDF render scale" }
        val safeScale = constrainedScale(pageIndex, requestedScale)
        val key = PdfPageCacheKey(pageIndex, (safeScale * SCALE_QUANTIZATION).roundToInt())
        imageCache.get(key)?.let { return PdfRenderedPage(pageIndex, it, safeScale) }
        val rendered = synchronized(lock) {
            ensureOpen()
            requirePage(pageIndex)
            renderer.renderImage(pageIndex, safeScale, ImageType.RGB)
        }
        imageCache.put(key, rendered)
        return PdfRenderedPage(pageIndex, rendered, safeScale)
    }

    fun extractPageText(pageIndex: Int): String = synchronized(lock) {
        ensureOpen()
        requirePage(pageIndex)
        if (!document.currentAccessPermission.canExtractContent()) return@synchronized ""
        PDFTextStripper().apply {
            sortByPosition = true
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }.getText(document)
    }

    internal fun cachedPageCount(): Int = imageCache.size()

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            imageCache.close()
            document.close()
        }
    }

    private fun constrainedScale(pageIndex: Int, requestedScale: Float): Float {
        val metrics = pageMetrics(pageIndex)
        val dimensionLimit = minOf(
            MAX_IMAGE_DIMENSION / metrics.widthPoints.coerceAtLeast(1f),
            MAX_IMAGE_DIMENSION / metrics.heightPoints.coerceAtLeast(1f),
        )
        val pixelLimit = kotlin.math.sqrt(
            MAX_PAGE_PIXELS.toDouble() /
                (metrics.widthPoints.coerceAtLeast(1f) * metrics.heightPoints.coerceAtLeast(1f)),
        ).toFloat()
        return requestedScale.coerceAtMost(minOf(dimensionLimit, pixelLimit)).coerceAtLeast(MIN_RENDER_SCALE)
    }

    private fun requirePage(pageIndex: Int) {
        require(pageIndex in 0 until pageCount) { "PDF page is out of range" }
    }

    private fun ensureOpen() = check(!closed) { "PDF document is already closed" }

    companion object {
        private const val SCALE_QUANTIZATION = 100
        private const val MIN_RENDER_SCALE = 0.1f
        private const val MAX_IMAGE_DIMENSION = 8_192f
        private const val MAX_PAGE_PIXELS = 32_000_000L
    }
}

class InvalidPdfException(message: String, cause: Throwable? = null) : Exception(message, cause)
