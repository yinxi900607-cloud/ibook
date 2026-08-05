package com.yinxi.edgereader.service

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookRecord
import com.yinxi.edgereader.persistence.repository.BookRepository
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

class BookIdentityManager(
    private val repository: BookRepository,
) {
    fun resolve(file: Path, encoding: String? = null): BookRecord {
        require(Files.isRegularFile(file)) { "Book file does not exist: $file" }
        val canonical = file.toRealPath().toString()
        val size = Files.size(file)
        val modifiedAt = Files.getLastModifiedTime(file).toMillis()
        val fingerprint = quickFingerprint(file)

        repository.findByCanonicalPath(canonical)?.let { existing ->
            return existing.copy(
                currentPath = file.toAbsolutePath().normalize().toString(),
                canonicalPath = canonical,
                fileSize = size,
                modifiedAt = modifiedAt,
                quickFingerprint = fingerprint,
                encoding = encoding ?: existing.encoding,
                missing = false,
            ).also(repository::upsert)
        }

        repository.findByQuickFingerprint(fingerprint)?.let { existing ->
            repository.updatePath(existing.id, file.toAbsolutePath().normalize().toString(), canonical, size, modifiedAt)
            if (encoding != null) repository.updateEncoding(existing.id, encoding)
            return repository.findById(existing.id) ?: existing
        }

        val now = System.currentTimeMillis()
        return BookRecord(
            id = UUID.randomUUID().toString(),
            title = file.fileName.toString().substringBeforeLast('.'),
            author = null,
            format = BookFormat.TXT,
            currentPath = file.toAbsolutePath().normalize().toString(),
            canonicalPath = canonical,
            fileName = file.fileName.toString(),
            fileSize = size,
            modifiedAt = modifiedAt,
            quickFingerprint = fingerprint,
            contentHash = null,
            coverCachePath = null,
            encoding = encoding,
            importedAt = now,
            lastOpenedAt = null,
            lastReadAt = null,
            readingDurationSeconds = 0,
            progressPercent = 0.0,
            missing = false,
        ).also(repository::upsert)
    }

    fun quickFingerprint(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val size = Files.size(file)
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size).array())
        FileChannel.open(file, StandardOpenOption.READ).use { channel ->
            updateDigest(channel, 0, minOf(FINGERPRINT_BLOCK_SIZE.toLong(), size), digest)
            val tailStart = maxOf(0, size - FINGERPRINT_BLOCK_SIZE)
            updateDigest(channel, tailStart, size - tailStart, digest)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    fun fullHash(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun updateDigest(channel: FileChannel, position: Long, length: Long, digest: MessageDigest) {
        channel.position(position)
        var remaining = length
        val buffer = ByteBuffer.allocate(minOf(FINGERPRINT_BLOCK_SIZE, maxOf(1, length.toInt())))
        while (remaining > 0) {
            buffer.clear()
            buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
            val count = channel.read(buffer)
            if (count < 0) break
            buffer.flip()
            digest.update(buffer)
            remaining -= count
        }
    }

    companion object {
        private const val FINGERPRINT_BLOCK_SIZE = 1024 * 1024
    }
}
