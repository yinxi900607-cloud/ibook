package com.yinxi.edgereader.ui.reader

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.yinxi.edgereader.model.BookNavigationItem
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListSelectionModel

class ChapterChooserDialog(
    project: Project,
    chapters: List<BookNavigationItem>,
) : DialogWrapper(project) {
    private val list = JBList(chapters).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component = super.getListCellRendererComponent(
                list,
                (value as? BookNavigationItem)?.title ?: value,
                index,
                isSelected,
                cellHasFocus,
            )
        }
        if (model.size > 0) selectedIndex = 0
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && selectedValue != null) close(OK_EXIT_CODE)
            }
        })
    }

    init {
        title = "Table of Contents"
        init()
    }

    val selectedChapter: BookNavigationItem?
        get() = list.selectedValue

    override fun createCenterPanel(): JComponent = JBScrollPane(list).apply {
        preferredSize = Dimension(420, 500)
    }
}
