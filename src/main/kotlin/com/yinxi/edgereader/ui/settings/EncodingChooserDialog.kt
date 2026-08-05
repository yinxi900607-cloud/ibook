package com.yinxi.edgereader.ui.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.charset.Charset
import javax.swing.JComponent

class EncodingChooserDialog(
    project: Project,
    candidates: List<Charset>,
) : DialogWrapper(project) {
    private val charsetByName = candidates.distinctBy { it.name() }.associateBy { it.displayName() }
    private val comboBox = ComboBox(charsetByName.keys.toTypedArray())

    init {
        title = "Choose Text Encoding"
        init()
    }

    val selectedCharset: Charset?
        get() = comboBox.selectedItem?.toString()?.let(charsetByName::get)

    override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(8)
        add(JBLabel("The file encoding could not be identified reliably. Choose an encoding:"), BorderLayout.NORTH)
        add(comboBox, BorderLayout.CENTER)
    }
}
