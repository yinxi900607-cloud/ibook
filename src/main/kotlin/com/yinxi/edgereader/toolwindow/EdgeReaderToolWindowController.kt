package com.yinxi.edgereader.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class EdgeReaderToolWindowController(
    @Suppress("unused") private val project: Project,
) {
    private var panel: EdgeReaderPanel? = null

    fun attach(panel: EdgeReaderPanel) {
        this.panel = panel
    }

    fun detach(panel: EdgeReaderPanel) {
        if (this.panel === panel) this.panel = null
    }

    fun openBook() = panel?.chooseBook()
    fun nextChapter() = panel?.nextChapter()
    fun previousChapter() = panel?.previousChapter()
    fun backToLibrary() = panel?.backToLibrary()
}
