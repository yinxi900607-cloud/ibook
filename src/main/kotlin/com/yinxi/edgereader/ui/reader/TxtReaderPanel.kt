package com.yinxi.edgereader.ui.reader

import com.intellij.openapi.components.service
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.parser.txt.TextSlice
import com.yinxi.edgereader.parser.txt.TxtParsedBook
import com.yinxi.edgereader.persistence.settings.ReaderSettingsService
import com.yinxi.edgereader.service.OpenedBook
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import javax.swing.JButton
import javax.swing.SwingUtilities

class TxtReaderPanel(
    private val onBack: () -> Unit,
    private val onOpen: () -> Unit,
    private val onChooseChapter: () -> Unit,
    private val onSettings: () -> Unit,
    private val onRequestSlice: (startOffset: Long, focusOffset: Long) -> Unit,
    private val onLocationChanged: (ReadingLocator.TextLocator, String?, Double) -> Unit,
) : JBPanel<TxtReaderPanel>(BorderLayout()) {
    private val titleLabel = JBLabel()
    private val chapterLabel = JBLabel("—")
    private val positionLabel = JBLabel("0 / 0")
    private val progressLabel = JBLabel("0.0%")
    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(12)
    }
    private val scrollPane = JBScrollPane(textArea)
    private var openedBook: OpenedBook? = null
    private var currentSlice: TextSlice? = null
    private var programmaticScroll = false
    private var loading = false
    private var previousScrollValue = 0

    init {
        border = JBUI.Borders.empty()
        add(createTopToolbar(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)
        scrollPane.verticalScrollBar.addAdjustmentListener { event ->
            if (!programmaticScroll) onScrolled(event.value)
            previousScrollValue = event.value
        }
    }

    fun showBook(book: OpenedBook, initialOffset: Long) {
        openedBook = book
        currentSlice = null
        loading = false
        programmaticScroll = true
        textArea.text = "Loading text index…"
        programmaticScroll = false
        titleLabel.text = book.record.title
        applySettings()
        jumpTo(initialOffset)
    }

    fun setSlice(slice: TextSlice, focusOffset: Long) {
        currentSlice = slice
        loading = false
        programmaticScroll = true
        textArea.text = slice.text
        val caret = (focusOffset - slice.startOffset).coerceIn(0, slice.text.length.toLong()).toInt()
        textArea.caretPosition = caret
        SwingUtilities.invokeLater {
            val rectangle = textArea.modelToView2D(caret)
            if (rectangle != null) {
                scrollPane.viewport.viewPosition = Point(0, maxOf(0, rectangle.y.toInt() - 24))
            }
            previousScrollValue = scrollPane.verticalScrollBar.value
            programmaticScroll = false
            publishLocation()
        }
    }

    fun setLoadFailed() {
        loading = false
    }

    fun applySettings() {
        val state = service<ReaderSettingsService>().state
        val family = state.fontFamily.ifBlank { textArea.font.family }
        textArea.font = Font(family, Font.PLAIN, state.fontSize.coerceIn(12, 36))
        textArea.border = JBUI.Borders.empty(12, state.horizontalMargin.coerceIn(8, 80))
        revalidate()
        repaint()
    }

    fun jumpTo(characterOffset: Long) {
        val book = openedBook ?: return
        val txtBook = book.parsedBook as TxtParsedBook
        val bounded = characterOffset.coerceIn(0, txtBook.index.totalCharacters)
        val start = maxOf(0, bounded - WINDOW_CHARACTERS / 4)
        requestSlice(start, bounded)
    }

    fun nextChapter() {
        val current = currentGlobalOffset()
        (openedBook?.parsedBook as? TxtParsedBook)?.index?.chapters
            ?.firstOrNull { it.offset() > current + 1 }
            ?.let { jumpTo(it.offset()) }
    }

    fun previousChapter() {
        val current = currentGlobalOffset()
        (openedBook?.parsedBook as? TxtParsedBook)?.index?.chapters
            ?.lastOrNull { it.offset() < current - 1 }
            ?.let { jumpTo(it.offset()) }
    }

    fun currentLocator(): ReadingLocator.TextLocator = ReadingLocator.TextLocator(
        characterOffset = currentGlobalOffset(),
        paragraphIndex = null,
        scrollRatio = scrollRatio(),
    )

    fun hasLoadedSlice(): Boolean = currentSlice != null

    private fun createTopToolbar(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(4)
        add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JButton("Library").apply { addActionListener { onBack() } })
            add(JButton("Open").apply { addActionListener { onOpen() } })
            add(JButton("Contents").apply { addActionListener { onChooseChapter() } })
            add(JButton("Reading Settings").apply { addActionListener { onSettings() } })
        }, BorderLayout.WEST)
        add(titleLabel, BorderLayout.EAST)
    }

    private fun createStatusBar(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(4, 6)
        add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(chapterLabel)
            add(positionLabel)
            add(progressLabel)
        }, BorderLayout.WEST)
        add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(JButton("Previous Chapter").apply { addActionListener { previousChapter() } })
            add(JButton("Next Chapter").apply { addActionListener { nextChapter() } })
        }, BorderLayout.EAST)
    }

    private fun onScrolled(value: Int) {
        publishLocation()
        if (loading) return
        val slice = currentSlice ?: return
        val scrollBar = scrollPane.verticalScrollBar
        val movingDown = value > previousScrollValue
        val movingUp = value < previousScrollValue
        if (movingDown && value + scrollBar.visibleAmount >= scrollBar.maximum - 12) {
            val sliceEnd = slice.startOffset + slice.text.length
            if (sliceEnd < slice.totalCharacters) {
                val focus = currentGlobalOffset()
                requestSlice(maxOf(0, sliceEnd - WINDOW_OVERLAP), focus)
            }
        } else if (movingUp && value <= 12 && slice.startOffset > 0) {
            val focus = currentGlobalOffset()
            requestSlice(maxOf(0, slice.startOffset - WINDOW_CHARACTERS + WINDOW_OVERLAP), focus)
        }
    }

    private fun requestSlice(startOffset: Long, focusOffset: Long) {
        if (loading) return
        loading = true
        onRequestSlice(startOffset, focusOffset)
    }

    private fun publishLocation() {
        val book = openedBook ?: return
        val offset = currentGlobalOffset()
        val index = (book.parsedBook as TxtParsedBook).index
        val total = index.totalCharacters
        val percent = if (total <= 0) 0.0 else offset.toDouble() / total * 100.0
        val chapter = index.chapters.lastOrNull { it.offset() <= offset }?.title
        chapterLabel.text = chapter ?: "No chapter"
        positionLabel.text = "$offset / $total"
        progressLabel.text = "%.1f%%".format(percent)
        onLocationChanged(currentLocator(), chapter, percent)
    }

    private fun currentGlobalOffset(): Long {
        val slice = currentSlice ?: return 0
        val point = Point(0, scrollPane.viewport.viewPosition.y)
        val relative = textArea.viewToModel2D(point).coerceAtLeast(0)
        return (slice.startOffset + relative).coerceAtMost(slice.totalCharacters)
    }

    private fun scrollRatio(): Double {
        val scrollBar = scrollPane.verticalScrollBar
        val range = scrollBar.maximum - scrollBar.visibleAmount
        return if (range <= 0) 0.0 else scrollBar.value.toDouble() / range
    }

    private fun BookNavigationItem.offset(): Long = (locator as ReadingLocator.TextLocator).characterOffset

    companion object {
        const val WINDOW_CHARACTERS = 128 * 1024L
        private const val WINDOW_OVERLAP = 4 * 1024L
    }
}
