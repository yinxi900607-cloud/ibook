package com.yinxi.edgereader.toolwindow

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.EdgeReaderBundle
import com.yinxi.edgereader.util.SupportedBookFiles
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.SwingConstants

class EdgeReaderPanel(
    private val project: Project,
) : JBPanel<EdgeReaderPanel>(BorderLayout()) {
    private val statusLabel = JBLabel(EdgeReaderBundle.message("library.empty.description"), SwingConstants.CENTER)

    init {
        border = JBUI.Borders.empty(24)
        add(createEmptyLibraryPanel(), BorderLayout.CENTER)
    }

    private fun createEmptyLibraryPanel(): JBPanel<*> = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
        isOpaque = false
        val constraints = GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.CENTER
            insets = Insets(6, 0, 6, 0)
        }

        add(JBLabel(EdgeReaderBundle.message("library.empty.title"), SwingConstants.CENTER).apply {
            font = JBFont.h2()
        }, constraints)

        constraints.gridy = 1
        add(statusLabel.apply {
            foreground = JBColor.GRAY
        }, constraints)

        constraints.gridy = 2
        constraints.fill = GridBagConstraints.NONE
        add(JButton(EdgeReaderBundle.message("library.open.button")).apply {
            addActionListener { chooseBook() }
        }, constraints)
    }

    private fun chooseBook() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle(EdgeReaderBundle.message("file.chooser.title"))
            .withDescription(EdgeReaderBundle.message("file.chooser.description"))
            .withFileFilter { file -> file.isDirectory || SupportedBookFiles.isSupportedName(file.name) }

        FileChooser.chooseFile(descriptor, project, null) { selectedFile ->
            statusLabel.text = EdgeReaderBundle.message("library.selected", selectedFile.name)
            statusLabel.toolTipText = selectedFile.presentableUrl
        }
    }
}
