package com.yinxi.edgereader.ui.library

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.ui.EdgeReaderIcons
import com.yinxi.edgereader.ui.EdgeReaderUi
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

class LibraryPanel(
    private val onOpenFile: () -> Unit,
    private val onContinue: (BookRecord) -> Unit,
    private val onRelocate: (BookRecord) -> Unit,
    private val onRemove: (BookRecord) -> Unit,
    private val onRefresh: () -> Unit,
) : JBPanel<LibraryPanel>(BorderLayout()) {
    private val recentModel = DefaultListModel<BookRecord>()
    private val allModel = DefaultListModel<BookRecord>()
    private val missingModel = DefaultListModel<BookRecord>()
    private val recentList = createList(recentModel, "No recently read books")
    private val allList = createList(allModel, "Your library is empty")
    private val missingList = createList(missingModel, "No missing files")
    private val tabs = JBTabbedPane()
    private val titleLabel = JBLabel("Library")

    init {
        border = JBUI.Borders.empty()
        add(createToolbar(), BorderLayout.NORTH)
        tabs.addTab("Recent", JBScrollPane(recentList))
        tabs.addTab("All Books", JBScrollPane(allList))
        tabs.addTab("Missing", JBScrollPane(missingList))
        add(tabs, BorderLayout.CENTER)
    }

    fun setBooks(books: List<BookRecord>) {
        replace(allModel, books)
        replace(
            recentModel,
            books.filter { it.lastReadAt != null || it.lastOpenedAt != null }
                .sortedByDescending { it.lastReadAt ?: it.lastOpenedAt ?: 0 }
                .take(30),
        )
        replace(missingModel, books.filter { it.missing })
    }

    fun setLoading(loading: Boolean) {
        if (loading) {
            recentList.emptyText.text = "Loading library…"
            allList.emptyText.text = "Loading library…"
            missingList.emptyText.text = "Loading library…"
        } else {
            recentList.emptyText.text = "No recently read books"
            allList.emptyText.text = "Your library is empty — open a local book to begin"
            missingList.emptyText.text = "No missing files"
        }
    }

    private fun createToolbar() = EdgeReaderUi.header(
        titleLabel,
        EdgeReaderUi.toolbar(
            "EdgeReader.Library.Header",
            this,
            EdgeReaderUi.action("Open Book", EdgeReaderIcons.Open, perform = onOpenFile),
            EdgeReaderUi.action("Continue Reading", EdgeReaderIcons.Next, enabled = { selectedBook() != null }) {
                selectedBook()?.let(onContinue)
            },
            EdgeReaderUi.action("Relocate File", EdgeReaderIcons.Relocate, enabled = { selectedBook() != null }) {
                selectedBook()?.let(onRelocate)
            },
            EdgeReaderUi.action("Remove from Library", EdgeReaderIcons.Remove, enabled = { selectedBook() != null }) {
                selectedBook()?.let(onRemove)
            },
            EdgeReaderUi.action("Refresh Library", EdgeReaderIcons.Refresh, perform = onRefresh),
        ),
    )

    private fun createList(model: DefaultListModel<BookRecord>, emptyText: String): JBList<BookRecord> =
        JBList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = BookRenderer()
            this.emptyText.text = emptyText
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                        selectedValue?.let(onContinue)
                    }
                }
            })
        }

    private fun selectedBook(): BookRecord? = when (tabs.selectedIndex) {
        0 -> recentList.selectedValue
        1 -> allList.selectedValue
        2 -> missingList.selectedValue
        else -> null
    }

    private fun replace(model: DefaultListModel<BookRecord>, values: List<BookRecord>) {
        model.clear()
        values.forEach(model::addElement)
    }

    private class BookRenderer : JBPanel<BookRenderer>(BorderLayout(10, 2)), ListCellRenderer<BookRecord> {
        private val title = JBLabel()
        private val author = JBLabel()
        private val details = JBLabel()
        private val cover = JBLabel().apply {
            preferredSize = Dimension(44, 60)
            horizontalAlignment = JBLabel.CENTER
            verticalAlignment = JBLabel.CENTER
        }
        private val iconCache = mutableMapOf<String, ImageIcon?>()

        init {
            border = JBUI.Borders.empty(6, 8)
            title.font = title.font.deriveFont(Font.BOLD)
            EdgeReaderUi.secondary(author)
            EdgeReaderUi.secondary(details)
            add(cover, BorderLayout.WEST)
            add(JBPanel<JBPanel<*>>().apply {
                isOpaque = false
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                add(title)
                add(author)
                add(javax.swing.Box.createVerticalGlue())
                add(details)
            }, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out BookRecord>,
            value: BookRecord,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            title.text = value.title
            author.text = value.author?.takeIf { it.isNotBlank() } ?: value.fileName
            val lastRead = value.lastReadAt?.let { DATE_FORMAT.format(Instant.ofEpochMilli(it)) } ?: "Never read"
            val missing = if (value.missing) " · Missing" else ""
            details.text = "${value.format}   ${"%.1f".format(value.progressPercent)}%   $lastRead$missing"
            cover.icon = value.coverCachePath?.let { path ->
                iconCache.getOrPut(path) {
                    path.takeIf { java.nio.file.Files.isRegularFile(java.nio.file.Path.of(it)) }?.let {
                        val source = ImageIcon(it)
                        ImageIcon(source.image.getScaledInstance(JBUI.scale(44), JBUI.scale(60), java.awt.Image.SCALE_SMOOTH))
                    }
                }
            }
            cover.text = if (cover.icon == null) value.format.name else null
            background = if (isSelected) list.selectionBackground else list.background
            title.foreground = if (isSelected) list.selectionForeground else list.foreground
            cover.foreground = if (isSelected) list.selectionForeground else
                JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
            if (isSelected) {
                author.foreground = list.selectionForeground
                details.foreground = list.selectionForeground
            } else {
                author.foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                details.foreground = if (value.missing) JBColor.namedColor("Label.errorForeground", JBColor.RED) else
                    JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
            }
            isOpaque = true
            return this
        }

        companion object {
            private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        }
    }
}
