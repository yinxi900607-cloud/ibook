package com.yinxi.edgereader.parser.txt

import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object TxtIndexCache {
    private const val MAGIC = 0x45525458
    private const val VERSION = 1
    private const val MAX_CHECKPOINTS = 1_000_000
    private const val MAX_CHAPTERS = 200_000

    fun load(cacheFile: Path, sourceFile: Path, charset: Charset, bomLength: Int): TxtBookIndex? {
        if (!Files.isRegularFile(cacheFile)) return null
        return runCatching {
            DataInputStream(BufferedInputStream(Files.newInputStream(cacheFile))).use { input ->
                require(input.readInt() == MAGIC)
                require(input.readInt() == VERSION)
                val cachedCharset = input.readUTF()
                val cachedBomLength = input.readInt()
                val fileSize = input.readLong()
                val modifiedAt = input.readLong()
                val totalCharacters = input.readLong()
                require(cachedCharset.equals(charset.name(), ignoreCase = true))
                require(cachedBomLength == bomLength)
                require(fileSize == Files.size(sourceFile))
                require(modifiedAt == Files.getLastModifiedTime(sourceFile).toMillis())
                val checkpointCount = input.readInt().also { require(it in 1..MAX_CHECKPOINTS) }
                val checkpoints = List(checkpointCount) { TxtCheckpoint(input.readLong(), input.readLong()) }
                val chapterCount = input.readInt().also { require(it in 0..MAX_CHAPTERS) }
                val chapters = List(chapterCount) {
                    BookNavigationItem(
                        title = input.readUTF(),
                        locator = ReadingLocator.TextLocator(input.readLong(), null, null),
                        level = input.readInt(),
                    )
                }
                TxtBookIndex(cachedCharset, cachedBomLength, totalCharacters, checkpoints, chapters, fileSize, modifiedAt)
            }
        }.getOrNull()
    }

    fun save(cacheFile: Path, index: TxtBookIndex) {
        if (loadMetadataMatches(cacheFile, index)) return
        Files.createDirectories(cacheFile.parent)
        val temporary = cacheFile.resolveSibling("${cacheFile.fileName}.tmp")
        DataOutputStream(BufferedOutputStream(Files.newOutputStream(temporary))).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeUTF(index.charsetName)
            output.writeInt(index.bomLength)
            output.writeLong(index.fileSize)
            output.writeLong(index.modifiedAt)
            output.writeLong(index.totalCharacters)
            output.writeInt(index.checkpoints.size)
            index.checkpoints.forEach { checkpoint ->
                output.writeLong(checkpoint.byteOffset)
                output.writeLong(checkpoint.characterOffset)
            }
            output.writeInt(index.chapters.size)
            index.chapters.forEach { chapter ->
                output.writeUTF(chapter.title.take(65_535))
                output.writeLong((chapter.locator as ReadingLocator.TextLocator).characterOffset)
                output.writeInt(chapter.level)
            }
        }
        try {
            Files.move(temporary, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun loadMetadataMatches(cacheFile: Path, index: TxtBookIndex): Boolean {
        if (!Files.isRegularFile(cacheFile)) return false
        return runCatching {
            DataInputStream(BufferedInputStream(Files.newInputStream(cacheFile))).use { input ->
                input.readInt() == MAGIC && input.readInt() == VERSION &&
                    input.readUTF() == index.charsetName && input.readInt() == index.bomLength &&
                    input.readLong() == index.fileSize && input.readLong() == index.modifiedAt
            }
        }.getOrDefault(false)
    }
}
