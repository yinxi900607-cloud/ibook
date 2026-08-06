package com.yinxi.edgereader.ui.reader

import com.intellij.openapi.components.service
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.parser.html.HtmlParsedBook
import com.yinxi.edgereader.persistence.settings.ReaderSettingsService
import com.yinxi.edgereader.service.OpenedBook
import com.yinxi.edgereader.ui.EdgeReaderIcons
import com.yinxi.edgereader.ui.EdgeReaderUi
import com.yinxi.edgereader.ui.settings.ReaderTheme
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Point
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

class HtmlReaderPanel(
    private val onBack: () -> Unit,
    private val onOpen: () -> Unit,
    private val onChooseChapter: () -> Unit,
    private val onSearch: () -> Unit,
    private val onShowBookmarks: () -> Unit,
    private val onAddBookmark: () -> Unit,
    private val onSettings: () -> Unit,
    private val onNavigateLink: (String) -> Unit,
    private val onLocationChanged: (ReadingLocator.HtmlLocator, String?, Double) -> Unit,
) : JBPanel<HtmlReaderPanel>(BorderLayout()) {
    private val titleLabel = JBLabel()
    private val positionLabel = JBLabel("0.0%")
    private val sectionLabel = JBLabel("—")
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
    private var programmaticScroll = false

    init {
        border = JBUI.Borders.empty()
        add(createHeader(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)
        EdgeReaderUi.secondary(sectionLabel)
        EdgeReaderUi.secondary(positionLabel)
        scrollPane.verticalScrollBar.addAdjustmentListener {
            if (!programmaticScroll) publishLocation()
        }
    }

    fun showBook(book: OpenedBook, locator: ReadingLocator.HtmlLocator?) {
        openedBook = book
        val parsed = book.parsedBook as HtmlParsedBook
        titleLabel.text = book.record.title
        titleLabel.toolTipText = book.record.title
        programmaticScroll = true
        editorPane.editorKit = createEditorKit()
        editorPane.text = parsed.content.html
        editorPane.caretPosition = 0
        SwingUtilities.invokeLater {
            restoreLocation(locator)
            programmaticScroll = false
            publishLocation()
        }
    }

    fun applySettings() {
        val book = openedBook ?: return
        val locator = currentLocator()
        showBook(book, locator)
    }

    fun jumpTo(locator: ReadingLocator.HtmlLocator) {
        restoreLocation(locator)
        publishLocation()
    }

    fun currentLocator(): ReadingLocator.HtmlLocator {
        val offset = visibleDocumentOffset()
        return ReadingLocator.HtmlLocator(
            documentPath = openedBook?.parsedBook?.file?.fileName?.toString(),
            elementId = nearestElementId(offset),
            normalizedTextOffset = offset,
            scrollRatio = scrollRatio(),
        )
    }

    fun currentSectionTitle(): String? {
        val parsed = openedBook?.parsedBook as? HtmlParsedBook ?: return null
        val offset = currentLocator().normalizedTextOffset ?: 0
        return parsed.content.navigation.lastOrNull {
            ((it.locator as? ReadingLocator.HtmlLocator)?.normalizedTextOffset ?: Int.MAX_VALUE) <= offset
        }?.title
    }

    fun currentExcerpt(): String? {
        val parsed = openedBook?.parsedBook as? HtmlParsedBook ?: return null
        val offset = currentLocator().normalizedTextOffset ?: return null
        val text = parsed.content.visibleText
        if (text.isEmpty()) return null
        val bounded = offset.coerceIn(0, text.length)
        return text.substring((bounded - 40).coerceAtLeast(0), (bounded + 100).coerceAtMost(text.length)).trim()
    }

    fun hasDocument(): Boolean = openedBook != null && editorPane.document.length > 0

    fun nextSection() {
        val parsed = openedBook?.parsedBook as? HtmlParsedBook ?: return
        val offset = currentLocator().normalizedTextOffset ?: 0
        parsed.content.navigation.firstOrNull {
            ((it.locator as? ReadingLocator.HtmlLocator)?.normalizedTextOffset ?: Int.MAX_VALUE) > offset + 1
        }?.locator?.let { jumpTo(it as ReadingLocator.HtmlLocator) }
    }

    fun previousSection() {
        val parsed = openedBook?.parsedBook as? HtmlParsedBook ?: return
        val offset = currentLocator().normalizedTextOffset ?: 0
        parsed.content.navigation.lastOrNull {
            ((it.locator as? ReadingLocator.HtmlLocator)?.normalizedTextOffset ?: -1) < offset - 1
        }?.locator?.let { jumpTo(it as ReadingLocator.HtmlLocator) }
    }

    private fun createEditorKit(): HTMLEditorKit {
        val state = service<ReaderSettingsService>().state
        val palette = ReaderTheme.fromStored(state.theme).palette()
        editorPane.background = palette.background
        editorPane.foreground = palette.foreground
        scrollPane.viewport.background = palette.background
        return HTMLEditorKit().apply {
            styleSheet.addRule(
                "body { font-family: '${state.fontFamily.replace("'", "")}'; font-size: ${state.fontSize.coerceIn(12, 36)}pt; " +
                    "line-height: ${state.lineSpacing.coerceIn(1.0f, 2.5f)}; color: ${palette.foregroundCss()}; " +
                    "background-color: ${palette.backgroundCss()}; margin-left: ${state.horizontalMargin.coerceIn(8, 80)}px; " +
                    "margin-right: ${state.horizontalMargin.coerceIn(8, 80)}px; }",
            )
            styleSheet.addRule("p { margin-top: 0; margin-bottom: ${state.paragraphSpacing.coerceIn(0, 32)}px; }")
            styleSheet.addRule("img { max-width: 100%; }")
            styleSheet.addRule("pre { padding: 8px; font-family: monospace; }")
            styleSheet.addRule("code { font-family: monospace; }")
        }
    }

    private fun restoreLocation(locator: ReadingLocator.HtmlLocator?) {
        val document = editorPane.document as? HTMLDocument ?: return
        val idOffset = locator?.elementId?.let(document::getElement)?.startOffset
        val offset = idOffset ?: locator?.normalizedTextOffset?.coerceIn(0, document.length)
        if (offset != null) {
            editorPane.caretPosition = offset
            editorPane.modelToView2D(offset)?.let {
                scrollPane.viewport.viewPosition = Point(0, (it.y.toInt() - 16).coerceAtLeast(0))
            }
        } else {
            val bar = scrollPane.verticalScrollBar
            val range = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
            bar.value = ((locator?.scrollRatio ?: 0.0).coerceIn(0.0, 1.0) * range).toInt()
        }
    }

    private fun publishLocation() {
        val ratio = scrollRatio()
        val section = currentSectionTitle()
        sectionLabel.text = section ?: "Document"
        positionLabel.text = "%.1f%%".format(ratio * 100.0)
        onLocationChanged(currentLocator(), section, ratio * 100.0)
    }

    private fun visibleDocumentOffset(): Int = editorPane.viewToModel2D(Point(0, scrollPane.viewport.viewPosition.y))
        .coerceIn(0, editorPane.document.length)

    private fun nearestElementId(offset: Int): String? {
        val document = editorPane.document as? HTMLDocument ?: return null
        var element = document.getCharacterElement(offset.coerceIn(0, (document.length - 1).coerceAtLeast(0)))
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

    private fun createHeader() = EdgeReaderUi.header(
        titleLabel,
        EdgeReaderUi.toolbar(
            "EdgeReader.Html.Header",
            this,
            EdgeReaderUi.action("Back to Library", EdgeReaderIcons.Library, perform = onBack),
            EdgeReaderUi.action("Open Book", EdgeReaderIcons.Open, perform = onOpen),
            EdgeReaderUi.action("Table of Contents", EdgeReaderIcons.Contents, perform = onChooseChapter),
            EdgeReaderUi.action("Search", EdgeReaderIcons.Search, perform = onSearch),
            EdgeReaderUi.action("Bookmarks", EdgeReaderIcons.Bookmark, perform = onShowBookmarks),
            EdgeReaderUi.action("Add Bookmark", EdgeReaderIcons.AddBookmark, perform = onAddBookmark),
            EdgeReaderUi.action("Reading Settings", EdgeReaderIcons.Settings, perform = onSettings),
        ),
    )

    private fun createFooter() = EdgeReaderUi.footer(
        JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(sectionLabel)
            add(positionLabel)
        },
        EdgeReaderUi.toolbar(
            "EdgeReader.Html.Navigation",
            this,
            EdgeReaderUi.action("Previous Section", EdgeReaderIcons.Previous, perform = ::previousSection),
            EdgeReaderUi.action("Next Section", EdgeReaderIcons.Next, perform = ::nextSection),
        ),
    )
}
