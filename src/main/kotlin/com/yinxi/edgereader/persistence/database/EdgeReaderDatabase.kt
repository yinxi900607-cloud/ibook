package com.yinxi.edgereader.persistence.database

import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class EdgeReaderDatabase(
    val databasePath: Path,
) : Closeable {
    private val lock = Any()
    private var connection: Connection? = null

    fun <T> read(block: (Connection) -> T): T = synchronized(lock) {
        block(connection())
    }

    fun <T> transaction(block: (Connection) -> T): T = synchronized(lock) {
        val connection = connection()
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (exception: Throwable) {
            connection.rollback()
            throw exception
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    fun schemaVersion(): Int = read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version").use { result ->
                if (result.next()) result.getInt(1) else 0
            }
        }
    }

    private fun connection(): Connection {
        connection?.takeUnless { it.isClosed }?.let { return it }
        Files.createDirectories(databasePath.parent)
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").also {
            connection = it
            configure(it)
            migrate(it)
        }
    }

    private fun configure(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA busy_timeout = 5000")
        }
    }

    private fun migrate(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)",
            )
        }
        val currentVersion = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version").use { result ->
                if (result.next()) result.getInt(1) else 0
            }
        }
        DatabaseMigrations.all.filter { it.version > currentVersion }.forEach { migration ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    migration.statements.forEach(statement::execute)
                }
                connection.prepareStatement("INSERT INTO schema_version(version, applied_at) VALUES (?, ?)").use {
                    it.setInt(1, migration.version)
                    it.setLong(2, System.currentTimeMillis())
                    it.executeUpdate()
                }
                connection.commit()
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    override fun close() = synchronized(lock) {
        connection?.close()
        connection = null
    }
}
