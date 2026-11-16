package app.koyura.media.novel.epub

import app.koyura.media.epub.EpubXhtmlParser
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.SeparatorStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the XHTML → [NovelBlock] conversion, using inline documents.
 *
 * These are the cases that are hard to reach through a fixture: every heading level, `<br>` inside a
 * paragraph, nested inline markup, list/table flattening, and the exclusion rules.
 */
class EpubXhtmlParserTest {

    private fun parse(body: String): List<NovelBlock> =
        EpubXhtmlParser.parse("book.epub", "text/ch.xhtml", "<html><body>$body</body></html>").blocks

    @Test
    fun everyHeadingLevelMapsToItsNumber() {
        val blocks = parse(
            "<h1>one</h1><h2>two</h2><h3>three</h3><h4>four</h4><h5>five</h5><h6>six</h6>",
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            blocks.filterIsInstance<NovelBlock.Heading>().map { it.level },
        )
        assertEquals(
            listOf("one", "two", "three", "four", "five", "six"),
            blocks.filterIsInstance<NovelBlock.Heading>().map { it.text },
        )
    }

    @Test
    fun brBecomesSpaceAndInlineMarkupIsFlattened() {
        val blocks = parse("<p>line1<br/>line2 <em>emphasised</em> text</p>")
        assertEquals(listOf("line1 line2 emphasised text"), blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text })
    }

    @Test
    fun nestedBlockContainersAreRecursed() {
        val blocks = parse("<div><section><p>deep</p><p>deeper</p></section></div>")
        assertEquals(listOf("deep", "deeper"), blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text })
    }

    @Test
    fun hrBecomesSeparator() {
        val blocks = parse("<p>a</p><hr/><p>b</p>")
        assertEquals(1, blocks.filterIsInstance<NovelBlock.Separator>().size)
        assertEquals(SeparatorStyle.DASHES, blocks.filterIsInstance<NovelBlock.Separator>().single().style)
    }

    @Test
    fun listItemsAndTableRowsBecomeParagraphs() {
        val list = parse("<ul><li>one</li><li>two</li></ul>")
        assertEquals(listOf("one", "two"), list.filterIsInstance<NovelBlock.Paragraph>().map { it.text })

        val table = parse("<table><tr><td>a</td><td>b</td></tr></table>")
        assertEquals(listOf("a b"), table.filterIsInstance<NovelBlock.Paragraph>().map { it.text })
    }

    @Test
    fun scriptStyleAndHeadAreNeverContent() {
        val blocks = parse(
            "<head><title>T</title><style>p{}</style></head>" +
                "<script>alert(1)</script><p>real</p><style>.x{}</style>",
        )
        assertEquals(listOf("real"), blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text })
    }

    @Test
    fun imageOnlyParagraphBecomesImageBlockNotAltText() {
        val blocks = parse("<p><img src=\"images/a.png\" alt=\"cap\"/></p>")
        assertEquals(1, blocks.size)
        val image = blocks.single() as NovelBlock.Image
        assertEquals("images/a.png", image.reference.source)
        assertEquals("cap", image.reference.altText)
        assertEquals("text/images/a.png", image.reference.resource?.path)
        assertTrue(blocks.none { it is NovelBlock.Paragraph })
    }

    @Test
    fun textAroundAnInlineImageKeepsOrder() {
        val blocks = parse("<p>before<img src=\"i.png\" alt=\"a\"/>after</p>")
        assertEquals(
            listOf("before", "after"),
            blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
        )
        assertEquals(1, blocks.filterIsInstance<NovelBlock.Image>().size)
        // The image sits between the two paragraphs in block order.
        assertEquals(true, blocks.indexOfFirst { it is NovelBlock.Image } in 1..1)
    }

    @Test
    fun remoteImageHasNoResourceButKeepsSource() {
        val blocks = parse("<p><img src=\"https://example.com/x.png\" alt=\"r\"/></p>")
        val image = blocks.single() as NovelBlock.Image
        assertNull(image.reference.resource)
        assertEquals("https://example.com/x.png", image.reference.source)
    }

    @Test
    fun fallbackTitlePrefersDocumentTitleThenFirstHeading() {
        val withTitle = EpubXhtmlParser.parse(
            "book.epub",
            "c.xhtml",
            "<html><head><title>Doc Title</title></head><body><h1>Heading</h1></body></html>",
        )
        assertEquals("Doc Title", withTitle.fallbackTitle)

        val withoutTitle = EpubXhtmlParser.parse(
            "book.epub",
            "c.xhtml",
            "<html><head></head><body><h2>Heading Text</h2></body></html>",
        )
        assertEquals("Heading Text", withoutTitle.fallbackTitle)
    }

    @Test
    fun emptyParagraphsAreDroppedButBlankStructureIsSafe() {
        assertEquals(emptyList(), parse("<p>   </p><div></div>"))
    }
}

class EpubPackageParserTest {

    private val archive = "/books/x.epub"

    @Test
    fun packageWithoutMetadataIsStillUsable() {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
        """.trimIndent()
        val pkg = app.koyura.media.epub.EpubPackageParser.parsePackage(archive, "OEBPS/content.opf", opf)
        assertEquals("", pkg.title)
        assertEquals(emptyList(), pkg.authors)
        assertNull(pkg.language)
        assertEquals(1, pkg.spine.size)
    }

    @Test
    fun manifestItemEscapingRootIsSkippedButDoesNotAbort() {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
              <manifest>
                <item id="bad" href="../../outside.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
        """.trimIndent()
        val pkg = app.koyura.media.epub.EpubPackageParser.parsePackage(archive, "OEBPS/content.opf", opf)
        assertTrue("bad" !in pkg.manifest)
        assertEquals("OEBPS/c1.xhtml", pkg.manifest["c1"]?.path)
    }

    @Test
    fun spineIdrefWithoutManifestItemIsMissingEntry() {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
              <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="missing"/></spine>
            </package>
        """.trimIndent()
        val error = kotlin.test.assertFailsWith<app.koyura.media.novel.NovelImportException.MissingEntry> {
            app.koyura.media.epub.EpubPackageParser.parsePackage(archive, "OEBPS/content.opf", opf)
        }
        assertEquals("missing", error.entry)
        assertEquals("OEBPS/content.opf", error.declaredBy)
    }

    @Test
    fun epub2CoverMetaIsUsedWhenNoCoverImageProperty() {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>T</dc:title>
                <meta name="cover" content="cover-id"/>
              </metadata>
              <manifest>
                <item id="cover-id" href="images/legacy.jpg" media-type="image/jpeg"/>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
        """.trimIndent()
        val pkg = app.koyura.media.epub.EpubPackageParser.parsePackage(archive, "OEBPS/content.opf", opf)
        assertEquals("OEBPS/images/legacy.jpg", pkg.cover?.path)
    }

    @Test
    fun linearNoIsAuxiliaryAndExcludedFromReadingSpine() {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
              <manifest>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                <item id="aux" href="aux.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c1"/><itemref idref="aux" linear="no"/></spine>
            </package>
        """.trimIndent()
        val pkg = app.koyura.media.epub.EpubPackageParser.parsePackage(archive, "OEBPS/content.opf", opf)
        assertEquals(listOf(false, true), pkg.spine.map { it.isAuxiliary })
    }

    @Test
    fun encryptionUsesNamespacePrefixedElements() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes256-cbc"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/text/x.xhtml"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
        """.trimIndent()
        val encrypted = app.koyura.media.epub.EpubPackageParser.parseEncryption(archive, xml)
        assertEquals("http://www.w3.org/2001/04/xmlenc#aes256-cbc", encrypted["OEBPS/text/x.xhtml"])
    }

    @Test
    fun malformedEncryptionXmlIsTreatedAsEmpty() {
        val encrypted = app.koyura.media.epub.EpubPackageParser.parseEncryption(archive, "<not-really-xml")
        assertTrue(encrypted.isEmpty())
    }
}
