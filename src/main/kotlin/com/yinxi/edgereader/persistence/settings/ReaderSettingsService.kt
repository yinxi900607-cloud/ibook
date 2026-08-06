package com.yinxi.edgereader.persistence.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "EdgeReaderSettings", storages = [Storage("edgeReader.xml")])
class ReaderSettingsService : PersistentStateComponent<ReaderSettingsService.SettingsState> {
    private var state = SettingsState()

    override fun getState(): SettingsState = state

    override fun loadState(state: SettingsState) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    class SettingsState {
        var fontFamily: String = ""
        var fontSize: Int = 18
        var lineSpacing: Float = 1.35f
        var paragraphSpacing: Int = 8
        var horizontalMargin: Int = 24
        var theme: String = "FOLLOW_IDE"
        var autoRestore: Boolean = true
        var lastBookId: String? = null
    }
}
