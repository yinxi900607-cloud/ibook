package com.yinxi.edgereader.parser.txt

import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class TxtCheckpoint(
    val byteOffset: Long,
    val characterOffset: Long,
)

data class TxtBookIndex(
    val charsetName: String,
    val bomLength: Int,
    val totalCharacters: Long,
    val checkpoints: List<TxtCheckpoint>,
    val chapters: List<BookNavigationItem>,
    val fileSize: Long,
    val modifiedAt: Long,
)

data class TextSlice(
    val startOffset: Long,
    val text: String,
    val totalCharacters: Long,
)

object TxtBookIndexer {
    private const val BYTE_BUFFER_SIZE = 256 * 1024
    private const val CHAR_BUFFER_SIZE = 64 * 1024
    private const val CHECKPOINT_INTERVAL = 64 * 1024L
    private const val MAX_INDEXED_LINE_LENGTH = 16 * 1024
    private const val MAX_CHAPTERS = 200_000

    fun build(file: Path, charset: Charset, bomLength: Int): TxtBookIndex {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.allocate(BYTE_BUFFER_SIZE).apply { limit(0) }
        val output = CharBuffer.allocate(CHAR_BUFFER_SIZE)
        val checkpoints = mutableListOf(TxtCheckpoint(bomLength.toLong(), 0))
        val chapters = mutableListOf<BookNavigationItem>()
        val line = StringBuilder()
        var lineStart = 0L
        var totalCharacters = 0L
        var lastCheckpoint = 0L
        var bufferStart = bomLength.toLong()
        var endOfInput = false

        Files.newByteChannel(file, StandardOpenOption.READ).use { channel ->
            channel.position(bomLength.toLong())
            while (true) {
                if (!endOfInput && !input.hasRemaining()) {
                    input.clear()
                    endOfInput = channel.read(input) < 0
                    input.flip()
                }

                output.clear()
                val result = decoder.decode(input, output, endOfInput)
                output.flip()
                while (output.hasRemaining()) {
                    val character = output.get()
                    if (character == '\n') {
                        addChapterIfPresent(line, lineStart, chapters)
                        line.setLength(0)
                        lineStart = totalCharacters + 1
                    } else if (line.length < MAX_INDEXED_LINE_LENGTH) {
                        line.append(character)
                    }
                    totalCharacters++
                }

                if (totalCharacters - lastCheckpoint >= CHECKPOINT_INTERVAL && input.position() > 0) {
                    checkpoints += TxtCheckpoint(bufferStart + input.position(), totalCharacters)
                    lastCheckpoint = totalCharacters
                }

                if (result.isError) result.throwException()
                if (result.isOverflow) continue
                if (endOfInput) break

                val consumed = input.position()
                input.compact()
                bufferStart += consumed
                endOfInput = channel.read(input) < 0
                input.flip()
            }
        }

        if (line.isNotEmpty()) addChapterIfPresent(line, lineStart, chapters)
        val fileSize = Files.size(file)
        if (checkpoints.last().characterOffset != totalCharacters) {
            checkpoints += TxtCheckpoint(fileSize, totalCharacters)
        }
        return TxtBookIndex(
            charsetName = charset.name(),
            bomLength = bomLength,
            totalCharacters = totalCharacters,
            checkpoints = checkpoints,
            chapters = chapters,
            fileSize = fileSize,
            modifiedAt = Files.getLastModifiedTime(file).toMillis(),
        )
    }

    private fun addChapterIfPresent(
        line: StringBuilder,
        characterOffset: Long,
        chapters: MutableList<BookNavigationItem>,
    ) {
        val title = line.toString().removeSuffix("\r").trim()
        if (chapters.size < MAX_CHAPTERS && TxtChapterDetector.isChapterTitle(title)) {
            chapters += BookNavigationItem(
                title = title,
                locator = ReadingLocator.TextLocator(characterOffset, null, null),
            )
        }
    }
}
