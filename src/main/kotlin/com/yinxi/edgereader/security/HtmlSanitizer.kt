package com.yinxi.edgereader.security

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.nodes.XmlDeclaration
import org.jsoup.parser.Parser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class SanitizedHtml(
    val html: String,
    val visibleText: String,
)

class HtmlSanitizer(
    private val extractionRoot: Path,
    private val allowLocalStyleSheets: Boolean = true,
) {
    private val access = ResourceAccessPolicy(extractionRoot)

    fun sanitize(sourceFile: Path): SanitizedHtml {
        require(access.contains(sourceFile)) { "HTML document is outside the EPUB cache" }
        val source = Files.readString(sourceFile, StandardCharsets.UTF_8)
        return sanitize(source, sourceFile, xml = true)
    }

    fun sanitizeHtml(sourceFile: Path): SanitizedHtml {
        require(access.contains(sourceFile)) { "HTML document is outside the allowed directory" }
        return sanitize(Files.readString(sourceFile, StandardCharsets.UTF_8), sourceFile, xml = false)
    }

    fun sanitizeGeneratedHtml(html: String, sourceFile: Path): SanitizedHtml {
        require(access.contains(sourceFile)) { "HTML document is outside the allowed directory" }
        return sanitize(html, sourceFile, xml = false)
    }

    private fun sanitize(source: String, sourceFile: Path, xml: Boolean): SanitizedHtml {
        val parser = if (xml) Parser.xmlParser() else Parser.htmlParser()
        val document = Jsoup.parse(source, sourceFile.toUri().toString(), parser)
        document.childNodes()
            .filter { it is XmlDeclaration || it is DocumentType }
            .forEach { it.remove() }
        document.outputSettings().syntax(Document.OutputSettings.Syntax.html)
        removeDangerousContent(document, sourceFile)
        if (!allowLocalStyleSheets) document.select("link[rel=stylesheet]").remove()
        rewriteResources(document, sourceFile)
        ensureDocumentStructure(document)
        return SanitizedHtml(document.outerHtml(), document.text())
    }

    fun sanitizeStyleSheets() {
        Files.walk(extractionRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".css", true) }
                .forEach { cssFile ->
                    val original = runCatching { Files.readString(cssFile, StandardCharsets.UTF_8) }.getOrNull() ?: return@forEach
                    Files.writeString(cssFile, sanitizeCss(original, cssFile), StandardCharsets.UTF_8)
                }
        }
    }

    private fun removeDangerousContent(document: Document, sourceFile: Path) {
        document.select("script, iframe, frame, frameset, object, embed, applet, base, form, input, button, textarea, select, meta[http-equiv]")
            .remove()
        document.allElements.forEach { element ->
            val unsafe = element.attributes().asList().map { it.key }.filter {
                it.startsWith("on", true) || it.equals("srcdoc", true) || it.equals("formaction", true) ||
                    it.equals("background", true) || it.equals("poster", true) || it.equals("xlink:href", true)
            }
            unsafe.forEach(element::removeAttr)
            element.attributes().asList().filter {
                val value = it.value.trim().lowercase()
                value.startsWith("javascript:") || value.startsWith("vbscript:")
            }.forEach { element.removeAttr(it.key) }
            element.attr("style").takeIf { it.isNotBlank() }?.let { element.attr("style", sanitizeCss(it, sourceFile)) }
        }
        document.select("style").forEach { style ->
            val safeCss = sanitizeCss(style.data(), sourceFile)
            style.empty().appendText(safeCss)
        }
    }

    private fun rewriteResources(document: Document, sourceFile: Path) {
        document.select("img[src], image[href], source[src], audio[src], video[src], link[href]").forEach { element ->
            val attribute = if (element.hasAttr("src")) "src" else "href"
            rewriteAttribute(element, attribute, sourceFile, allowFragmentOnly = false)
        }
        document.select("a[href]").forEach { element ->
            rewriteAttribute(element, "href", sourceFile, allowFragmentOnly = true)
        }
        document.select("*[srcset]").forEach { it.removeAttr("srcset") }
    }

    private fun rewriteAttribute(element: Element, attribute: String, sourceFile: Path, allowFragmentOnly: Boolean) {
        val value = element.attr(attribute).trim()
        if (allowFragmentOnly && value.startsWith('#')) return
        val fragment = value.substringAfter('#', "").takeIf { '#' in value }
        val target = access.resolve(sourceFile, value)
        if (target == null || !Files.isRegularFile(target)) {
            element.removeAttr(attribute)
            return
        }
        val local = buildString {
            append(target.toUri().toASCIIString())
            if (fragment != null) append('#').append(fragment)
        }
        element.attr(attribute, local)
    }

    private fun ensureDocumentStructure(document: Document) {
        document.head().appendElement("meta").attr("charset", "UTF-8")
    }

    companion object {
        private val CSS_URL = Regex("(?is)url\\s*\\(\\s*(['\"]?)([^)'\"]+)\\1\\s*\\)")
        private val ANY_IMPORT = Regex("(?is)@import\\s+[^;]+;?")
        private val DANGEROUS_CSS = Regex("(?is)(?:expression\\s*\\(|javascript:|-moz-binding\\s*:|behavior\\s*:)")

        fun sanitizeCss(css: String): String = css
            .replace(ANY_IMPORT, "")
            .replace(CSS_URL) { match ->
                val value = match.groupValues[2].trim()
                if (value.startsWith('#') || (!value.contains(':') && !value.startsWith('/') && ".." !in value)) match.value else "url()"
            }
            .replace(DANGEROUS_CSS, "")
    }

    private fun sanitizeCss(css: String, sourceFile: Path?): String = css
        .replace(ANY_IMPORT, "")
        .replace(CSS_URL) { match ->
            val value = match.groupValues[2].trim()
            if (value.startsWith('#')) return@replace match.value
            val target = sourceFile?.let { access.resolve(it, value) }
            if (target != null && Files.isRegularFile(target)) "url('${target.toUri().toASCIIString()}')" else "url()"
        }
        .replace(DANGEROUS_CSS, "")
}
