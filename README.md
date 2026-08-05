# Edge Reader

![Build](https://github.com/yinxi900607-cloud/ibook/workflows/Build/badge.svg)

Edge Reader is a private, local-first electronic book reader integrated into JetBrains IDEs. Phase 0 provides a right-side Tool Window, an empty library view, a local book chooser, and a configurable Toggle Edge Reader action.

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

Book parsing and persistence are intentionally deferred to the following development phases.
