package com.yinxi.edgereader.model

sealed interface ReadingLocator {
    data class TextLocator(
        val characterOffset: Long,
        val paragraphIndex: Int?,
        val scrollRatio: Double?,
    ) : ReadingLocator

    data class EpubLocator(
        val spineItemId: String?,
        val chapterHref: String,
        val elementId: String?,
        val normalizedTextOffset: Int?,
        val scrollRatio: Double?,
    ) : ReadingLocator

    data class PdfLocator(
        val pageIndex: Int,
        val verticalRatio: Double,
        val zoomMode: String?,
        val zoomScale: Double?,
    ) : ReadingLocator

    data class HtmlLocator(
        val documentPath: String?,
        val elementId: String?,
        val normalizedTextOffset: Int?,
        val scrollRatio: Double?,
    ) : ReadingLocator
}
