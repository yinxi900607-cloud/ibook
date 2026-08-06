package com.yinxi.edgereader.ui.bookmark

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.Bookmark
import com.yinxi.edgereader.ui.EdgeReaderIcons
import com.yinxi.edgereader.ui.EdgeReaderUi
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListModel
import javax.swing.JComponent

class BookmarkDialog(
    project: Project,
    bookmarks: List<Bookmark>,
    private val onJump: (Bookmark) -> Unit,
    private val onDelete: (Bookmark, (Result<Unit>) -> Unit) -> Unit,
) : DialogWrapper(project) {
    private val model = DefaultListModel<Bookmark>().apply { bookmarks.forEach(::addElement) }
    private val list = JBList(model).apply {
        emptyText.text = "No bookmarks in this book"
        cellRenderer = Renderer()
        if (model.size > 0) selectedIndex = 0
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) jumpToSelection()
            }
        })
    }

    init {
        title = "Bookmarks"
        setOKButtonText("Go to Bookmark")
        init()
        isOKActionEnabled = list.selectedValue != null
        list.addListSelectionListener { isOKActionEnabled = list.selectedValue != null }
    }

    override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        preferredSize = Dimension(520, 380)
        add(
            EdgeReaderUi.toolbar(
                "EdgeReader.Bookmarks",
                list,
                EdgeReaderUi.action("Delete Bookmark", EdgeReaderIcons.Remove, enabled = { list.selectedValue != null }) {
                    deleteSelection()
                },
            ),
            BorderLayout.NORTH,
        )
        add(JBScrollPane(list), BorderLayout.CENTER)
    }

    override fun doOKAction() = jumpToSelection()

    private fun jumpToSelection() {
        val bookmark = list.selectedValue ?: return
        onJump(bookmark)
        close(OK_EXIT_CODE)
    }

    private fun deleteSelection() {
        val bookmark = list.selectedValue ?: return
        onDelete(bookmark) { result ->
            result.onSuccess { model.removeElement(bookmark) }
        }
    }

    private class Renderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?, value: Any?, index: Int, selected: Boolean, focused: Boolean,
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, selected, focused)
            val bookmark = value as? Bookmark
            text = bookmark?.let {
                val title = it.title?.takeIf(String::isNotBlank) ?: "Saved position"
                val excerpt = it.excerpt?.takeIf(String::isNotBlank)?.let { text -> " — $text" }.orEmpty()
                "$title$excerpt   ${DATE_FORMAT.format(Instant.ofEpochMilli(it.createdAt))}"
            }.orEmpty()
            border = JBUI.Borders.empty(6, 8)
            return this
        }

        companion object {
            private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        }
    }
}
