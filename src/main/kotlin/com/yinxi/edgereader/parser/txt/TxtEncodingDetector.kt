package com.yinxi.edgereader.parser.txt

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

data class EncodingDetectionResult(
    val charset: Charset,
    val bomLength: Int,
    val reliable: Boolean,
    val candidates: List<Charset>,
)

object TxtEncodingDetector {
    private const val SAMPLE_SIZE = 1024 * 1024
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private val legacyCharsets = listOf("GB18030", "GBK", "Big5").map(Charset::forName)

    fun detect(file: Path): EncodingDetectionResult {
        val sample = Files.newInputStream(file).use { input -> input.readNBytes(SAMPLE_SIZE) }
        return detect(sample)
    }

    fun detect(sample: ByteArray): EncodingDetectionResult {
        bom(sample, utf8Bom)?.let {
            return EncodingDetectionResult(StandardCharsets.UTF_8, utf8Bom.size, true, listOf(StandardCharsets.UTF_8))
        }
        bom(sample, utf16LeBom)?.let {
            return EncodingDetectionResult(StandardCharsets.UTF_16LE, utf16LeBom.size, true, listOf(StandardCharsets.UTF_16LE))
        }
        bom(sample, utf16BeBom)?.let {
            return EncodingDetectionResult(StandardCharsets.UTF_16BE, utf16BeBom.size, true, listOf(StandardCharsets.UTF_16BE))
        }

        detectBomlessUtf16(sample)?.let {
            return EncodingDetectionResult(it, 0, true, listOf(it))
        }

        val utf8 = decodeSample(sample, StandardCharsets.UTF_8)
        if (utf8 != null) {
            return EncodingDetectionResult(StandardCharsets.UTF_8, 0, true, listOf(StandardCharsets.UTF_8))
        }

        val scored = legacyCharsets.mapNotNull { charset ->
            decodeSample(sample, charset)?.let { decoded -> charset to quality(decoded) }
        }.sortedByDescending { it.second }

        if (scored.isEmpty()) {
            return EncodingDetectionResult(StandardCharsets.UTF_8, 0, false, supportedCharsets())
        }

        val best = scored.first()
        val difference = best.second - (scored.getOrNull(1)?.second ?: 0.0)
        return EncodingDetectionResult(
            charset = best.first,
            bomLength = 0,
            reliable = difference >= 0.08,
            candidates = scored.map { it.first },
        )
    }

    fun supportedCharsets(): List<Charset> = listOf(
        StandardCharsets.UTF_8,
        StandardCharsets.UTF_16LE,
        StandardCharsets.UTF_16BE,
    ) + legacyCharsets

    private fun bom(bytes: ByteArray, bom: ByteArray): Unit? =
        if (bytes.size >= bom.size && bom.indices.all { bytes[it] == bom[it] }) Unit else null

    private fun detectBomlessUtf16(bytes: ByteArray): Charset? {
        if (bytes.size < 8) return null
        val evenZeros = bytes.indices.count { it % 2 == 0 && bytes[it] == 0.toByte() }
        val oddZeros = bytes.indices.count { it % 2 == 1 && bytes[it] == 0.toByte() }
        val pairs = bytes.size / 2.0
        return when {
            oddZeros / pairs > 0.35 && evenZeros / pairs < 0.1 -> StandardCharsets.UTF_16LE
            evenZeros / pairs > 0.35 && oddZeros / pairs < 0.1 -> StandardCharsets.UTF_16BE
            else -> null
        }
    }

    private fun decodeSample(bytes: ByteArray, charset: Charset): String? {
        for (trim in 0..minOf(4, bytes.size)) {
            try {
                val decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(bytes, 0, bytes.size - trim)).toString()
            } catch (_: CharacterCodingException) {
                // A sample may end in the middle of a multi-byte character.
            }
        }
        return null
    }

    private fun quality(text: String): Double {
        if (text.isEmpty()) return 0.0
        var printable = 0
        var controls = 0
        var cjk = 0
        for (character in text) {
            when {
                character == '\n' || character == '\r' || character == '\t' -> printable++
                Character.isISOControl(character) -> controls++
                else -> printable++
            }
            if (character.code in 0x3400..0x9FFF) cjk++
        }
        val length = text.length.toDouble()
        return printable / length + cjk / length * 0.15 - controls / length * 4.0 - abs(cjk / length - 0.35) * 0.02
    }
}
