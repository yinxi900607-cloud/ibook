package com.yinxi.edgereader.security

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class ResourceAccessPolicy(root: Path) {
    private val root = root.toAbsolutePath().normalize()

    fun resolve(document: Path, reference: String): Path? {
        val cleaned = reference.trim().substringBefore('#').substringBefore('?')
        if (cleaned.isBlank()) return null
        val uri = runCatching { URI(cleaned) }.getOrNull() ?: return null
        if (uri.isAbsolute || cleaned.startsWith("//") || cleaned.startsWith('/')) return null
        val decoded = runCatching { URLDecoder.decode(cleaned, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val resolved = document.parent.resolve(decoded).normalize().toAbsolutePath()
        return resolved.takeIf { it.startsWith(root) }
    }

    fun contains(path: Path): Boolean = path.toAbsolutePath().normalize().startsWith(root)
}
