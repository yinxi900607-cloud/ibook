package com.yinxi.edgereader.security

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class UnsafeArchiveException(message: String) : IOException(message)

class SafeZipExtractor(
    private val limits: EpubSecurityLimits = EpubSecurityLimits(),
) {
    fun extract(archive: Path, destination: Path): Path {
        require(Files.isRegularFile(archive)) { "EPUB file does not exist" }
        if (Files.size(archive) > limits.maxArchiveBytes) throw UnsafeArchiveException("EPUB exceeds the 500 MB safety limit")

        val parent = destination.toAbsolutePath().normalize().parent
            ?: throw UnsafeArchiveException("Invalid EPUB cache destination")
        Files.createDirectories(parent)
        val staging = parent.resolve(".${destination.fileName}.tmp-${UUID.randomUUID()}")
        Files.createDirectory(staging)
        try {
            extractInto(archive, staging)
            Files.writeString(staging.resolve(COMPLETE_MARKER), "ok")
            if (Files.exists(destination)) deleteTree(destination)
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(staging, destination)
            }
            return destination
        } catch (exception: Throwable) {
            deleteTree(staging)
            throw exception
        }
    }

    fun isComplete(destination: Path): Boolean =
        Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(destination.resolve(COMPLETE_MARKER))

    private fun extractInto(archive: Path, root: Path) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        var entryCount = 0
        var totalBytes = 0L
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > limits.maxEntries) throw UnsafeArchiveException("EPUB contains too many ZIP entries")
                val target = safeTarget(normalizedRoot, entry)
                validateDeclaredSize(entry)
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                    continue
                }
                Files.createDirectories(target.parent)
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
                    throw UnsafeArchiveException("EPUB attempts to write through a symbolic link")
                }
                zip.getInputStream(entry).use { raw ->
                    BufferedInputStream(raw).use { input ->
                        BufferedOutputStream(Files.newOutputStream(target)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                entryBytes += count
                                totalBytes += count
                                if (entryBytes > limits.maxEntryBytes) throw UnsafeArchiveException("EPUB ZIP entry is too large")
                                if (totalBytes > limits.maxTotalBytes) throw UnsafeArchiveException("EPUB expands beyond the total safety limit")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun validateDeclaredSize(entry: ZipEntry) {
        if (entry.size > limits.maxEntryBytes) throw UnsafeArchiveException("EPUB ZIP entry is too large")
        if (entry.size > 0 && entry.compressedSize == 0L) throw UnsafeArchiveException("EPUB ZIP entry has an invalid compression ratio")
        if (entry.size > 0 && entry.compressedSize > 0) {
            val ratio = entry.size.toDouble() / entry.compressedSize
            if (ratio > limits.maxCompressionRatio) throw UnsafeArchiveException("EPUB ZIP entry has a suspicious compression ratio")
        }
    }

    private fun safeTarget(root: Path, entry: ZipEntry): Path {
        val name = entry.name.replace('\\', '/')
        if (name.isBlank() || name.startsWith('/') || WINDOWS_ABSOLUTE.matches(name)) {
            throw UnsafeArchiveException("EPUB contains an absolute ZIP path")
        }
        val target = root.resolve(name).normalize()
        if (!target.startsWith(root)) throw UnsafeArchiveException("EPUB contains an unsafe ZIP path")
        return target
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    companion object {
        private const val COMPLETE_MARKER = ".edge-reader-complete"
        private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:/.*")
    }
}
