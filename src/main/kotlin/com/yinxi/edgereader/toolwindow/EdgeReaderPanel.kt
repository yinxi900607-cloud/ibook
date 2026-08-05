package com.yinxi.edgereader.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPanel
import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.parser.txt.EncodingSelectionRequiredException
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import com.yinxi.edgereader.persistence.settings.ReaderSettingsService
import com.yinxi.edgereader.service.BookLibraryService
import com.yinxi.edgereader.service.OpenedBook
import com.yinxi.edgereader.service.ReadingProgressService
import com.yinxi.edgereader.ui.EdgeReaderNotifications
import com.yinxi.edgereader.ui.library.LibraryPanel
import com.yinxi.edgereader.ui.reader.ChapterChooserDialog
import com.yinxi.edgereader.ui.reader.TxtReaderPanel
import com.yinxi.edgereader.ui.settings.EncodingChooserDialog
import com.yinxi.edgereader.ui.settings.ReaderSettingsDialog
import java.awt.CardLayout
import java.awt.event.HierarchyEvent
import java.nio.file.Path

class EdgeReaderPanel(
    private val project: Project,
    private val controller: EdgeReaderToolWindowController,
) : JBPanel<EdgeReaderPanel>(), Disposable {
    private val cards = CardLayout()
    private val libraryService = service<BookLibraryService>()
    private val progressService = service<ReadingProgressService>()
    private val settings = service<ReaderSettingsService>()
    private val libraryPanel = LibraryPanel(
        onOpenFile = ::chooseBook,
        onContinue = ::openBook,
        onRelocate = ::relocateBook,
        onRemove = ::removeBook,
        onRefresh = ::refreshLibrary,
    )
    private val readerPanel = TxtReaderPanel(
        onBack = ::backToLibrary,
        onOpen = ::chooseBook,
        onChooseChapter = ::chooseChapter,
        onSettings = ::showSettings,
        onRequestSlice = ::loadSlice,
        onLocationChanged = ::onLocationChanged,
    )
    private var currentBook: OpenedBook? = null
    private var disposed = false
    private var restoreAttempted = false

    init {
        layout = cards
        add(libraryPanel, LIBRARY_CARD)
        add(readerPanel, READER_CARD)
        controller.attach(this)
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !isShowing) saveAndFlush()
        }
        refreshLibrary()
    }

    fun chooseBook() {
        val descriptor = txtDescriptor("Open TXT Book")
        FileChooser.chooseFile(descriptor, project, null) { selectedFile -> openPath(selectedFile.toNioPath()) }
    }

    fun nextChapter() = readerPanel.nextChapter()

    fun previousChapter() = readerPanel.previousChapter()

    fun backToLibrary() {
        saveAndFlush()
        cards.show(this, LIBRARY_CARD)
        refreshLibrary()
    }

    private fun refreshLibrary() {
        libraryPanel.setLoading(true)
        libraryService.loadLibrary { result ->
            if (disposed) return@loadLibrary
            result.onSuccess { books ->
                libraryPanel.setBooks(books)
                libraryPanel.setLoading(false)
                restoreLastBookIfNeeded(books)
            }.onFailure { showError("Unable to load library", it) }
        }
    }

    private fun restoreLastBookIfNeeded(books: List<BookRecord>) {
        if (restoreAttempted || !settings.state.autoRestore || currentBook != null) return
        restoreAttempted = true
        settings.state.lastBookId?.let { id -> books.firstOrNull { it.id == id && !it.missing } }?.let(::openBook)
    }

    private fun openBook(record: BookRecord) {
        if (record.missing) {
            EdgeReaderNotifications.error(project, "Original file is missing", "Relocate the book before continuing.")
            return
        }
        saveAndFlush()
        libraryPanel.setLoading(true)
        libraryService.openBook(record) { handleOpenResult(it, Path.of(record.currentPath), record.encoding) }
    }

    private fun openPath(path: Path, encoding: String? = null) {
        saveAndFlush()
        libraryPanel.setLoading(true)
        libraryService.openBook(path, encoding) { handleOpenResult(it, path, encoding) }
    }

    private fun handleOpenResult(result: Result<OpenedBook>, path: Path, attemptedEncoding: String?) {
        if (disposed) return
        result.onSuccess(::showReader).onFailure { exception ->
            libraryPanel.setLoading(false)
            if (exception is EncodingSelectionRequiredException && attemptedEncoding == null) {
                val dialog = EncodingChooserDialog(project, exception.candidates)
                if (dialog.showAndGet()) dialog.selectedCharset?.let { openPath(path, it.name()) }
            } else {
                showError("Unable to open this file", exception)
            }
        }
    }

    private fun showReader(book: OpenedBook) {
        currentBook?.parsedBook?.close()
        currentBook = book
        settings.state.lastBookId = book.record.id
        cards.show(this, READER_CARD)
        val initialOffset = runCatching {
            (book.progress?.locatorJson?.let(ReadingLocatorCodec::decode) as? ReadingLocator.TextLocator)?.characterOffset
        }.getOrNull() ?: 0L
        readerPanel.showBook(book, initialOffset)
    }

    private fun loadSlice(startOffset: Long, focusOffset: Long) {
        val book = currentBook ?: return
        libraryService.readSlice(book, startOffset, TxtReaderPanel.WINDOW_CHARACTERS.toInt()) { result ->
            if (disposed || currentBook?.record?.id != book.record.id) return@readSlice
            result.onSuccess { readerPanel.setSlice(it, focusOffset) }
                .onFailure {
                    readerPanel.setLoadFailed()
                    showError("Unable to read this section", it)
                }
        }
    }

    private fun onLocationChanged(
        locator: ReadingLocator.TextLocator,
        chapterTitle: String?,
        progressPercent: Double,
    ) {
        currentBook?.record?.id?.let { progressService.update(it, locator, chapterTitle, progressPercent) }
    }

    private fun chooseChapter() {
        val chapters = currentBook?.parsedBook?.index?.chapters.orEmpty()
        if (chapters.isEmpty()) {
            Messages.showInfoMessage(project, "No chapter headings were detected in this TXT file.", "Table of Contents")
            return
        }
        val dialog = ChapterChooserDialog(project, chapters)
        if (dialog.showAndGet()) {
            val locator = dialog.selectedChapter?.locator as? ReadingLocator.TextLocator
            locator?.let { readerPanel.jumpTo(it.characterOffset) }
        }
    }

    private fun showSettings() {
        if (ReaderSettingsDialog(project).showAndGet()) readerPanel.applySettings()
    }

    private fun relocateBook(record: BookRecord) {
        saveAndFlush()
        FileChooser.chooseFile(txtDescriptor("Relocate TXT Book"), project, null) { selectedFile ->
            libraryPanel.setLoading(true)
            libraryService.relocateBook(record, selectedFile.toNioPath()) { result ->
                result.onSuccess(::showReader).onFailure { showError("The selected file does not match this book", it) }
            }
        }
    }

    private fun removeBook(record: BookRecord) {
        val answer = Messages.showYesNoDialog(
            project,
            "Remove '${record.title}' from the library? The original TXT file will not be deleted.",
            "Remove from Library",
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return
        if (currentBook?.record?.id == record.id) {
            saveAndFlush()
            currentBook?.parsedBook?.close()
            currentBook = null
        }
        libraryService.removeBook(record.id) { result ->
            result.onSuccess { refreshLibrary() }.onFailure { showError("Unable to remove the book", it) }
        }
    }

    private fun saveAndFlush() {
        val book = currentBook ?: return
        if (!readerPanel.hasLoadedSlice()) {
            progressService.flushAsync(book.record.id)
            return
        }
        val locator = readerPanel.currentLocator()
        val total = book.parsedBook.index.totalCharacters
        val percent = if (total <= 0) 0.0 else locator.characterOffset.toDouble() / total * 100.0
        val chapter = book.parsedBook.index.chapters.lastOrNull {
            (it.locator as ReadingLocator.TextLocator).characterOffset <= locator.characterOffset
        }?.title
        progressService.update(book.record.id, locator, chapter, percent)
        progressService.flushAsync(book.record.id)
    }

    private fun txtDescriptor(title: String) = FileChooserDescriptor(true, false, false, false, false, false)
        .withTitle(title)
        .withDescription("Choose a .txt or .text file. Reading and indexing run in the background.")
        .withFileFilter { file ->
            file.isDirectory || file.extension.equals("txt", true) || file.extension.equals("text", true)
        }

    private fun showError(title: String, exception: Throwable) {
        EdgeReaderNotifications.error(project, title, readableMessage(exception))
    }

    private fun readableMessage(exception: Throwable): String = when {
        exception.message?.contains("missing", ignoreCase = true) == true -> "The original file has moved. Relocate it from the library."
        exception is java.nio.charset.CharacterCodingException -> "The selected encoding cannot decode this file. Choose a different encoding."
        else -> exception.message?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred while processing the book."
    }

    override fun dispose() {
        disposed = true
        saveAndFlush()
        currentBook?.parsedBook?.close()
        currentBook = null
        controller.detach(this)
    }

    companion object {
        private const val LIBRARY_CARD = "library"
        private const val READER_CARD = "reader"
    }
}
