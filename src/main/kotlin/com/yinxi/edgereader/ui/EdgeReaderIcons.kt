package com.yinxi.edgereader.ui

import com.intellij.openapi.util.IconLoader

object EdgeReaderIcons {
    @JvmField val Library = icon("library")
    @JvmField val Open = icon("open")
    @JvmField val Contents = icon("contents")
    @JvmField val Settings = icon("settings")
    @JvmField val Previous = icon("previous")
    @JvmField val Next = icon("next")
    @JvmField val Search = icon("search")
    @JvmField val Refresh = icon("refresh")
    @JvmField val Remove = icon("remove")
    @JvmField val Relocate = icon("relocate")
    @JvmField val ZoomIn = icon("zoomIn")
    @JvmField val ZoomOut = icon("zoomOut")
    @JvmField val FitWidth = icon("fitWidth")

    private fun icon(name: String) = IconLoader.getIcon("/icons/actions/$name.svg", EdgeReaderIcons::class.java)
}
