<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Edge Reader Changelog

## [Unreleased]

## [0.6.1] - 2026-08-10
### Fixed
- Ensured reading themes override EPUB-authored fixed page backgrounds and text colors while preserving book layout, images, and links.

## [0.6.0] - 2026-08-07
### Changed
- Set IntelliJ IDEA 2026.2 as the minimum and currently tested platform.
- Updated build and release automation to Java 25 and attached the built ZIP to GitHub release drafts.
- Replaced large Swing button rows with compact IntelliJ Action Toolbars and theme-aware SVG actions.
- Refined library rows, reading status bars, typography, line spacing, paragraph spacing, and reading themes.

### Fixed
- Removed XHTML XML declarations before EPUB chapters reach Swing HTML rendering, preventing `?xml version=...?>` from appearing as book text.

### Added
- Installation, privacy, proprietary license, third-party notices, and Marketplace publishing documentation.
- Environment-backed JetBrains Marketplace signing and publishing configuration.
- IntelliJ Platform Gradle Plugin 2.x project targeting the current IntelliJ 2026.2 runtime.
- Right-side Edge Reader Tool Window with an empty library state.
- Local electronic book chooser for planned first-release formats.
- Toggle Edge Reader action with Option+R / Alt+R default shortcut.
- JUnit 5 unit tests and plugin ZIP build.
- Streaming TXT parser with BOM detection, encoding selection, chapter indexing, and random character-offset reads.
- SQLite schema migrations and repositories for books, progress, bookmarks, chapters, and index metadata.
- Shared library with recent, all-books, and missing-file views.
- Debounced progress persistence, restart restoration, and moved-file quick fingerprint matching.
- TXT chapter navigation, font settings, and configurable Keymap actions.
- EPUB 2 and EPUB 3 package, manifest, spine, navigation, metadata, and cover parsing.
- Secure EPUB extraction with entry, size, compression-ratio, path, and XML entity protections.
- Sanitized Swing HTML chapter rendering with local images, basic CSS, and remote resources disabled.
- Stable EPUB progress locators using spine ID, chapter href, element ID, normalized text offset, and scroll ratio.
- Current IntelliJ IDEA 2026.2 platform as the primary runtime target.
- Java 25 toolchain and bytecode target, matching the current IntelliJ 2026.2 runtime.
- PDFBox 3 file-backed PDF loading with metadata, outlines, per-page text extraction, and cancellable search.
- Background current/adjacent page rendering, bounded LRU image cache, page jump, fit-width, and custom zoom.
- PDF page/vertical-position/zoom persistence and compatibility for image-only scanned PDFs without OCR.
- Next Page and Previous Page Keymap actions.
- CommonMark Markdown parsing with heading navigation, code blocks, safe local images and links, and stable reading restoration.
- Sanitized local HTML parsing and reading with script/frame/form removal, remote-resource blocking, and symlink-aware local path containment.
- Debounced, cancellable search dialog shared by TXT, EPUB, PDF, Markdown, and HTML readers.
- SQLite-backed bookmark creation, listing, deletion, and stable locator restoration for all reader formats.
- Add Bookmark and Search Current Book actions for customizable IDE Keymaps.
