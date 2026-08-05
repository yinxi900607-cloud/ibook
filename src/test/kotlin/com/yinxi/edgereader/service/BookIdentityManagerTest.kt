package com.yinxi.edgereader.service

import com.yinxi.edgereader.persistence.database.EdgeReaderDatabase
import com.yinxi.edgereader.persistence.repository.SqliteBookRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BookIdentityManagerTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `moving a TXT file preserves its book id by quick fingerprint`() {
        val firstPath = tempDirectory.resolve("original.txt")
        Files.writeString(firstPath, "第一章\n" + "正文".repeat(10_000))

        EdgeReaderDatabase(tempDirectory.resolve("identity.db")).use { database ->
            val repository = SqliteBookRepository(database)
            val identity = BookIdentityManager(repository)
            val first = identity.resolve(firstPath)
            val movedPath = tempDirectory.resolve("renamed.txt")
            Files.move(firstPath, movedPath)

            val moved = identity.resolve(movedPath)
            val copy = tempDirectory.resolve("copy.txt")
            Files.copy(movedPath, copy)

            assertEquals(first.id, moved.id)
            assertEquals(movedPath.toRealPath().toString(), moved.canonicalPath)
            assertEquals(identity.fullHash(movedPath), identity.fullHash(copy))
        }
    }

    @Test
    fun `different content produces a different fingerprint`() {
        val first = tempDirectory.resolve("first.txt").also { Files.writeString(it, "one") }
        val second = tempDirectory.resolve("second.txt").also { Files.writeString(it, "two") }
        EdgeReaderDatabase(tempDirectory.resolve("fingerprint.db")).use { database ->
            val identity = BookIdentityManager(SqliteBookRepository(database))
            assertNotEquals(identity.quickFingerprint(first), identity.quickFingerprint(second))
        }
    }
}
