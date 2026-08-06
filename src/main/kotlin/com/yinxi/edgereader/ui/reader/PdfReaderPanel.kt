package com.yinxi.edgereader.ui.reader

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.parser.pdf.PdfParsedBook
import com.yinxi.edgereader.parser.pdf.PdfRenderedPage
import com.yinxi.edgereader.parser.pdf.PdfZoomMode
import com.yinxi.edgereader.service.OpenedBook
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

class PdfReaderPanel(
    private val onBack: () -> Unit,
    private val onOpen: () -> Unit,
    private val onChooseChapter: () -> Unit,
    private val onSearch: () -> Unit,
    private val onRequestPage: (Int, Int, PdfZoomMode, Float, Long) -> Unit,
    private val onLocationChanged: (ReadingLocator.PdfLocator, String?, Double) -> Unit,
) : JBPanel<PdfReaderPanel>(BorderLayout()) {
    private val titleLabel = JBLabel()
    private val statusLabel = JBLabel("—")
    private val progressLabel = JBLabel("0.0%")
    private val zoomLabel = JBLabel("Fit width")
    private val textStatusLabel = JBLabel()
    private val imageLabel = JBLabel("Open a PDF to begin", SwingConstants.CENTER).apply {
        verticalAlignment = SwingConstants.TOP
        border = JBUI.Borders.empty(12)
    }
    private val scrollPane = JBScrollPane(imageLabel)
    private val pageModel = SpinnerNumberModel(1, 1, 1, 1)
    private val pageSpinner = JSpinner(pageModel)
    private var openedBook: OpenedBook? = null
    private var currentPage = 0
    private var zoomMode = PdfZoomMode.FIT_WIDTH
    private var customScale = 1f
    private var requestedVerticalRatio = 0.0
    private var generation = 0L
    private var loading = false
    private var changingSpinner = false
    private var programmaticScroll = false
    private val resizeTimer = Timer(250) {
        if (zoomMode == PdfZoomMode.FIT_WIDTH && openedBook != null) requestCurrentPage()
    }.apply { isRepeats = false }

    init {
        border = JBUI.Borders.empty()
        add(createTopToolbar(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)
        pageSpinner.addChangeListener {
            if (!changingSpinner) showPage((pageSpinner.value as Int) - 1)
        }
        scrollPane.verticalScrollBar.addAdjustmentListener {
            if (!programmaticScroll && !loading) publishLocation()
        }
        scrollPane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                resizeTimer.restart()
            }
        })
    }

    fun showBook(book: OpenedBook, locator: ReadingLocator.PdfLocator?) {
        openedBook = book
        val pdf = book.parsedBook as PdfParsedBook
        titleLabel.text = book.record.title
        textStatusLabel.text = if (pdf.hasSearchableText) "" else "No searchable text detected"
        currentPage = locator?.pageIndex?.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0)) ?: 0
        zoomMode = runCatching { PdfZoomMode.valueOf(locator?.zoomMode.orEmpty()) }.getOrDefault(PdfZoomMode.FIT_WIDTH)
        customScale = locator?.zoomScale?.toFloat()?.coerceIn(0.5f, 4f) ?: 1f
        requestedVerticalRatio = locator?.verticalRatio?.coerceIn(0.0, 1.0) ?: 0.0
        changingSpinner = true
        pageModel.maximum = pdf.pageCount.coerceAtLeast(1)
        pageSpinner.value = currentPage + 1
        changingSpinner = false
        requestCurrentPage()
    }

    fun setPage(rendered: PdfRenderedPage, requestGeneration: Long) {
        if (requestGeneration != generation || rendered.pageIndex != currentPage) return
        loading = false
        programmaticScroll = true
        imageLabel.text = null
        imageLabel.icon = ImageIcon(rendered.image)
        zoomLabel.text = if (zoomMode == PdfZoomMode.FIT_WIDTH) "Fit width" else "${(customScale * 100).toInt()}%"
        SwingUtilities.invokeLater {
            val bar = scrollPane.verticalScrollBar
            val range = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
            bar.value = (requestedVerticalRatio * range).toInt()
            requestedVerticalRatio = 0.0
            programmaticScroll = false
            publishLocation()
        }
    }

    fun setLoadFailed(requestGeneration: Long) {
        if (requestGeneration != generation) return
        loading = false
        imageLabel.icon = null
        imageLabel.text = "Unable to render this PDF page"
    }

    fun nextPage() {
        val pageCount = (openedBook?.parsedBook as? PdfParsedBook)?.pageCount ?: return
        if (currentPage + 1 < pageCount) showPage(currentPage + 1)
    }

    fun previousPage() {
        if (currentPage > 0) showPage(currentPage - 1)
    }

    fun jumpTo(locator: ReadingLocator.PdfLocator) {
        zoomMode = runCatching { PdfZoomMode.valueOf(locator.zoomMode.orEmpty()) }.getOrDefault(zoomMode)
        customScale = locator.zoomScale?.toFloat()?.coerceIn(0.5f, 4f) ?: customScale
        requestedVerticalRatio = locator.verticalRatio.coerceIn(0.0, 1.0)
        showPage(locator.pageIndex)
    }

    fun currentLocator(): ReadingLocator.PdfLocator = ReadingLocator.PdfLocator(
        pageIndex = currentPage,
        verticalRatio = verticalRatio(),
        zoomMode = zoomMode.name,
        zoomScale = if (zoomMode == PdfZoomMode.CUSTOM) customScale.toDouble() else null,
    )

    fun hasRenderedPage(): Boolean = imageLabel.icon != null

    fun disposeReader() {
        resizeTimer.stop()
        imageLabel.icon = null
    }

    private fun showPage(pageIndex: Int) {
        val pdf = openedBook?.parsedBook as? PdfParsedBook ?: return
        currentPage = pageIndex.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))
        requestedVerticalRatio = 0.0
        changingSpinner = true
        pageSpinner.value = currentPage + 1
        changingSpinner = false
        requestCurrentPage()
    }

    private fun requestCurrentPage() {
        if (openedBook == null) return
        generation++
        loading = true
        imageLabel.icon = null
        imageLabel.text = "Rendering page ${currentPage + 1}…"
        onRequestPage(
            currentPage,
            scrollPane.viewport.extentSize.width.coerceAtLeast(width),
            zoomMode,
            customScale,
            generation,
        )
    }

    private fun setFitWidth() {
        requestedVerticalRatio = verticalRatio()
        zoomMode = PdfZoomMode.FIT_WIDTH
        requestCurrentPage()
    }

    private fun changeZoom(factor: Float) {
        requestedVerticalRatio = verticalRatio()
        if (zoomMode == PdfZoomMode.FIT_WIDTH) customScale = 1f
        zoomMode = PdfZoomMode.CUSTOM
        customScale = (customScale * factor).coerceIn(0.5f, 4f)
        requestCurrentPage()
    }

    private fun publishLocation() {
        val pdf = openedBook?.parsedBook as? PdfParsedBook ?: return
        if (pdf.pageCount <= 0) return
        val percent = (currentPage + verticalRatio()) / pdf.pageCount * 100.0
        statusLabel.text = "Page ${currentPage + 1} / ${pdf.pageCount}"
        progressLabel.text = "%.1f%%".format(percent)
        onLocationChanged(currentLocator(), "Page ${currentPage + 1}", percent)
    }

    private fun verticalRatio(): Double {
        val bar = scrollPane.verticalScrollBar
        val range = bar.maximum - bar.visibleAmount
        return if (range <= 0) 0.0 else bar.value.toDouble() / range
    }

    private fun createTopToolbar(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(4)
        add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JButton("Library").apply { addActionListener { onBack() } })
            add(JButton("Open").apply { addActionListener { onOpen() } })
            add(JButton("Contents").apply { addActionListener { onChooseChapter() } })
            add(JButton("Search").apply { addActionListener { onSearch() } })
        }, BorderLayout.WEST)
        add(titleLabel, BorderLayout.EAST)
    }

    private fun createStatusBar(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(4, 6)
        add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(statusLabel)
            add(progressLabel)
            add(zoomLabel)
            add(textStatusLabel)
        }, BorderLayout.WEST)
        add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(JButton("−").apply { toolTipText = "Zoom out"; addActionListener { changeZoom(0.8f) } })
            add(JButton("+").apply { toolTipText = "Zoom in"; addActionListener { changeZoom(1.25f) } })
            add(JButton("Fit").apply { addActionListener { setFitWidth() } })
            add(JButton("Previous").apply { addActionListener { previousPage() } })
            add(pageSpinner)
            add(JButton("Next").apply { addActionListener { nextPage() } })
        }, BorderLayout.EAST)
    }
}
