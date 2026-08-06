package com.yinxi.edgereader.security

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class ResourceAccessPolicy(root: Path) {
    private val root = root.toAbsolutePath().normalize()
    private val realRoot = runCatching { this.root.toRealPath() }.getOrDefault(this.root)

    fun resolve(document: Path, reference: String): Path? {
        val cleaned = reference.trim().substringBefore('#').substringBefore('?')
        if (cleaned.isBlank()) return null
        val uri = runCatching { URI(cleaned) }.getOrNull() ?: return null
        if (uri.isAbsolute || cleaned.startsWith("//") || cleaned.startsWith('/')) return null
        val decoded = runCatching { URLDecoder.decode(cleaned, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val resolved = document.parent.resolve(decoded).normalize().toAbsolutePath()
        return resolved.takeIf(::contains)
    }

    fun contains(path: Path): Boolean {
        val normalized = path.toAbsolutePath().normalize()
        if (!normalized.startsWith(root)) return false
        val real = runCatching { normalized.toRealPath() }.getOrNull() ?: return true
        return real.startsWith(realRoot)
    }
}
