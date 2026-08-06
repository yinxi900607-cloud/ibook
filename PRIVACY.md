# Edge Reader Privacy Policy

Last updated: 2026-08-06

Edge Reader is a local-first JetBrains IDE plugin. The plugin does not require an account, does not include analytics or advertising, and does not transmit books, reading positions, bookmarks, searches, file paths, hashes, or settings to the publisher.

## Data processed locally

Edge Reader processes only electronic-book files explicitly selected by the user. It stores the following local data:

- Book metadata, file paths, quick fingerprints, optional full SHA-256 hashes, progress, and bookmarks in `<IDE-system>/edge-reader/edge-reader.db`.
- Reader preferences in the IDE configuration file `edgeReader.xml`.
- TXT indexes, extracted EPUB contents, and cached cover thumbnails under `<IDE-system>/edge-reader/cache/`.
- PDF page images only in a bounded in-memory cache while the PDF is open.

Book contents and bookmark excerpts are not written to logs. Original book files are not uploaded or modified. Removing a book from the library does not delete the original file.

## Network access

Edge Reader does not make application-level network requests. Remote HTML, Markdown, and EPUB resources are blocked, scripts are removed, and local resources are restricted to the selected document directory or the plugin's protected EPUB cache.

JetBrains IDE and Marketplace services may independently perform update checks according to the user's JetBrains settings and policies; those services are outside Edge Reader.

## Data retention and deletion

Library records remain until removed by the user. Cached data remains in the IDE system directory until that IDE installation's system data is cleared. To remove all Edge Reader data, close the IDE and delete its `edge-reader` system directory and the `edgeReader.xml` settings file. Back up bookmarks first if they are needed.

## Contact

Questions and issue reports can be submitted at https://github.com/yinxi900607-cloud/ibook/issues.
