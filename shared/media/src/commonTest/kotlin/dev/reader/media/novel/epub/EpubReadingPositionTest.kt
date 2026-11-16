package dev.reader.media.novel.epub

import dev.reader.media.novel.CodePoints
import dev.reader.media.novel.LocalNovelLimits
import dev.reader.media.novel.NovelBlock
import dev.reader.media.novel.NovelFingerprints
import dev.reader.media.novel.PositionResolution
import dev.reader.media.novel.useImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reading-position contract applied to EPUB, so both formats behave identically.
 *
 * EPUB chapter ids come from document paths, so they are stable across opens independently of text;
 * content version comes from the archive's size/CRC records.
 */
class EpubReadingPositionTest {

    @Test
    fun chapterIdsAndVersionAreStableAcrossOpens() {
        val first = EpubFixtures.open("spine-order.epub")
        val second = EpubFixtures.open("spine-order.epub")
        try {
            assertEquals(
                first.tableOfContents.map { it.id },
                second.tableOfContents.map { it.id },
            )
            assertEquals(first.contentVersion, second.contentVersion)
            // Version is a 16-hex-character fingerprint, not an empty placeholder.
            assertEquals(16, first.contentVersion.length)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun positionIsExactOnReopen() {
        EpubFixtures.open("spine-order.epub").useImport { novel ->
            val entry = novel.tableOfContents.first { it.level == 1 }
            val paragraphIndex = novel.readChapter(entry.id).blocks
                .indexOfFirst { it is NovelBlock.Paragraph }
            assertTrue(paragraphIndex >= 0)
            val stored = novel.positionOf(entry.id, paragraphIndex, 4)

            EpubFixtures.open("spine-order.epub").useImport { reopened ->
                val resolution = reopened.resolvePosition(stored)
                assertEquals(PositionResolution.ResolutionKind.EXACT, resolution.resolution)
                assertEquals(stored, resolution.position)
                assertEquals(entry.id, resolution.position.chapterId)
                assertEquals(4, resolution.position.codePointOffset)
            }
        }
    }

    @Test
    fun codepointOffsetsHandleEmojiInsideEpubParagraphs() {
        // Build an EPUB in memory with an emoji paragraph so the EPUB path is exercised end to end.
        val bytes = InMemoryEpub.build(
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Emoji</dc:title></metadata>
                  <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent(),
            extraEntries = mapOf(
                "OEBPS/c1.xhtml" to "<html><body><p>他笑😀了。</p></body></html>",
            ),
        )
        InMemoryEpub.open(bytes, "/books/emoji.epub").useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            val paragraph = novel.readChapter(chapterId).blocks
                .filterIsInstance<NovelBlock.Paragraph>().single().text
            assertEquals("他笑😀了。", paragraph)

            // Offset 3 is right after the emoji (one code point), which is string index 4.
            val stored = novel.positionOf(chapterId, blockIndex = 0, codePointOffset = 3)
            assertEquals(3, stored.codePointOffset)
            assertEquals(4, CodePoints.toStringIndex(paragraph, 3))

            novel.resolvePosition(stored).let { resolution ->
                assertEquals(PositionResolution.ResolutionKind.EXACT, resolution.resolution)
                assertEquals(3, resolution.position.codePointOffset)
            }
        }
    }

    @Test
    fun resolveAfterCloseThrows() {
        val novel = EpubFixtures.open("spine-order.epub")
        val entry = novel.tableOfContents.first { it.level == 1 }
        val stored = novel.positionOf(entry.id, 0, 0)
        novel.close()
        kotlin.test.assertFailsWith<dev.reader.media.novel.NovelClosedException> {
            novel.resolvePosition(stored)
        }
        kotlin.test.assertFailsWith<dev.reader.media.novel.NovelClosedException> {
            novel.positionOf(entry.id, 0, 0)
        }
    }

    @Test
    fun fingerprintOfEpubParagraphIsContentBased() {
        EpubFixtures.open("spine-order.epub").useImport { novel ->
            val entry = novel.tableOfContents.first { it.level == 1 }
            val chapter = novel.readChapter(entry.id)
            val index = chapter.blocks.indexOfFirst { it is NovelBlock.Paragraph }
            val text = (chapter.blocks[index] as NovelBlock.Paragraph).text
            val position = novel.positionOf(entry.id, index, 0)
            assertEquals(NovelFingerprints.block(text), position.blockFingerprint)
        }
    }

    // ---- Resume must not read the whole book ----

    /**
     * Builds a two-chapter EPUB whose second chapter exceeds a 1024-byte chapter cap.
     *
     * The nav is valid, so the table of contents is available without touching either body. The first
     * chapter is short and resumes exactly; the second must still fail when read directly.
     */
    private fun overLimitSecondChapterEpub(): ByteArray = InMemoryEpub.build(
        opf = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Resume</dc:title></metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>
        """.trimIndent(),
        extraEntries = mapOf(
            "OEBPS/nav.xhtml" to """
                <html xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc">
                <ol><li><a href="c1.xhtml">One</a></li><li><a href="c2.xhtml">Two</a></li></ol>
                </nav></body></html>
            """.trimIndent(),
            "OEBPS/c1.xhtml" to "<html><body><p>Readable first chapter.</p></body></html>",
            "OEBPS/c2.xhtml" to "<html><body><p>${"long ".repeat(500)}</p></body></html>",
        ),
    )

    @Test
    fun exactResumeDoesNotReadAnOverLimitLaterChapter() {
        val limits = LocalNovelLimits(maxChapterBytes = 1024)
        InMemoryEpub.open(overLimitSecondChapterEpub(), "/books/resume.epub", limits).useImport { novel ->
            val first = novel.tableOfContents.first { it.level == 1 }
            val last = novel.tableOfContents.last { it.level == 1 }
            val paragraphIndex = novel.readChapter(first.id).blocks
                .indexOfFirst { it is NovelBlock.Paragraph }
            assertTrue(paragraphIndex >= 0, "first chapter must expose a paragraph")
            val stored = novel.positionOf(first.id, paragraphIndex, codePointOffset = 3)

            // The defect: resolving the first chapter used to parse the over-limit second chapter.
            val resolution = novel.resolvePosition(stored)
            assertEquals(PositionResolution.ResolutionKind.EXACT, resolution.resolution)
            assertEquals(stored, resolution.position)
            assertEquals(first.id, resolution.position.chapterId)
            assertEquals(paragraphIndex, resolution.position.blockIndex)
            assertEquals(3, resolution.position.codePointOffset)

            // Reading the over-limit chapter directly must still report the cap.
            val error = kotlin.test.assertFailsWith<dev.reader.media.novel.NovelImportException.LimitExceeded> {
                novel.readChapter(last.id)
            }
            assertEquals("chapter-bytes", error.limit)
            assertEquals(1024L, error.maximum)
        }
    }

    @Test
    fun resumeParsesOnlyTheAnchoredChapter() {
        val limits = LocalNovelLimits(maxChapterBytes = 1024)
        InMemoryEpub.open(overLimitSecondChapterEpub(), "/books/resume.epub", limits).useImport { novel ->
            val epub = novel as dev.reader.media.epub.EpubNovel
            val first = novel.tableOfContents.first { it.level == 1 }
            val paragraphIndex = novel.readChapter(first.id).blocks
                .indexOfFirst { it is NovelBlock.Paragraph }
            val stored = novel.positionOf(first.id, paragraphIndex, codePointOffset = 3)
            assertEquals(1, epub.cachedChapterCount, "positionOf should cache exactly the anchored chapter")

            novel.resolvePosition(stored)
            assertEquals(
                1,
                epub.cachedChapterCount,
                "an exact resume must not parse any other chapter",
            )
        }
    }
}
