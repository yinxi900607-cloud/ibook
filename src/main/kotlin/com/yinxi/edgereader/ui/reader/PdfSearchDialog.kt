package com.yinxi.edgereader.ui.reader

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.model.SearchResult
import kotlinx.coroutines.Job
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class PdfSearchDialog(
    project: Project,
    private val searchable: Boolean,
    private val onSearch: (String, (Result<List<SearchResult>>) -> Unit) -> Job,
    private val onJump: (SearchResult) -> Unit,
) : DialogWrapper(project) {
    private val queryField = JBTextField()
    private val statusLabel = JBLabel(if (searchable) "Enter text to search all pages." else "This PDF has no searchable text.")
    private val listModel = DefaultListModel<SearchResult>()
    private val resultList = JBList(listModel).apply {
        cellRenderer = SearchResultRenderer()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) jumpToSelection()
            }
        })
    }
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private val searchTimer = Timer(300) { startSearch() }.apply { isRepeats = false }

    init {
        title = "Search PDF"
        setOKButtonText("Go to Page")
        init()
        isOKActionEnabled = false
        queryField.isEnabled = searchable
        queryField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = changed()
            override fun removeUpdate(event: DocumentEvent) = changed()
            override fun changedUpdate(event: DocumentEvent) = changed()
            private fun changed() {
                searchGeneration++
                searchJob?.cancel()
                searchTimer.restart()
            }
        })
        resultList.addListSelectionListener { isOKActionEnabled = resultList.selectedValue != null }
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(8)
        preferredSize = Dimension(560, 360)
        add(queryField, BorderLayout.NORTH)
        add(JScrollPane(resultList), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    override fun getPreferredFocusedComponent(): JComponent = queryField

    override fun doOKAction() = jumpToSelection()

    override fun dispose() {
        searchTimer.stop()
        searchJob?.cancel()
        super.dispose()
    }

    private fun startSearch() {
        val query = queryField.text.trim()
        listModel.clear()
        if (query.isEmpty()) {
            statusLabel.text = "Enter text to search all pages."
            return
        }
        statusLabel.text = "Searching…"
        val requestGeneration = searchGeneration
        searchJob = onSearch(query) { result ->
            if (isDisposed || requestGeneration != searchGeneration) return@onSearch
            result.onSuccess { matches ->
                listModel.clear()
                matches.forEach(listModel::addElement)
                statusLabel.text = if (matches.isEmpty()) "No matches found." else "${matches.size} result(s)"
            }.onFailure { statusLabel.text = it.message ?: "Search failed." }
        }
    }

    private fun jumpToSelection() {
        val result = resultList.selectedValue ?: return
        onJump(result)
        close(OK_EXIT_CODE)
    }

    private class SearchResultRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val result = value as? SearchResult
            text = result?.let { "${it.title ?: "Result"} — ${it.excerpt}" }.orEmpty()
            border = JBUI.Borders.empty(5, 6)
            return this
        }
    }
}
