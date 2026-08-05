package com.yinxi.edgereader.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class PreviousChapterAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { EdgeReaderActionSupport.withVisibleReader(it) { previousChapter() } }
    }
}
