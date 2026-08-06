package com.yinxi.edgereader.parser.epub

import com.yinxi.edgereader.model.BookFormat
import com.yinxi.edgereader.model.BookMetadata
import com.yinxi.edgereader.model.BookNavigationItem
import com.yinxi.edgereader.model.ReadingLocator
import com.yinxi.edgereader.security.ResourceAccessPolicy
import com.yinxi.edgereader.security.SecureXml
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class EpubPackage(
    val metadata: BookMetadata,
    val packageFile: Path,
    val coverFile: Path?,
    val manifest: Map<String, EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    val navigation: List<BookNavigationItem>,
)

class InvalidEpubException(message: String, cause: Throwable? = null) : Exception(message, cause)

class EpubPackageReader(
    private val extractionRoot: Path,
) {
    private val root = extractionRoot.toAbsolutePath().normalize()
    private val access = ResourceAccessPolicy(root)

    fun read(): EpubPackage {
        val containerFile = root.resolve("META-INF/container.xml")
        if (!Files.isRegularFile(containerFile)) throw InvalidEpubException("EPUB is missing META-INF/container.xml")
        val container = parseXml(containerFile, "EPUB container.xml is invalid")
        val rootfile = container.elements("rootfile").firstOrNull()?.attribute("full-path")
            ?.takeIf { it.isNotBlank() }
            ?: throw InvalidEpubException("EPUB container does not reference an OPF package")
        val packageFile = safeRootPath(rootfile) ?: throw InvalidEpubException("EPUB package path is unsafe")
        if (!Files.isRegularFile(packageFile)) throw InvalidEpubException("EPUB OPF package is missing")

        val opf = parseXml(packageFile, "EPUB OPF package is invalid")
        val title = opf.elements("title").firstOrNull()?.textContent?.trim().orEmpty()
            .ifBlank { packageFile.fileName.toString().substringBeforeLast('.') }
        val author = opf.elements("creator").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }
        val manifest = readManifest(opf, packageFile)
        val spineElement = opf.elements("spine").firstOrNull()
            ?: throw InvalidEpubException("EPUB OPF package has no spine")
        val spine = spineElement.childElements("itemref").mapNotNull { itemRef ->
            val idref = itemRef.attribute("idref") ?: return@mapNotNull null
            manifest[idref]?.let {
                EpubSpineItem(idref, it, itemRef.attribute("linear")?.lowercase() != "no")
            }
        }
        if (spine.isEmpty()) throw InvalidEpubException("EPUB spine contains no readable chapters")
        val coverFile = readCover(opf, manifest)
        val navigation = readNavigation(opf, spineElement.attribute("toc"), manifest, spine)
            .ifEmpty { fallbackNavigation(spine) }
        return EpubPackage(BookMetadata(title, author, BookFormat.EPUB), packageFile, coverFile, manifest, spine, navigation)
    }

    private fun readCover(opf: Document, manifest: Map<String, EpubManifestItem>): Path? {
        manifest.values.firstOrNull { "cover-image" in it.properties }?.let { return it.file.takeIf(Files::isRegularFile) }
        val coverId = opf.elements("meta").firstOrNull {
            it.attribute("name")?.equals("cover", true) == true
        }?.attribute("content")
        return coverId?.let(manifest::get)?.file?.takeIf(Files::isRegularFile)
    }

    private fun readManifest(opf: Document, packageFile: Path): Map<String, EpubManifestItem> {
        val packageDirectory = packageFile.parent
        return opf.elements("manifest").firstOrNull()?.childElements("item").orEmpty().mapNotNull { item ->
            val id = item.attribute("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = item.attribute("href")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val file = resolveRelative(packageDirectory, href) ?: throw InvalidEpubException("EPUB manifest path is unsafe: $href")
            val rootHref = root.relativize(file).toString().replace('\\', '/')
            EpubManifestItem(
                id = id,
                href = rootHref,
                mediaType = item.attribute("media-type").orEmpty(),
                properties = item.attribute("properties").orEmpty().split(Regex("\\s+")).filter(String::isNotBlank).toSet(),
                file = file,
            )
        }.associateBy { it.id }
    }

    private fun readNavigation(
        opf: Document,
        tocId: String?,
        manifest: Map<String, EpubManifestItem>,
        spine: List<EpubSpineItem>,
    ): List<BookNavigationItem> {
        val navItem = manifest.values.firstOrNull { "nav" in it.properties }
        if (navItem != null && Files.isRegularFile(navItem.file)) return readEpub3Navigation(navItem, spine)
        val ncxItem = tocId?.let(manifest::get) ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        if (ncxItem != null && Files.isRegularFile(ncxItem.file)) return readNcxNavigation(ncxItem, spine)
        return emptyList()
    }

    private fun readEpub3Navigation(navItem: EpubManifestItem, spine: List<EpubSpineItem>): List<BookNavigationItem> {
        val document = Jsoup.parse(Files.readString(navItem.file), navItem.file.toUri().toString(), Parser.xmlParser())
        val toc = document.getElementsByTag("nav").firstOrNull {
            it.attributes().asList().any { attribute ->
                (attribute.key.equals("epub:type", true) || attribute.key.equals("type", true)) &&
                    attribute.value.split(Regex("\\s+")).any { value -> value.equals("toc", true) }
            }
        } ?: document.getElementsByTag("nav").firstOrNull() ?: return emptyList()
        val results = mutableListOf<BookNavigationItem>()
        toc.children().filter { it.tagName().equals("ol", true) || it.tagName().equals("ul", true) }
            .forEach { readNavList(it, navItem.file, spine, 1, results) }
        return results
    }

    private fun readNavList(
        list: Element,
        navFile: Path,
        spine: List<EpubSpineItem>,
        level: Int,
        results: MutableList<BookNavigationItem>,
    ) {
        list.children().filter { it.tagName().equals("li", true) }.forEach { li ->
            val anchor = li.children().firstOrNull { it.tagName().equals("a", true) }
            if (anchor != null) locator(navFile, anchor.attr("href"), spine)?.let { locator ->
                results += BookNavigationItem(anchor.text().ifBlank { "Untitled" }, locator, level)
            }
            li.children().filter { it.tagName().equals("ol", true) || it.tagName().equals("ul", true) }
                .forEach { readNavList(it, navFile, spine, level + 1, results) }
        }
    }

    private fun readNcxNavigation(ncxItem: EpubManifestItem, spine: List<EpubSpineItem>): List<BookNavigationItem> {
        val document = parseXml(ncxItem.file, "EPUB NCX document is invalid")
        val results = mutableListOf<BookNavigationItem>()
        document.elements("navMap").firstOrNull()?.childElements("navPoint")?.forEach {
            readNavPoint(it, ncxItem.file, spine, 1, results)
        }
        return results
    }

    private fun readNavPoint(node: Node, ncxFile: Path, spine: List<EpubSpineItem>, level: Int, results: MutableList<BookNavigationItem>) {
        val title = node.childElements("navLabel").firstOrNull()?.elements("text")?.firstOrNull()?.textContent?.trim().orEmpty()
        val src = node.childElements("content").firstOrNull()?.attribute("src")
        if (!src.isNullOrBlank()) locator(ncxFile, src, spine)?.let {
            results += BookNavigationItem(title.ifBlank { "Untitled" }, it, level)
        }
        node.childElements("navPoint").forEach { readNavPoint(it, ncxFile, spine, level + 1, results) }
    }

    private fun locator(document: Path, href: String, spine: List<EpubSpineItem>): ReadingLocator.EpubLocator? {
        val file = resolveRelative(document.parent, href) ?: return null
        val rootHref = root.relativize(file).toString().replace('\\', '/')
        val spineItem = spine.firstOrNull { it.manifestItem.href.substringBefore('#') == rootHref }
        return ReadingLocator.EpubLocator(
            spineItemId = spineItem?.idref,
            chapterHref = rootHref,
            elementId = href.substringAfter('#', "").takeIf { it.isNotBlank() },
            normalizedTextOffset = null,
            scrollRatio = null,
        )
    }

    private fun fallbackNavigation(spine: List<EpubSpineItem>) = spine.mapIndexed { index, item ->
        BookNavigationItem(
            title = "Chapter ${index + 1}",
            locator = ReadingLocator.EpubLocator(item.idref, item.manifestItem.href, null, 0, 0.0),
        )
    }

    private fun resolveRelative(directory: Path, href: String): Path? {
        val clean = href.substringBefore('#').substringBefore('?')
        val decoded = runCatching { URLDecoder.decode(clean, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val file = directory.resolve(decoded).normalize().toAbsolutePath()
        return file.takeIf(access::contains)
    }

    private fun safeRootPath(path: String): Path? {
        val decoded = runCatching { URLDecoder.decode(path, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val file = root.resolve(decoded).normalize()
        return file.takeIf(access::contains)
    }

    private fun parseXml(file: Path, message: String): Document = try {
        Files.newInputStream(file).use(SecureXml::parse)
    } catch (exception: Exception) {
        throw InvalidEpubException(message, exception)
    }

    private fun Document.elements(localName: String): List<Node> {
        val namespaced = getElementsByTagNameNS("*", localName)
        if (namespaced.length > 0) return (0 until namespaced.length).map(namespaced::item)
        val plain = getElementsByTagName(localName)
        return (0 until plain.length).map(plain::item)
    }

    private fun Node.childElements(localName: String): List<Node> = buildList {
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            if (child.nodeType == Node.ELEMENT_NODE && (child.localName ?: child.nodeName.substringAfter(':')) == localName) add(child)
        }
    }

    private fun Node.elements(localName: String): List<Node> {
        val result = mutableListOf<Node>()
        fun visit(current: Node) {
            if (current.nodeType == Node.ELEMENT_NODE && (current.localName ?: current.nodeName.substringAfter(':')) == localName) result += current
            for (index in 0 until current.childNodes.length) visit(current.childNodes.item(index))
        }
        visit(this)
        return result
    }

    private fun Node.attribute(name: String): String? = attributes?.getNamedItem(name)?.nodeValue
}
