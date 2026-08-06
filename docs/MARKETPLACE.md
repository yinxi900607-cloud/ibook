# Edge Reader Marketplace Copy

## Name

Edge Reader

## Short description

A private, local-first TXT, EPUB, PDF, Markdown, and HTML reader inside IntelliJ IDEA.

## Description

Read electronic books without leaving IntelliJ IDEA. Edge Reader lives in a collapsible right-side Tool Window, so the reading surface disappears when it is not needed and never takes over the editor.

Features:

- Local TXT, EPUB, PDF, Markdown, and HTML reading.
- Shared library, chapter or outline navigation, search, bookmarks, and automatic progress restoration.
- Large TXT files use streaming indexes and bounded reading windows.
- PDF rendering is page-based with bounded adjacent-page caching and zoom controls.
- EPUB 2/3 navigation, local images, basic styles, and stable chapter/text-position restoration.
- Theme-aware typography, light, dark, sepia, and soft-green reading themes.
- Moved-file recovery using paths, quick fingerprints, and background SHA-256 hashes.

Privacy and safety:

- No account, analytics, advertising, cloud sync, or publisher-operated network service.
- Books, paths, searches, bookmarks, and progress stay on the user's computer.
- Remote HTML resources and scripts are blocked.
- EPUB archives are checked for path traversal, expansion limits, and unsafe XML.

Supported platform: IntelliJ IDEA 2026.2.x, build 262.

## Release notes for 0.6.0

- Complete local reading flow for TXT, EPUB, PDF, Markdown, and HTML.
- Cross-format cancellable search and persistent bookmarks.
- Stable progress restoration and moved-file recognition.
- Compact JetBrains-native reader controls and theme-aware presentation.
- Secure EPUB and HTML resource handling.

## Links

- Source and issues: https://github.com/yinxi900607-cloud/ibook
- Privacy: https://github.com/yinxi900607-cloud/ibook/blob/main/PRIVACY.md
- Installation: https://github.com/yinxi900607-cloud/ibook/blob/main/INSTALL.md
