package com.yinxi.edgereader.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ResourceAccessPolicyTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `allows local resources and rejects traversal absolute URLs and symlink escapes`() {
        val root = Files.createDirectories(tempDirectory.resolve("book"))
        val document = root.resolve("chapter.html")
        val image = root.resolve("image.png")
        Files.writeString(document, "chapter")
        Files.write(image, byteArrayOf(1))
        val outside = tempDirectory.resolve("outside.txt")
        Files.writeString(outside, "private")
        Files.createSymbolicLink(root.resolve("escape.txt"), outside)

        val policy = ResourceAccessPolicy(root)
        assertEquals(image.toAbsolutePath(), policy.resolve(document, "image.png"))
        assertNull(policy.resolve(document, "../outside.txt"))
        assertNull(policy.resolve(document, "file:///etc/passwd"))
        assertNull(policy.resolve(document, "https://example.invalid/a.png"))
        assertNull(policy.resolve(document, "escape.txt"))
    }
}
