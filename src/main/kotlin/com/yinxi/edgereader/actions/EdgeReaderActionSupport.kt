package com.yinxi.edgereader.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.yinxi.edgereader.toolwindow.EdgeReaderToolWindowController
import com.yinxi.edgereader.toolwindow.EdgeReaderToolWindowFactory

internal object EdgeReaderActionSupport {
    fun withVisibleReader(project: Project, action: EdgeReaderToolWindowController.() -> Unit) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(EdgeReaderToolWindowFactory.TOOL_WINDOW_ID)
            ?: return
        toolWindow.activate({ project.service<EdgeReaderToolWindowController>().action() }, true)
    }
}
