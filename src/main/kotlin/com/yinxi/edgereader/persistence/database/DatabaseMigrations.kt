package com.yinxi.edgereader.persistence.database

data class DatabaseMigration(
    val version: Int,
    val statements: List<String>,
)

object DatabaseMigrations {
    val all = listOf(
        DatabaseMigration(
            version = 1,
            statements = listOf(
                """
                CREATE TABLE IF NOT EXISTS books (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    author TEXT,
                    format TEXT NOT NULL,
                    current_path TEXT NOT NULL,
                    canonical_path TEXT,
                    file_name TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    modified_at INTEGER NOT NULL,
                    quick_fingerprint TEXT NOT NULL,
                    content_hash TEXT,
                    cover_cache_path TEXT,
                    encoding TEXT,
                    imported_at INTEGER NOT NULL,
                    last_opened_at INTEGER,
                    last_read_at INTEGER,
                    reading_duration_seconds INTEGER NOT NULL DEFAULT 0,
                    progress_percent REAL NOT NULL DEFAULT 0,
                    missing INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
                """
                CREATE TABLE IF NOT EXISTS reading_progress (
                    book_id TEXT PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
                    locator_json TEXT NOT NULL,
                    chapter_title TEXT,
                    progress_percent REAL NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE IF NOT EXISTS bookmarks (
                    id TEXT PRIMARY KEY,
                    book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                    locator_json TEXT NOT NULL,
                    title TEXT,
                    excerpt TEXT,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE IF NOT EXISTS chapter_index (
                    book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                    ordinal INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    locator_json TEXT NOT NULL,
                    level INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (book_id, ordinal)
                )
                """.trimIndent(),
                """
                CREATE TABLE IF NOT EXISTS search_index_metadata (
                    book_id TEXT PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
                    index_version INTEGER NOT NULL,
                    source_modified_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            ),
        ),
        DatabaseMigration(
            version = 2,
            statements = listOf(
                "CREATE INDEX IF NOT EXISTS idx_books_canonical_path ON books(canonical_path)",
                "CREATE INDEX IF NOT EXISTS idx_books_quick_fingerprint ON books(quick_fingerprint)",
                "CREATE INDEX IF NOT EXISTS idx_books_content_hash ON books(content_hash)",
                "CREATE INDEX IF NOT EXISTS idx_books_last_read_at ON books(last_read_at DESC)",
                "CREATE INDEX IF NOT EXISTS idx_bookmarks_book_id ON bookmarks(book_id)",
            ),
        ),
    )
}
