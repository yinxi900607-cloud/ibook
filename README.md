# Edge Reader

![Build](https://github.com/yinxi900607-cloud/ibook/workflows/Build/badge.svg)

Edge Reader is a private, local-first electronic book reader integrated into JetBrains IDEs.

The current TXT MVP provides:

- A collapsible right-side Tool Window and shared local library.
- UTF-8, UTF-16 LE/BE, GB18030, GBK, and Big5 handling with an encoding fallback dialog.
- Streaming chapter indexing and bounded text windows suitable for very large TXT files.
- Chapter navigation, configurable font and margins, and continuous windowed scrolling.
- SQLite-backed reading progress with 700ms debounce and a 15-second fallback save.
- Path, quick fingerprint, and background full-hash identity tracking for moved files.

## Requirements

- IntelliJ Platform 2024.2 or newer
- Java 21 for development

The build targets IntelliJ IDEA Community 2025.2.6.2 and depends only on `com.intellij.modules.platform`.

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

EPUB, PDF, Markdown, HTML, search, and bookmarks are intentionally deferred to later development phases.
