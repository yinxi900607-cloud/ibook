package com.yinxi.edgereader.toolwindow

import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class EdgeReaderToolWindowController {
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
    fun nextPage() = panel?.nextPage()
    fun previousPage() = panel?.previousPage()
    fun backToLibrary() = panel?.backToLibrary()
    fun addBookmark() = panel?.addBookmark()
    fun showSearch() = panel?.showSearch()
}
