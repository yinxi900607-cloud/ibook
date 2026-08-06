package com.yinxi.edgereader.parser.epub

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubTestBooks {
    fun create(directory: Path, epub3: Boolean = true): Path {
        val file = directory.resolve(if (epub3) "epub3.epub" else "epub2.epub")
        val opf = if (epub3) epub3Opf() else epub2Opf()
        val navigation = if (epub3) mapOf("OEBPS/nav.xhtml" to epub3Nav()) else mapOf("OEBPS/toc.ncx" to epub2Ncx())
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to container().toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/text/ch1.xhtml" to chapterOne().toByteArray(),
            "OEBPS/text/ch2.xhtml" to chapterTwo().toByteArray(),
            "OEBPS/styles/main.css" to css().toByteArray(),
            "OEBPS/images/pixel.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
        ) + navigation.mapValues { it.value.toByteArray() }
        writeZip(file, entries)
        return file
    }

    fun writeZip(file: Path, entries: Map<String, ByteArray>) {
        Files.newOutputStream(file).use { output ->
            ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun container() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private fun epub3Opf() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="book-id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="book-id">edge-reader-test-3</dc:identifier>
            <dc:title>Edge Reader EPUB 3</dc:title><dc:creator>yinxi test</dc:creator>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="chapter-1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="chapter-2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
            <item id="css" href="styles/main.css" media-type="text/css"/>
            <item id="image" href="images/pixel.png" media-type="image/png" properties="cover-image"/>
          </manifest>
          <spine><itemref idref="chapter-1"/><itemref idref="chapter-2"/></spine>
        </package>
    """.trimIndent()

    private fun epub2Opf() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package version="2.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="book-id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="book-id">edge-reader-test-2</dc:identifier>
            <dc:title>Edge Reader EPUB 2</dc:title><dc:creator>yinxi test</dc:creator>
            <meta name="cover" content="image"/>
          </metadata>
          <manifest>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            <item id="chapter-1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="chapter-2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
            <item id="css" href="styles/main.css" media-type="text/css"/>
            <item id="image" href="images/pixel.png" media-type="image/png"/>
          </manifest>
          <spine toc="ncx"><itemref idref="chapter-1"/><itemref idref="chapter-2"/></spine>
        </package>
    """.trimIndent()

    private fun epub3Nav() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
          <body><nav epub:type="toc"><ol>
            <li><a href="text/ch1.xhtml#start">The Beginning</a>
              <ol><li><a href="text/ch1.xhtml#part">A Nested Part</a></li></ol>
            </li>
            <li><a href="text/ch2.xhtml">The End</a></li>
          </ol></nav></body>
        </html>
    """.trimIndent()

    private fun epub2Ncx() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <navMap>
            <navPoint id="n1"><navLabel><text>NCX Beginning</text></navLabel><content src="text/ch1.xhtml#start"/>
              <navPoint id="n1a"><navLabel><text>NCX Nested</text></navLabel><content src="text/ch1.xhtml#part"/></navPoint>
            </navPoint>
            <navPoint id="n2"><navLabel><text>NCX End</text></navLabel><content src="text/ch2.xhtml"/></navPoint>
          </navMap>
        </ncx>
    """.trimIndent()

    private fun chapterOne() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head>
          <title>One</title><link rel="stylesheet" href="../styles/main.css"/>
          <script>alert('never')</script>
        </head><body onclick="steal()">
          <h1 id="start">The Beginning</h1><p>Alpha searchable words.</p>
          <h2 id="part">A Nested Part</h2><img src="../images/pixel.png"/>
          <img src="https://example.invalid/tracker.png"/><iframe src="https://example.invalid/"/>
        </body></html>
    """.trimIndent()

    private fun chapterTwo() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head>
          <body><h1>The End</h1><p>Omega searchable words.</p></body></html>
    """.trimIndent()

    private fun css() = """
        @import url('https://example.invalid/remote.css');
        body { color: #222; background-image: url(https://example.invalid/tracker.png); }
        p { background-image: url('../images/pixel.png'); }
        div { background-image: url('../../../../outside.txt'); }
    """.trimIndent()
}
