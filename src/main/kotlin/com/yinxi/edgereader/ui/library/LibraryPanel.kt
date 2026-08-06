package com.yinxi.edgereader.ui.library

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.BookRecord
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListModel
import javax.swing.JButton
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
    private val emptyMessage = JBLabel("Open a local TXT or EPUB book to start reading.")

    init {
        border = JBUI.Borders.empty(8)
        add(createToolbar(), BorderLayout.NORTH)
        tabs.addTab("Recent", JBScrollPane(recentList))
        tabs.addTab("All Books", JBScrollPane(allList))
        tabs.addTab("Missing", JBScrollPane(missingList))
        add(tabs, BorderLayout.CENTER)
        add(emptyMessage, BorderLayout.SOUTH)
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
        emptyMessage.isVisible = books.isEmpty()
    }

    fun setLoading(loading: Boolean) {
        emptyMessage.text = if (loading) "Loading library…" else "Open a local TXT or EPUB book to start reading."
    }

    private fun createToolbar(): JBPanel<*> = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
        add(JButton("Open Book").apply { addActionListener { onOpenFile() } })
        add(JButton("Continue").apply { addActionListener { selectedBook()?.let(onContinue) } })
        add(JButton("Relocate").apply { addActionListener { selectedBook()?.let(onRelocate) } })
        add(JButton("Remove").apply { addActionListener { selectedBook()?.let(onRemove) } })
        add(JButton("Refresh").apply { addActionListener { onRefresh() } })
    }

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

    private class BookRenderer : JBPanel<BookRenderer>(BorderLayout(8, 2)), ListCellRenderer<BookRecord> {
        private val title = JBLabel()
        private val details = JBLabel()

        init {
            border = JBUI.Borders.empty(8, 10)
            title.font = title.font.deriveFont(Font.BOLD)
            details.foreground = JBColor.GRAY
            add(title, BorderLayout.NORTH)
            add(details, BorderLayout.SOUTH)
        }

        override fun getListCellRendererComponent(
            list: JList<out BookRecord>,
            value: BookRecord,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            title.text = value.title
            val lastRead = value.lastReadAt?.let { DATE_FORMAT.format(Instant.ofEpochMilli(it)) } ?: "Never read"
            val missing = if (value.missing) " · File missing" else ""
            details.text = "${value.format} · ${"%.1f".format(value.progressPercent)}% · $lastRead$missing"
            background = if (isSelected) list.selectionBackground else list.background
            title.foreground = if (isSelected) list.selectionForeground else list.foreground
            isOpaque = true
            return this
        }

        companion object {
            private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        }
    }
}
