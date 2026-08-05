package com.yinxi.edgereader.persistence.database

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import java.nio.file.Path

@Service(Service.Level.APP)
class EdgeReaderDatabaseService : Disposable {
    private val lock = Any()
    @Volatile
    private var database: EdgeReaderDatabase? = null

    fun getDatabase(): EdgeReaderDatabase = database ?: synchronized(lock) {
        database ?: EdgeReaderDatabase(defaultDatabasePath()).also { database = it }
    }

    fun dataDirectory(): Path = Path.of(PathManager.getSystemPath(), "edge-reader")

    override fun dispose() {
        synchronized(lock) {
            database?.close()
            database = null
        }
    }

    private fun defaultDatabasePath(): Path = dataDirectory().resolve("edge-reader.db")
}
