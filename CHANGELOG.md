<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Edge Reader Changelog

## [Unreleased]
### Added
- IntelliJ Platform Gradle Plugin 2.x project targeting Java 21.
- Right-side Edge Reader Tool Window with an empty library state.
- Local electronic book chooser for planned first-release formats.
- Toggle Edge Reader action with Option+R / Alt+R default shortcut.
- JUnit 5 unit tests and plugin ZIP build.
- Streaming TXT parser with BOM detection, encoding selection, chapter indexing, and random character-offset reads.
- SQLite schema migrations and repositories for books, progress, bookmarks, chapters, and index metadata.
- Shared library with recent, all-books, and missing-file views.
- Debounced progress persistence, restart restoration, and moved-file quick fingerprint matching.
- TXT chapter navigation, font settings, and configurable Keymap actions.
