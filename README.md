# Edge Reader

![Build](https://github.com/yinxi900607-cloud/ibook/workflows/Build/badge.svg)

Edge Reader is a private, local-first electronic book reader integrated into JetBrains IDEs.

The current TXT, EPUB, PDF, Markdown, and local HTML reader provides:

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
- CommonMark-based Markdown rendering with heading navigation, code blocks, local images, links, and progress restoration.
- Sanitized local HTML reading with heading navigation, stable element/text/scroll locators, and local-resource containment.
- Cancellable, debounced cross-format search for TXT, EPUB, PDF, Markdown, and HTML.
- SQLite-backed bookmarks with current-position capture, excerpts, listing, deletion, and jump restoration.
- Compact JetBrains-native toolbars and theme-aware reading surfaces with light, dark, sepia, and soft-green themes.

## Requirements

- IntelliJ Platform 2026.2
- Java 25 for development (the bundled JBR in IntelliJ IDEA 2026.2 is supported)

The build targets and is tested against IntelliJ IDEA 2026.2.0.1. It depends only on
`com.intellij.modules.platform`; older IDE versions are intentionally outside the supported scope.

## Build

```shell
./gradlew clean test
./gradlew buildPlugin
./gradlew verifyPlugin
```

The installable archive is generated under `build/distributions/`.

For local development against an already installed IDE, pass
`-PedgeReaderLocalIdePath=/absolute/path/to/IDE/Contents`. CI and release builds use the locked remote platform by default.

## Installation

- Build the plugin ZIP.
- Open <kbd>Settings/Preferences</kbd> → <kbd>Plugins</kbd>.
- Choose <kbd>Install Plugin from Disk…</kbd> and select the ZIP in `build/distributions/`.
- Open the right-side <kbd>Edge Reader</kbd> Tool Window, or press <kbd>Option+R</kbd> on macOS / <kbd>Alt+R</kbd> on Windows and Linux.

Detailed installation and upgrade instructions are in [INSTALL.md](INSTALL.md).

All book files, reading progress, bookmarks, and caches remain local to the JetBrains IDE system directory. HTML and Markdown scripts, frames, forms, remote resources, and out-of-root local resources are blocked. See [PRIVACY.md](PRIVACY.md) for the complete local-data policy.

## Legal

- Edge Reader source and original assets: [LICENSE](LICENSE)
- Bundled open-source libraries: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
