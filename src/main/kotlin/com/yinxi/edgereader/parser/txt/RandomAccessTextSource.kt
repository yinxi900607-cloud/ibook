package com.yinxi.edgereader.parser.txt

import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class RandomAccessTextSource(
    private val file: Path,
    private val index: TxtBookIndex,
) {
    private val charset = Charset.forName(index.charsetName)

    fun read(characterOffset: Long, maxCharacters: Int): TextSlice {
        require(maxCharacters > 0) { "maxCharacters must be positive" }
        val boundedOffset = characterOffset.coerceIn(0, index.totalCharacters)
        val checkpoint = index.checkpoints.lastOrNull { it.characterOffset <= boundedOffset }
            ?: index.checkpoints.first()
        val relativeSkip = boundedOffset - checkpoint.characterOffset

        Files.newByteChannel(file, StandardOpenOption.READ).use { channel ->
            channel.position(checkpoint.byteOffset)
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            Channels.newReader(channel, decoder, 16 * 1024).use { reader ->
                skipFully(reader, relativeSkip)
                val result = StringBuilder(maxCharacters)
                val buffer = CharArray(minOf(16 * 1024, maxCharacters))
                while (result.length < maxCharacters) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, maxCharacters - result.length))
                    if (count < 0) break
                    result.append(buffer, 0, count)
                }
                return TextSlice(boundedOffset, result.toString(), index.totalCharacters)
            }
        }
    }

    private fun skipFully(reader: java.io.Reader, characters: Long) {
        var remaining = characters
        val discard = CharArray(8 * 1024)
        while (remaining > 0) {
            val count = reader.read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
            if (count < 0) return
            remaining -= count
        }
    }
}
