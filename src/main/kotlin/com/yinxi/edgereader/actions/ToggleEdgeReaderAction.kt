package com.yinxi.edgereader.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.yinxi.edgereader.toolwindow.EdgeReaderToolWindowFactory

class ToggleEdgeReaderAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(EdgeReaderToolWindowFactory.TOOL_WINDOW_ID)

        if (toolWindow == null) {
            logger<ToggleEdgeReaderAction>().warn("Edge Reader tool window is not registered")
            return
        }

        if (toolWindow.isVisible) {
            toolWindow.hide(null)
        } else {
            toolWindow.activate(null, true)
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
