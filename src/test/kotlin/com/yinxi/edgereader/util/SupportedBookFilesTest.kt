package com.yinxi.edgereader.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SupportedBookFilesTest {
    @Test
    fun `accepts every format planned for the first release`() {
        listOf(
            "book.txt",
            "book.text",
            "book.epub",
            "book.pdf",
            "book.md",
            "book.markdown",
            "book.html",
            "book.htm",
            "book.xhtml",
        ).forEach { fileName -> assertTrue(SupportedBookFiles.isSupportedName(fileName), fileName) }
    }

    @Test
    fun `extension matching is case insensitive`() {
        assertTrue(SupportedBookFiles.isSupported(Path.of("Novel.EPUB")))
        assertTrue(SupportedBookFiles.isSupportedName("NOTES.MarkDown"))
    }

    @Test
    fun `rejects directories disguised by dots and unrelated files`() {
        assertFalse(SupportedBookFiles.isSupportedName("archive.zip"))
        assertFalse(SupportedBookFiles.isSupportedName("pdf"))
        assertFalse(SupportedBookFiles.isSupportedName("book.pdf.exe"))
    }
}
