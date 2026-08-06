# Edge Reader

![Build](https://github.com/yinxi900607-cloud/ibook/workflows/Build/badge.svg)

Edge Reader is a private, local-first electronic book reader integrated into JetBrains IDEs.

The current TXT, EPUB, and PDF reader provides:

- A collapsible right-side Tool Window and shared local library.
- UTF-8, UTF-16 LE/BE, GB18030, GBK, and Big5 handling with an encoding fallback dialog.
- Streaming chapter indexing and bounded text windows suitable for very large TXT files.
- Chapter navigation, configurable font and margins, and continuous windowed scrolling.
- SQLite-backed reading progress with 700ms debounce and a 15-second fallback save.
- Path, quick fingerprint, and background full-hash identity tracking for moved files.
- EPUB 2 NCX and EPUB 3 navigation, OPF manifest/spine parsing, cover discovery, local images, and basic CSS.
- Stable EPUB chapter/text-offset progress restoration with a Swing HTML reader.
- ZIP Slip, archive expansion, XML entity, script, remote-resource, and local path escape protections.
- PDFBox-based file-backed PDF loading, page rendering, outline navigation, page jumps, and text search.
- Current-page plus adjacent-page rendering with a bounded LRU image cache, fit-width mode, and 50%–400% zoom.
- Stable PDF restoration by page, vertical ratio, and zoom; image-only scanned PDFs remain readable without OCR.

## Requirements

- IntelliJ Platform 2026.2
- Java 25 for development (the bundled JBR in IntelliJ IDEA 2026.2 is supported)

The build currently targets IntelliJ IDEA Community 2026.2.0.1 and depends only on `com.intellij.modules.platform`.
Older platform compatibility will be validated after current-version functionality is complete.

## Build

```shell
./gradlew clean test
./gradlew buildPlugin
```

The installable archive is generated under `build/distributions/`.

For local development against an already installed IDE, pass
`-PedgeReaderLocalIdePath=/absolute/path/to/IDE/Contents`. CI and release builds use the locked remote platform by default.

## Installation

- Build the plugin ZIP.
- Open <kbd>Settings/Preferences</kbd> → <kbd>Plugins</kbd>.
- Choose <kbd>Install Plugin from Disk…</kbd> and select the ZIP in `build/distributions/`.
- Open the right-side <kbd>Edge Reader</kbd> Tool Window, or press <kbd>Option+R</kbd> on macOS / <kbd>Alt+R</kbd> on Windows and Linux.

Markdown, standalone HTML, cross-format search UI, and bookmarks are intentionally deferred to later development phases.
