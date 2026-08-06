package com.yinxi.edgereader.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPanel
import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.parser.epub.EpubParsedBook
import com.yinxi.edgereader.parser.pdf.InvalidPdfException
import com.yinxi.edgereader.parser.pdf.PdfParsedBook
import com.yinxi.edgereader.parser.pdf.PdfZoomMode
import com.yinxi.edgereader.parser.txt.EncodingSelectionRequiredException
import com.yinxi.edgereader.parser.txt.TxtParsedBook
import com.yinxi.edgereader.persistence.repository.ReadingLocatorCodec
import com.yinxi.edgereader.persistence.settings.ReaderSettingsService
import com.yinxi.edgereader.service.BookLibraryService
import com.yinxi.edgereader.service.OpenedBook
import com.yinxi.edgereader.service.ReadingProgressService
import com.yinxi.edgereader.ui.EdgeReaderNotifications
import com.yinxi.edgereader.ui.library.LibraryPanel
import com.yinxi.edgereader.ui.reader.ChapterChooserDialog
import com.yinxi.edgereader.ui.reader.EpubReaderPanel
import com.yinxi.edgereader.ui.reader.PdfReaderPanel
import com.yinxi.edgereader.ui.reader.PdfSearchDialog
import com.yinxi.edgereader.ui.reader.TxtReaderPanel
import com.yinxi.edgereader.ui.settings.EncodingChooserDialog
import com.yinxi.edgereader.ui.settings.ReaderSettingsDialog
import java.awt.CardLayout
import java.awt.event.HierarchyEvent
import java.net.URI
import java.nio.file.Path
import kotlinx.coroutines.Job

class EdgeReaderPanel(
    private val project: Project,
    private val controller: EdgeReaderToolWindowController,
) : JBPanel<EdgeReaderPanel>(), Disposable {
    private val cards = CardLayout()
    private val readerCards = CardLayout()
    private val readerContainer = JBPanel<JBPanel<*>>(readerCards)
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
    private val txtReaderPanel = TxtReaderPanel(
        onBack = ::backToLibrary,
        onOpen = ::chooseBook,
        onChooseChapter = ::chooseChapter,
        onSettings = ::showSettings,
        onRequestSlice = ::loadTextSlice,
        onLocationChanged = ::onTextLocationChanged,
    )
    private val epubReaderPanel = EpubReaderPanel(
        onBack = ::backToLibrary,
        onOpen = ::chooseBook,
        onChooseChapter = ::chooseChapter,
        onSettings = ::showSettings,
        onRequestChapter = ::loadEpubChapter,
        onNavigateLink = ::navigateEpubLink,
        onLocationChanged = ::onEpubLocationChanged,
    )
    private val pdfReaderPanel = PdfReaderPanel(
        onBack = ::backToLibrary,
        onOpen = ::chooseBook,
        onChooseChapter = ::chooseChapter,
        onSearch = ::showPdfSearch,
        onRequestPage = ::loadPdfPage,
        onLocationChanged = ::onPdfLocationChanged,
    )
    private var currentBook: OpenedBook? = null
    private var pdfRenderJob: Job? = null
    private var disposed = false
    private var restoreAttempted = false

    init {
        layout = cards
        readerContainer.add(txtReaderPanel, TXT_READER_CARD)
        readerContainer.add(epubReaderPanel, EPUB_READER_CARD)
        readerContainer.add(pdfReaderPanel, PDF_READER_CARD)
        add(libraryPanel, LIBRARY_CARD)
        add(readerContainer, READER_CARD)
        controller.attach(this)
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !isShowing) saveAndFlush()
        }
        refreshLibrary()
    }

    fun chooseBook() {
        FileChooser.chooseFile(bookDescriptor("Open Electronic Book"), project, null) { selectedFile ->
            openPath(selectedFile.toNioPath())
        }
    }

    fun nextChapter() = when (currentBook?.record?.format) {
        BookFormat.TXT -> txtReaderPanel.nextChapter()
        BookFormat.EPUB -> epubReaderPanel.nextChapter()
        else -> Unit
    }

    fun previousChapter() = when (currentBook?.record?.format) {
        BookFormat.TXT -> txtReaderPanel.previousChapter()
        BookFormat.EPUB -> epubReaderPanel.previousChapter()
        else -> Unit
    }

    fun nextPage() {
        pdfReaderPanel.nextPage()
    }

    fun previousPage() {
        pdfReaderPanel.previousPage()
    }

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
        pdfRenderJob?.cancel()
        currentBook?.parsedBook?.close()
        currentBook = book
        settings.state.lastBookId = book.record.id
        cards.show(this, READER_CARD)
        val locator = runCatching { book.progress?.locatorJson?.let(ReadingLocatorCodec::decode) }.getOrNull()
        when (val parsed = book.parsedBook) {
            is TxtParsedBook -> {
                readerCards.show(readerContainer, TXT_READER_CARD)
                txtReaderPanel.showBook(book, (locator as? ReadingLocator.TextLocator)?.characterOffset ?: 0L)
            }
            is EpubParsedBook -> {
                readerCards.show(readerContainer, EPUB_READER_CARD)
                epubReaderPanel.showBook(book, locator as? ReadingLocator.EpubLocator)
            }
            is PdfParsedBook -> {
                readerCards.show(readerContainer, PDF_READER_CARD)
                pdfReaderPanel.showBook(book, locator as? ReadingLocator.PdfLocator)
            }
            else -> showError("Unable to open this file", IllegalArgumentException("Unsupported parsed book type"))
        }
    }

    private fun loadTextSlice(startOffset: Long, focusOffset: Long) {
        val book = currentBook ?: return
        libraryService.readTextSlice(book, startOffset, TxtReaderPanel.WINDOW_CHARACTERS.toInt()) { result ->
            if (disposed || currentBook?.record?.id != book.record.id) return@readTextSlice
            result.onSuccess { txtReaderPanel.setSlice(it, focusOffset) }
                .onFailure {
                    txtReaderPanel.setLoadFailed()
                    showError("Unable to read this section", it)
                }
        }
    }

    private fun loadEpubChapter(index: Int, locator: ReadingLocator.EpubLocator?) {
        val book = currentBook ?: return
        libraryService.readEpubChapter(book, index) { result ->
            if (disposed || currentBook?.record?.id != book.record.id) return@readEpubChapter
            result.onSuccess { epubReaderPanel.setChapter(it, locator) }
                .onFailure {
                    epubReaderPanel.setLoadFailed()
                    showError("Unable to read this EPUB chapter", it)
                }
        }
    }

    private fun onTextLocationChanged(locator: ReadingLocator.TextLocator, chapterTitle: String?, progressPercent: Double) {
        currentBook?.record?.id?.let { progressService.update(it, locator, chapterTitle, progressPercent) }
    }

    private fun onEpubLocationChanged(locator: ReadingLocator.EpubLocator, chapterTitle: String?, progressPercent: Double) {
        currentBook?.record?.id?.let { progressService.update(it, locator, chapterTitle, progressPercent) }
    }

    private fun onPdfLocationChanged(locator: ReadingLocator.PdfLocator, chapterTitle: String?, progressPercent: Double) {
        currentBook?.record?.id?.let { progressService.update(it, locator, chapterTitle, progressPercent) }
    }

    private fun loadPdfPage(
        pageIndex: Int,
        viewportWidth: Int,
        zoomMode: PdfZoomMode,
        customScale: Float,
        generation: Long,
    ) {
        val book = currentBook ?: return
        pdfRenderJob?.cancel()
        pdfRenderJob = libraryService.renderPdfPage(book, pageIndex, viewportWidth, zoomMode, customScale) { result ->
            if (disposed || currentBook?.record?.id != book.record.id) return@renderPdfPage
            result.onSuccess { pdfReaderPanel.setPage(it, generation) }
                .onFailure {
                    pdfReaderPanel.setLoadFailed(generation)
                    showError("Unable to render this PDF page", it)
                }
        }
    }

    private fun showPdfSearch() {
        val book = currentBook ?: return
        val pdf = book.parsedBook as? PdfParsedBook ?: return
        PdfSearchDialog(
            project = project,
            searchable = pdf.hasSearchableText,
            onSearch = { query, callback -> libraryService.searchPdf(book, query, callback = callback) },
            onJump = { result -> (result.locator as? ReadingLocator.PdfLocator)?.let(pdfReaderPanel::jumpTo) },
        ).show()
    }

    private fun chooseChapter() {
        val chapters = navigation()
        if (chapters.isEmpty()) {
            Messages.showInfoMessage(project, "No table of contents is available for this book.", "Table of Contents")
            return
        }
        val dialog = ChapterChooserDialog(project, chapters)
        if (dialog.showAndGet()) dialog.selectedChapter?.locator?.let(::jumpTo)
    }

    private fun navigation(): List<BookNavigationItem> = when (val parsed = currentBook?.parsedBook) {
        is TxtParsedBook -> parsed.index.chapters
        is EpubParsedBook -> parsed.navigation
        is PdfParsedBook -> parsed.navigation
        else -> emptyList()
    }

    private fun jumpTo(locator: ReadingLocator) {
        when (locator) {
            is ReadingLocator.TextLocator -> txtReaderPanel.jumpTo(locator.characterOffset)
            is ReadingLocator.EpubLocator -> epubReaderPanel.jumpTo(locator)
            is ReadingLocator.PdfLocator -> pdfReaderPanel.jumpTo(locator)
            else -> Unit
        }
    }

    private fun navigateEpubLink(link: String) {
        val epub = currentBook?.parsedBook as? EpubParsedBook ?: return
        val uri = runCatching { URI(link) }.getOrNull() ?: return
        if (uri.scheme != null && uri.scheme != "file") return
        if (uri.scheme == null && link.startsWith('#')) {
            epubReaderPanel.jumpTo(epubReaderPanel.currentLocator().copy(elementId = link.removePrefix("#")))
            return
        }
        val path = runCatching { Path.of(uri) }.getOrNull()?.toAbsolutePath()?.normalize() ?: return
        if (!path.startsWith(epub.extractionRoot.toAbsolutePath().normalize())) return
        val href = epub.extractionRoot.relativize(path).toString().replace('\\', '/')
        epubReaderPanel.jumpTo(
            ReadingLocator.EpubLocator(
                spineItemId = epub.spine.firstOrNull { it.manifestItem.href == href }?.idref,
                chapterHref = href,
                elementId = uri.fragment,
                normalizedTextOffset = null,
                scrollRatio = null,
            ),
        )
    }

    private fun showSettings() {
        if (!ReaderSettingsDialog(project).showAndGet()) return
        when (currentBook?.record?.format) {
            BookFormat.TXT -> txtReaderPanel.applySettings()
            BookFormat.EPUB -> epubReaderPanel.applySettings()
            else -> Unit
        }
    }

    private fun relocateBook(record: BookRecord) {
        saveAndFlush()
        FileChooser.chooseFile(bookDescriptor("Relocate Electronic Book"), project, null) { selectedFile ->
            libraryPanel.setLoading(true)
            libraryService.relocateBook(record, selectedFile.toNioPath()) { result ->
                result.onSuccess(::showReader).onFailure { showError("The selected file does not match this book", it) }
            }
        }
    }

    private fun removeBook(record: BookRecord) {
        val answer = Messages.showYesNoDialog(
            project,
            "Remove '${record.title}' from the library? The original file will not be deleted.",
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
        when (val parsed = book.parsedBook) {
            is TxtParsedBook -> if (txtReaderPanel.hasLoadedSlice()) {
                val locator = txtReaderPanel.currentLocator()
                val total = parsed.index.totalCharacters
                val percent = if (total <= 0) 0.0 else locator.characterOffset.toDouble() / total * 100.0
                val chapter = parsed.index.chapters.lastOrNull {
                    (it.locator as ReadingLocator.TextLocator).characterOffset <= locator.characterOffset
                }?.title
                progressService.update(book.record.id, locator, chapter, percent)
            }
            is EpubParsedBook -> if (epubReaderPanel.hasLoadedChapter()) {
                val locator = epubReaderPanel.currentLocator()
                val index = parsed.chapterIndex(locator.chapterHref, locator.spineItemId)
                val percent = if (parsed.spine.isEmpty()) 0.0 else (index + (locator.scrollRatio ?: 0.0)) / parsed.spine.size * 100.0
                progressService.update(book.record.id, locator, parsed.navigation.lastOrNull {
                    (it.locator as? ReadingLocator.EpubLocator)?.chapterHref == locator.chapterHref
                }?.title, percent)
            }
            is PdfParsedBook -> if (pdfReaderPanel.hasRenderedPage()) {
                val locator = pdfReaderPanel.currentLocator()
                val percent = if (parsed.pageCount <= 0) 0.0 else
                    (locator.pageIndex + locator.verticalRatio) / parsed.pageCount * 100.0
                progressService.update(book.record.id, locator, "Page ${locator.pageIndex + 1}", percent)
            }
            else -> Unit
        }
        progressService.flushAsync(book.record.id)
    }

    private fun bookDescriptor(title: String) = FileChooserDescriptor(true, false, false, false, false, false)
        .withTitle(title)
        .withDescription("Choose a local .txt, .text, .epub, or .pdf file. Parsing runs in the background.")
        .withFileFilter { file ->
            file.isDirectory || file.extension?.lowercase() in setOf("txt", "text", "epub", "pdf")
        }

    private fun showError(title: String, exception: Throwable) {
        EdgeReaderNotifications.error(project, title, readableMessage(exception))
    }

    private fun readableMessage(exception: Throwable): String = when {
        exception.message?.contains("missing", ignoreCase = true) == true -> "The original file has moved. Relocate it from the library."
        exception is java.nio.charset.CharacterCodingException -> "The selected encoding cannot decode this file. Choose a different encoding."
        exception is com.yinxi.edgereader.security.UnsafeArchiveException -> exception.message ?: "The EPUB archive was rejected by safety checks."
        exception is com.yinxi.edgereader.parser.epub.InvalidEpubException -> exception.message ?: "The EPUB structure is incomplete."
        exception is InvalidPdfException -> exception.message ?: "The PDF is damaged or password protected."
        else -> exception.message?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred while processing the book."
    }

    override fun dispose() {
        disposed = true
        pdfRenderJob?.cancel()
        pdfReaderPanel.disposeReader()
        saveAndFlush()
        currentBook?.parsedBook?.close()
        currentBook = null
        controller.detach(this)
    }

    companion object {
        private const val LIBRARY_CARD = "library"
        private const val READER_CARD = "reader"
        private const val TXT_READER_CARD = "txt"
        private const val EPUB_READER_CARD = "epub"
        private const val PDF_READER_CARD = "pdf"
    }
}
