<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Edge Reader Changelog

## [Unreleased]
### Added
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
