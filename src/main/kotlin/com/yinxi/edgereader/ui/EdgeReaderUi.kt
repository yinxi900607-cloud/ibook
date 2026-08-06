package com.yinxi.edgereader.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants

object EdgeReaderUi {
    fun action(
        text: String,
        icon: Icon,
        enabled: () -> Boolean = { true },
        perform: () -> Unit,
    ): AnAction = object : DumbAwareAction(text, text, icon) {
        override fun actionPerformed(event: AnActionEvent) = perform()

        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = enabled()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    fun toolbar(place: String, target: JComponent, vararg actions: AnAction): JComponent =
        ActionManager.getInstance().createActionToolbar(place, DefaultActionGroup(*actions), true).apply {
            targetComponent = target
        }.component

    fun header(title: JBLabel, toolbar: JComponent): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(2, 4),
        )
        title.font = title.font.deriveFont(Font.BOLD)
        title.horizontalAlignment = SwingConstants.RIGHT
        add(toolbar, BorderLayout.WEST)
        add(title, BorderLayout.CENTER)
    }

    fun footer(left: JComponent, right: JComponent): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(1, 6),
        )
        add(left, BorderLayout.CENTER)
        add(right, BorderLayout.EAST)
    }

    fun secondary(label: JBLabel) {
        label.foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
        label.font = label.font.deriveFont(label.font.size2D - 1f)
    }
}
