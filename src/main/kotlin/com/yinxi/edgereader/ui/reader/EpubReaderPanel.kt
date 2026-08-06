package com.yinxi.edgereader.ui.reader

import com.intellij.openapi.components.service
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.parser.epub.EpubChapterContent
import com.yinxi.edgereader.parser.epub.EpubParsedBook
import com.yinxi.edgereader.persistence.settings.ReaderSettingsService
import com.yinxi.edgereader.service.OpenedBook
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Point
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

class EpubReaderPanel(
    private val onBack: () -> Unit,
    private val onOpen: () -> Unit,
    private val onChooseChapter: () -> Unit,
    private val onSettings: () -> Unit,
    private val onRequestChapter: (Int, ReadingLocator.EpubLocator?) -> Unit,
    private val onNavigateLink: (String) -> Unit,
    private val onLocationChanged: (ReadingLocator.EpubLocator, String?, Double) -> Unit,
) : JBPanel<EpubReaderPanel>(BorderLayout()) {
    private val titleLabel = JBLabel()
    private val chapterLabel = JBLabel("—")
    private val positionLabel = JBLabel("0 / 0")
    private val progressLabel = JBLabel("0.0%")
    private val editorPane = JEditorPane().apply {
        isEditable = false
        contentType = "text/html"
        border = JBUI.Borders.empty(12)
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                (event.url?.toExternalForm() ?: event.description)?.let(onNavigateLink)
            }
        }
    }
    private val scrollPane = JBScrollPane(editorPane)
    private var openedBook: OpenedBook? = null
    private var currentChapter: EpubChapterContent? = null
    private var currentChapterIndex = 0
    private var loading = false
    private var programmaticScroll = false
    private var requestedLocator: ReadingLocator.EpubLocator? = null

    init {
        border = JBUI.Borders.empty()
        add(createTopToolbar(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)
        scrollPane.verticalScrollBar.addAdjustmentListener {
            if (!programmaticScroll && !loading) publishLocation()
        }
    }

    fun showBook(book: OpenedBook, locator: ReadingLocator.EpubLocator?) {
        openedBook = book
        currentChapter = null
        titleLabel.text = book.record.title
        val epub = book.parsedBook as EpubParsedBook
        currentChapterIndex = locator?.let { epub.chapterIndex(it.chapterHref, it.spineItemId) } ?: 0
        requestChapter(currentChapterIndex, locator)
    }

    fun setChapter(content: EpubChapterContent, target: ReadingLocator.EpubLocator?) {
        currentChapter = content
        requestedLocator = target
        loading = false
        programmaticScroll = true
        editorPane.editorKit = createEditorKit()
        editorPane.text = content.html
        editorPane.caretPosition = 0
        SwingUtilities.invokeLater {
            restoreLocation(target)
            programmaticScroll = false
            publishLocation()
        }
    }

    fun setLoadFailed() {
        loading = false
    }

    fun applySettings() {
        val content = currentChapter ?: return
        val locator = currentLocator()
        setChapter(content, locator)
    }

    fun jumpTo(locator: ReadingLocator.EpubLocator) {
        val epub = openedBook?.parsedBook as? EpubParsedBook ?: return
        requestChapter(epub.chapterIndex(locator.chapterHref, locator.spineItemId), locator)
    }

    fun nextChapter() {
        val epub = openedBook?.parsedBook as? EpubParsedBook ?: return
        if (currentChapterIndex + 1 < epub.spine.size) requestChapter(currentChapterIndex + 1, null)
    }

    fun previousChapter() {
        if (currentChapterIndex > 0) requestChapter(currentChapterIndex - 1, null)
    }

    fun currentLocator(): ReadingLocator.EpubLocator {
        val chapter = currentChapter
        val offset = visibleDocumentOffset()
        return ReadingLocator.EpubLocator(
            spineItemId = chapter?.spineItemId,
            chapterHref = chapter?.chapterHref ?: requestedLocator?.chapterHref.orEmpty(),
            elementId = nearestElementId(offset),
            normalizedTextOffset = offset,
            scrollRatio = scrollRatio(),
        )
    }

    fun hasLoadedChapter(): Boolean = currentChapter != null

    private fun requestChapter(index: Int, locator: ReadingLocator.EpubLocator?) {
        if (loading) return
        loading = true
        currentChapterIndex = index
        requestedLocator = locator
        editorPane.text = "<html><body>Loading EPUB chapter…</body></html>"
        onRequestChapter(index, locator)
    }

    private fun createEditorKit(): HTMLEditorKit {
        val state = service<ReaderSettingsService>().state
        return HTMLEditorKit().apply {
            styleSheet.addRule(
                "body { font-family: '${state.fontFamily.replace("'", "")}' ; font-size: ${state.fontSize.coerceIn(12, 36)}pt; " +
                    "line-height: ${state.lineSpacing.coerceIn(1.0f, 2.5f)}; " +
                    "margin-left: ${state.horizontalMargin.coerceIn(8, 80)}px; margin-right: ${state.horizontalMargin.coerceIn(8, 80)}px; }",
            )
            styleSheet.addRule("img { max-width: 100%; }")
        }
    }

    private fun restoreLocation(locator: ReadingLocator.EpubLocator?) {
        val document = editorPane.document as? HTMLDocument ?: return
        val idOffset = locator?.elementId?.let(document::getElement)?.startOffset
        val textOffset = locator?.normalizedTextOffset?.coerceIn(0, document.length)
        val offset = idOffset ?: textOffset
        if (offset != null) {
            editorPane.caretPosition = offset
            editorPane.modelToView2D(offset)?.let { scrollPane.viewport.viewPosition = Point(0, maxOf(0, it.y.toInt() - 16)) }
        } else {
            val ratio = locator?.scrollRatio ?: 0.0
            val bar = scrollPane.verticalScrollBar
            val range = maxOf(0, bar.maximum - bar.visibleAmount)
            bar.value = (ratio.coerceIn(0.0, 1.0) * range).toInt()
        }
    }

    private fun publishLocation() {
        val epub = openedBook?.parsedBook as? EpubParsedBook ?: return
        val chapter = currentChapter ?: return
        val ratio = scrollRatio()
        val percent = if (epub.spine.isEmpty()) 0.0 else (currentChapterIndex + ratio) / epub.spine.size * 100.0
        chapterLabel.text = chapter.title
        positionLabel.text = "${currentChapterIndex + 1} / ${epub.spine.size}"
        progressLabel.text = "%.1f%%".format(percent)
        onLocationChanged(currentLocator(), chapter.title, percent)
    }

    private fun visibleDocumentOffset(): Int {
        val point = Point(0, scrollPane.viewport.viewPosition.y)
        return editorPane.viewToModel2D(point).coerceIn(0, editorPane.document.length)
    }

    private fun nearestElementId(offset: Int): String? {
        val document = editorPane.document as? HTMLDocument ?: return null
        var element = document.getCharacterElement(offset)
        while (true) {
            val id = element.attributes.getAttribute(javax.swing.text.html.HTML.Attribute.ID)?.toString()
            if (!id.isNullOrBlank()) return id
            element = element.parentElement ?: return null
        }
    }

    private fun scrollRatio(): Double {
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
}
