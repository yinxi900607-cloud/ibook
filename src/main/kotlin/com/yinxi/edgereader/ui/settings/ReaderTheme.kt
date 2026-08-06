package com.yinxi.edgereader.ui.settings

import java.awt.Color
import javax.swing.UIManager

enum class ReaderTheme(val displayName: String) {
    FOLLOW_IDE("Follow IDE"),
    LIGHT("Light"),
    DARK("Dark"),
    SEPIA("Sepia"),
    SOFT_GREEN("Soft Green"),
    ;

    fun palette(): ReaderPalette = when (this) {
        FOLLOW_IDE -> ReaderPalette(
            UIManager.getColor("TextArea.background") ?: Color(0x2B, 0x2D, 0x30),
            UIManager.getColor("TextArea.foreground") ?: Color(0xDF, 0xE1, 0xE5),
        )
        LIGHT -> ReaderPalette(Color(0xFA, 0xFA, 0xFA), Color(0x2B, 0x2D, 0x30))
        DARK -> ReaderPalette(Color(0x2B, 0x2D, 0x30), Color(0xDF, 0xE1, 0xE5))
        SEPIA -> ReaderPalette(Color(0xF4, 0xEC, 0xD8), Color(0x4A, 0x40, 0x33))
        SOFT_GREEN -> ReaderPalette(Color(0xE5, 0xEC, 0xE4), Color(0x37, 0x42, 0x38))
    }

    companion object {
        fun fromStored(value: String): ReaderTheme = entries.firstOrNull { it.name == value } ?: FOLLOW_IDE
    }
}

data class ReaderPalette(
    val background: Color,
    val foreground: Color,
) {
    fun backgroundCss(): String = background.css()
    fun foregroundCss(): String = foreground.css()

    private fun Color.css(): String = "#%02x%02x%02x".format(red, green, blue)
}
