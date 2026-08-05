package com.yinxi.edgereader.ui.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.yinxi.edgereader.persistence.settings.ReaderSettingsService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GraphicsEnvironment
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class ReaderSettingsDialog(
    project: Project,
) : DialogWrapper(project) {
    private val settings = service<ReaderSettingsService>().state
    private val fonts = arrayOf("Follow IDE") + GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
    private val fontCombo = ComboBox(fonts).apply {
        selectedItem = settings.fontFamily.ifBlank { "Follow IDE" }
    }
    private val sizeSpinner = JSpinner(SpinnerNumberModel(settings.fontSize.coerceIn(12, 36), 12, 36, 1))
    private val marginSpinner = JSpinner(SpinnerNumberModel(settings.horizontalMargin.coerceIn(8, 80), 8, 80, 2))

    init {
        title = "Reading Settings"
        init()
    }

    override fun doOKAction() {
        settings.fontFamily = fontCombo.selectedItem?.toString().orEmpty().takeUnless { it == "Follow IDE" }.orEmpty()
        settings.fontSize = sizeSpinner.value as Int
        settings.horizontalMargin = marginSpinner.value as Int
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
        border = JBUI.Borders.empty(8)
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            anchor = GridBagConstraints.WEST
            insets = Insets(5, 5, 5, 12)
        }
        val fieldConstraints = GridBagConstraints().apply {
            gridx = 1
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
        }
        add(JBLabel("Font"), labelConstraints.apply { gridy = 0 })
        add(fontCombo, fieldConstraints.apply { gridy = 0 })
        add(JBLabel("Font size"), labelConstraints.apply { gridy = 1 })
        add(sizeSpinner, fieldConstraints.apply { gridy = 1 })
        add(JBLabel("Horizontal margin"), labelConstraints.apply { gridy = 2 })
        add(marginSpinner, fieldConstraints.apply { gridy = 2 })
    }
}
