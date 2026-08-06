package com.yinxi.edgereader.parser.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path

object PdfTestBooks {
    fun createTextPdf(directory: Path): Path {
        val file = directory.resolve("text.pdf")
        PDDocument().use { document ->
            document.documentInformation.title = "Edge Reader PDF"
            document.documentInformation.author = "yinxi test"
            repeat(3) { index ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
                    content.newLineAtOffset(72f, 700f)
                    content.showText("Page ${index + 1}: ${if (index == 1) "Omega searchable phrase" else "sample content"}")
                    content.endText()
                }
            }
            val outline = PDDocumentOutline()
            document.documentCatalog.documentOutline = outline
            outline.addLast(PDOutlineItem().apply {
                title = "Second Page"
                destination = PDPageFitDestination().apply { page = document.getPage(1) }
            })
            document.save(file.toFile())
        }
        return file
    }

    fun createBlankPdf(directory: Path, pages: Int = 1): Path {
        val file = directory.resolve("blank-$pages.pdf")
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(file.toFile())
        }
        return file
    }

    fun createScannedPdf(directory: Path): Path {
        val file = directory.resolve("scan.pdf")
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            val image = BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB).apply {
                val graphics = createGraphics()
                try {
                    graphics.color = Color.WHITE
                    graphics.fillRect(0, 0, width, height)
                    graphics.color = Color.BLACK
                    graphics.fillRect(20, 20, 40, 40)
                } finally {
                    graphics.dispose()
                }
            }
            val pdfImage = LosslessFactory.createFromImage(document, image)
            PDPageContentStream(document, page).use { it.drawImage(pdfImage, 72f, 600f, 80f, 80f) }
            document.save(file.toFile())
            image.flush()
        }
        return file
    }

    fun createPasswordProtectedPdf(directory: Path): Path {
        val file = directory.resolve("protected.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(StandardProtectionPolicy("owner-secret", "user-secret", AccessPermission()).apply {
                encryptionKeyLength = 128
            })
            document.save(file.toFile())
        }
        return file
    }
}
