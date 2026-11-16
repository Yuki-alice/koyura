package app.koyura.media.novel.epub

import app.koyura.media.epub.openEpubNovel
import app.koyura.media.fixtureRoot
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.useImport
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** EPUB fixtures and helpers shared by the EPUB test suites. */
object EpubFixtures {
    fun path(name: String) = fixtureRoot().toPath() / "epub" / name

    fun open(name: String) = openEpubNovel(FileSystem.SYSTEM, path(name))
}

class EpubRealFixtureTest {

    @Test
    fun prideAndPrejudiceMetadataMatchesPackageDocument() {
        EpubFixtures.open("pride-and-prejudice.epub").useImport { novel ->
            assertEquals("Pride and Prejudice", novel.metadata.title)
            assertEquals(listOf("Jane Austen"), novel.metadata.authors)
            assertEquals("Jane Austen", novel.metadata.author)
            assertEquals("en-GB", novel.metadata.language)
            assertEquals("epub/images/cover.jpg", novel.metadata.cover?.path)
            assertEquals("image/jpeg", novel.metadata.cover?.mediaType)
        }
    }

    @Test
    fun prideAndPrejudiceCoverBytesAreTheDeclaredJpeg() {
        EpubFixtures.open("pride-and-prejudice.epub").useImport { novel ->
            val cover = novel.metadata.cover!!
            val bytes = novel.readResource(cover.path)
            assertEquals(389354, bytes.size)
            // JPEG SOI marker.
            assertEquals(0xFF, bytes[0].toInt() and 0xFF)
            assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        }
    }

    @Test
    fun prideAndPrejudiceTableOfContentsFollowsNavAndSpineOrder() {
        EpubFixtures.open("pride-and-prejudice.epub").useImport { novel ->
            val titles = novel.tableOfContents.map { it.title }
            assertTrue(titles.size >= 60, "expected the full novel, got ${titles.size}")
            assertTrue("Titlepage" in titles)
            assertTrue("I" in titles)
            // Nav order is preserved: I comes before II, and both before III.
            assertTrue(titles.indexOf("I") < titles.indexOf("II"))
            assertTrue(titles.indexOf("II") < titles.indexOf("III"))
            // Every entry has a stable, unique id.
            assertEquals(titles.size, novel.tableOfContents.map { it.id }.distinct().size)
        }
    }

    @Test
    fun prideAndPrejudiceChapterBodyIsStructuredText() {
        EpubFixtures.open("pride-and-prejudice.epub").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.first { it.title == "I" }.id)
            assertEquals("I", chapter.title)
            val headings = chapter.blocks.filterIsInstance<NovelBlock.Heading>()
            assertEquals(1, headings.size)
            // The Standard Ebooks source marks the chapter number with <h2>, so level 2 is correct.
            assertEquals(2, headings.single().level)
            assertEquals("I", headings.single().text)
            val paragraphs = chapter.blocks.filterIsInstance<NovelBlock.Paragraph>()
            assertTrue(paragraphs.size > 5)
            assertEquals(
                "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
                paragraphs.first().text,
            )
            // Inline <em>/<abbr> text must be flattened, not dropped.
            assertTrue(paragraphs.any { it.text.contains("Mr. Bennet") })
        }
    }

    @Test
    fun coleMetadataNavAndInlineImages() {
        EpubFixtures.open("cole-voyage-of-life.epub").useImport { novel ->
            assertEquals("Thomas Cole - The Voyage of Life", novel.metadata.title)
            assertEquals(listOf("Jesse Dylan"), novel.metadata.authors)
            assertEquals("en-US", novel.metadata.language)
            assertEquals("EPUB/images/portrait.jpg", novel.metadata.cover?.path)

            val titles = novel.tableOfContents.map { it.title }
            assertTrue("Childhood" in titles)
            assertTrue(titles.indexOf("Childhood") < titles.indexOf("Youth"))

            // Find an image-bearing chapter and read its bytes from the archive.
            var checked = 0
            for (entry in novel.tableOfContents) {
                val images = novel.readChapter(entry.id).blocks.filterIsInstance<NovelBlock.Image>()
                for (image in images) {
                    val resource = image.reference.resource ?: continue
                    assertTrue(resource.path.startsWith("EPUB/images/"), "unexpected path ${resource.path}")
                    val bytes = novel.readResource(resource.path)
                    // Exact sizes from `unzip -l cole-voyage-of-life.epub`, so an image cannot pass
                    // merely by being "big enough".
                    val expected = when (resource.path) {
                        "EPUB/images/1-childhood-320.jpg" -> 72811
                        "EPUB/images/1-childhood-1024.jpg" -> 149915
                        "EPUB/images/2-youth-320.jpg" -> 80124
                        "EPUB/images/2-youth-1024.jpg" -> 238651
                        "EPUB/images/portrait.jpg" -> 12559
                        else -> null
                    }
                    expected?.let { assertEquals(it, bytes.size, "size mismatch for ${resource.path}") }
                    // Independent of the table, a JPEG SOI marker proves the bytes are an image.
                    assertEquals(0xFF, bytes[0].toInt() and 0xFF)
                    assertEquals(0xD8, bytes[1].toInt() and 0xFF)
                    checked++
                }
                if (checked >= 4) break
            }
            assertTrue(checked >= 4, "expected multiple inline images, saw $checked")
        }
    }

    @Test
    fun colePaintingPagesAppearInReadingOrderEvenWhenNavOmitsThem() {
        EpubFixtures.open("cole-voyage-of-life.epub").useImport { novel ->
            // The nav only lists the text pages, but the spine includes the painting pages; spine
            // order must win so no content is lost.
            val chapterDocs = (novel as app.koyura.media.epub.EpubNovel).chapterDocuments
            assertTrue(chapterDocs.any { it.endsWith("1b-childhood-painting.xhtml") }, "painting page missing")
            assertTrue(chapterDocs.any { it.endsWith("4b-oldage-painting.xhtml") })
        }
    }
}

class EpubSpineOrderTest {

    @Test
    fun readingOrderFollowsSpineNotManifest() {
        EpubFixtures.open("spine-order.epub").useImport { novel ->
            assertEquals(listOf("First In Nav", "Second In Nav"), novel.tableOfContents.map { it.title })
            assertEquals(
                listOf(
                    "This is the FIRST spine document.",
                    "This is the SECOND spine document.",
                ),
                novel.tableOfContents.map { entry ->
                    novel.readChapter(entry.id).blocks.filterIsInstance<NovelBlock.Paragraph>().single().text
                },
            )
        }
    }
}

class EpubReferenceTest {

    @Test
    fun resolvesSpacesPercentEncodingDotSegmentsAndFragments() {
        EpubFixtures.open("refs.epub").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            val images = chapter.blocks.filterIsInstance<NovelBlock.Image>()
            assertEquals(5, images.size)

            val byAlt = images.associateBy { it.reference.altText }
            assertEquals("OEBPS/images/a b.png", byAlt["space encoded"]!!.reference.resource!!.path)
            assertEquals("OEBPS/images/a b.png", byAlt["literal space"]!!.reference.resource!!.path)
            assertEquals("OEBPS/images/deep/c.png", byAlt["deep"]!!.reference.resource!!.path)
            assertEquals("OEBPS/images/deep/c.png", byAlt["fragment"]!!.reference.resource!!.path)
            assertEquals("OEBPS/images/渡口.png", byAlt["cjk"]!!.reference.resource!!.path)

            // The resolved bytes are readable and match the stored file.
            assertEquals(24, novel.readResource("OEBPS/images/a b.png").size)
            assertEquals(23, novel.readResource("OEBPS/images/deep/c.png").size)
        }
    }

    @Test
    fun remoteAndEscapingReferencesExposeNoResource() {
        EpubFixtures.open("unsafe-ref.epub").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            val images = chapter.blocks.filterIsInstance<NovelBlock.Image>()
            val byAlt = images.associateBy { it.reference.altText }

            assertEquals(null, byAlt["escapes"]!!.reference.resource)
            assertEquals(null, byAlt["remote"]!!.reference.resource)
            assertEquals(null, byAlt["absolute"]!!.reference.resource)
            assertEquals("OEBPS/images/ok.png", byAlt["ok"]!!.reference.resource!!.path)

            // Remote and escaping references keep their raw text for debugging.
            assertEquals("https://example.com/remote.png", byAlt["remote"]!!.reference.source)
            assertEquals("../../../outside.png", byAlt["escapes"]!!.reference.source)
        }
    }

    @Test
    fun resolverRejectsUnsafeFormsWithTypedErrors() {
        val archive = "/books/test.epub"
        fun reason(reference: String): String = assertFailsWith<NovelImportException.UnsafeReference> {
            app.koyura.media.epub.EpubPathResolver.resolve(archive, "OEBPS/text/ch.xhtml", reference)
        }.reason

        assertEquals("escapes-root", reason("../../../etc/passwd"))
        assertEquals("absolute", reason("/META-INF/container.xml"))
        assertEquals("remote", reason("https://example.com/x.png"))
        assertEquals("remote", reason("//example.com/x.png"))
        assertEquals("remote", reason("data:image/png;base64,AAAA"))
        assertEquals("bad-percent-encoding", reason("images/bad%E0%A4.png"))
    }
}

class EpubErrorTest {

    @Test
    fun corruptedArchiveIsReportedAsCorruptedNotIo() {
        val error = assertFailsWith<NovelImportException.CorruptedArchive> {
            EpubFixtures.open("corrupted.epub")
        }
        assertEquals("not-a-zip", error.detail)
        assertEquals(null, error.entry)
        assertTrue(error.message!!.contains("corrupted.epub"))
    }

    @Test
    fun missingSpineDocumentIsReportedAtOpen() {
        val error = assertFailsWith<NovelImportException.MissingEntry> {
            EpubFixtures.open("missing-entry.epub")
        }
        assertTrue(error.entry.endsWith("chapter-2.xhtml"), "got ${error.entry}")
        assertTrue(error.message!!.contains("content.opf"))
    }

    @Test
    fun encryptedSpineDocumentBlocksOpening() {
        val error = assertFailsWith<NovelImportException.EncryptedContent> {
            EpubFixtures.open("encrypted.epub")
        }
        assertTrue(error.entry.endsWith("chapter-2.xhtml"), "got ${error.entry}")
        assertEquals("http://www.w3.org/2001/04/xmlenc#aes256-cbc", error.algorithm)
    }

    @Test
    fun encryptedFontOnlyDoesNotBlockReading() {
        EpubFixtures.open("encrypted-font.epub").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            assertEquals(
                listOf("Still readable."),
                chapter.blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }
}
