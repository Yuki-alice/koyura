package dev.reader.media.novel.epub

import dev.reader.media.novel.CodePoints
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
}
