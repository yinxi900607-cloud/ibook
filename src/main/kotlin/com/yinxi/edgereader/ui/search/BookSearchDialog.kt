package com.yinxi.edgereader.ui.search

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

class BookSearchDialog(
    project: Project,
    private val searchable: Boolean,
    private val onSearch: (String, (Result<List<SearchResult>>) -> Unit) -> Job,
    private val onJump: (SearchResult) -> Unit,
) : DialogWrapper(project) {
    private val queryField = JBTextField()
    private val statusLabel = JBLabel(if (searchable) "Type to search this book." else "This document has no searchable text.")
    private val model = DefaultListModel<SearchResult>()
    private val results = JBList(model).apply {
        cellRenderer = ResultRenderer()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) jumpToSelection()
            }
        })
    }
    private var job: Job? = null
    private var generation = 0L
    private val timer = Timer(300) { startSearch() }.apply { isRepeats = false }

    init {
        title = "Search Book"
        setOKButtonText("Go to Result")
        init()
        isOKActionEnabled = false
        queryField.isEnabled = searchable
        queryField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = changed()
            override fun removeUpdate(event: DocumentEvent) = changed()
            override fun changedUpdate(event: DocumentEvent) = changed()
            private fun changed() {
                generation++
                job?.cancel()
                timer.restart()
            }
        })
        results.addListSelectionListener { isOKActionEnabled = results.selectedValue != null }
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(8)
        preferredSize = Dimension(580, 380)
        add(queryField, BorderLayout.NORTH)
        add(JScrollPane(results), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    override fun getPreferredFocusedComponent(): JComponent = queryField

    override fun doOKAction() = jumpToSelection()

    override fun dispose() {
        timer.stop()
        job?.cancel()
        super.dispose()
    }

    private fun startSearch() {
        val query = queryField.text.trim()
        model.clear()
        if (query.isEmpty()) {
            statusLabel.text = "Type to search this book."
            return
        }
        val requestGeneration = generation
        statusLabel.text = "Searching…"
        job = onSearch(query) { result ->
            if (isDisposed || requestGeneration != generation) return@onSearch
            result.onSuccess { matches ->
                model.clear()
                matches.forEach(model::addElement)
                statusLabel.text = if (matches.isEmpty()) "No matches found." else "${matches.size} result(s)"
            }.onFailure { statusLabel.text = it.message ?: "Search failed." }
        }
    }

    private fun jumpToSelection() {
        val result = results.selectedValue ?: return
        onJump(result)
        close(OK_EXIT_CODE)
    }

    private class ResultRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?, value: Any?, index: Int, selected: Boolean, focused: Boolean,
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, selected, focused)
            val result = value as? SearchResult
            text = result?.let { "${it.title ?: "Result"} — ${it.excerpt}" }.orEmpty()
            border = JBUI.Borders.empty(5, 6)
            return this
        }
    }
}
