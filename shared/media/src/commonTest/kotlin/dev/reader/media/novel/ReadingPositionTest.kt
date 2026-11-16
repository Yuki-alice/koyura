package dev.reader.media.novel

import dev.reader.media.novel.txt.openTxtNovel
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reading-position semantics, exercised against real TXT novels built in memory.
 *
 * Every assertion names the concrete block index and offset, so a regression in anchoring shows up as
 * a wrong position rather than a vague failure.
 */
class ReadingPositionTest {

    /** Minimal in-memory filesystem serving one UTF-8 novel. */
    private class MemFs(private val bytes: ByteArray) : FileSystem() {
        override fun canonicalize(path: Path): Path = path
        override fun metadataOrNull(path: Path) = okio.FileMetadata(
            isRegularFile = true,
            isDirectory = false,
            size = bytes.size.toLong(),
        )
        override fun list(dir: Path): List<Path> = emptyList()
        override fun listOrNull(dir: Path): List<Path>? = emptyList()
        override fun openReadOnly(file: Path): okio.FileHandle = throw UnsupportedOperationException()
        override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): okio.FileHandle =
            throw UnsupportedOperationException()
        override fun source(file: Path): Source = object : Source {
            private val buffer = Buffer().write(bytes)
            override fun read(sink: Buffer, byteCount: Long): Long = buffer.read(sink, byteCount)
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }
        override fun sink(file: Path, mustCreate: Boolean): Sink = throw UnsupportedOperationException()
        override fun appendingSink(file: Path, mustExist: Boolean): Sink = throw UnsupportedOperationException()
        override fun createDirectory(dir: Path, mustCreate: Boolean) = throw UnsupportedOperationException()
        override fun atomicMove(source: Path, target: Path) = throw UnsupportedOperationException()
        override fun delete(path: Path, mustExist: Boolean) = throw UnsupportedOperationException()
        override fun createSymlink(source: Path, target: Path) = throw UnsupportedOperationException()
    }

    private fun open(text: String): NovelImport =
        openTxtNovel(MemFs(text.encodeToByteArray()), "book.txt".toPath())

    private fun paragraphsOf(novel: NovelImport, chapterId: String): List<String> =
        novel.readChapter(chapterId).blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }

    // ---- Same content, repeated opens: identical ids, version, and positions ----

    @Test
    fun identicalContentProducesIdenticalChapterIdsVersionAndPositions() {
        val text = "第一章 甲\n第一段。\n第二段。\n\n第二章 乙\n第三段。\n"
        val first = open(text)
        val second = open(text)
        try {
            assertEquals(
                first.tableOfContents.map { it.id },
                second.tableOfContents.map { it.id },
                "chapter ids must be stable across opens",
            )
            assertEquals(first.contentVersion, second.contentVersion, "content version must be stable")

            val chapterId = first.tableOfContents[1].id
            val firstPosition = first.positionOf(chapterId, 0, 2)
            val secondPosition = second.positionOf(chapterId, 0, 2)
            assertEquals(firstPosition, secondPosition)
            assertEquals(chapterId, firstPosition.chapterId)
            assertEquals(0, firstPosition.blockIndex)
            assertEquals(2, firstPosition.codePointOffset)

            // And re-resolving the stored position in a fresh instance is exact.
            val resolution = second.resolvePosition(firstPosition)
            assertEquals(PositionResolution.ResolutionKind.EXACT, resolution.resolution)
            assertEquals(firstPosition, resolution.position)
        } finally {
            first.close()
            second.close()
        }
    }

    // ---- Content inserted before the anchor: same block, new index ----

    @Test
    fun insertedParagraphBeforeAnchorReanchorsToNewIndex() {
        val before = "第一章 甲\n第一段。\n第二段。\n第三段。\n"
        val after = "第一章 甲\n新增零段。\n第一段。\n第二段。\n第三段。\n"

        open(before).useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            // TXT section bodies exclude the heading, so "第二段。" is block 1.
            val stored = novel.positionOf(chapterId, blockIndex = 1, codePointOffset = 3)
            assertEquals("第二段。", paragraphsOf(novel, chapterId)[1])

            open(after).useImport { updated ->
                val resolution = updated.resolvePosition(stored)
                assertEquals(
                    PositionResolution.ResolutionKind.REANCHORED_BLOCK,
                    resolution.resolution,
                    "an inserted paragraph should move the anchor, not lose it",
                )
                // "第二段。" is now block 2 (new + first + second).
                assertEquals(2, resolution.position.blockIndex)
                assertEquals(3, resolution.position.codePointOffset)
                val newParagraphs = paragraphsOf(updated, chapterId)
                assertEquals("第二段。", newParagraphs[2])
            }
        }
    }

    // ---- Deleting the anchored paragraph: fall back to the previous survivor ----

    @Test
    fun deletedAnchoredParagraphFallsBackToPreviousParagraphEnd() {
        val before = "第一章 甲\n第一段。\n要删除的段落。\n第三段。\n"
        val after = "第一章 甲\n第一段。\n第三段。\n"

        open(before).useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            val stored = novel.positionOf(chapterId, blockIndex = 1, codePointOffset = 20)
            assertEquals("要删除的段落。", paragraphsOf(novel, chapterId)[1])

            open(after).useImport { updated ->
                val resolution = updated.resolvePosition(stored)
                assertEquals(
                    PositionResolution.ResolutionKind.PREVIOUS_PARAGRAPH,
                    resolution.resolution,
                )
                // "第一段。" is block 0, and the offset is clamped to its end (4 code points).
                assertEquals(0, resolution.position.blockIndex)
                assertEquals(CodePoints.length("第一段。"), resolution.position.codePointOffset)
            }
        }
    }

    @Test
    fun deletedFirstParagraphFallsBackToNextParagraph() {
        val before = "第一章 甲\n第一段。\n第二段。\n"
        val after = "第一章 甲\n第二段。\n"

        open(before).useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            val stored = novel.positionOf(chapterId, blockIndex = 0, codePointOffset = 2)
            open(after).useImport { updated ->
                val resolution = updated.resolvePosition(stored)
                assertEquals(PositionResolution.ResolutionKind.NEXT_PARAGRAPH, resolution.resolution)
                assertEquals(0, resolution.position.blockIndex)
                assertEquals(2, resolution.position.codePointOffset)
            }
        }
    }

    // ---- Duplicate paragraph text: the anchor must pick the right instance ----

    @Test
    fun duplicateParagraphsResolveToTheCorrectInstance() {
        val text = "第一章 甲\n重复段落。\n中间段。\n重复段落。\n末尾段。\n"
        open(text).useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            val paragraphs = paragraphsOf(novel, chapterId)
            assertEquals(listOf("重复段落。", "中间段。", "重复段落。", "末尾段。"), paragraphs)

            // Anchor the second occurrence (block 2) and confirm it survives a no-op reopen at the
            // same index rather than jumping to the first occurrence (block 0).
            val stored = novel.positionOf(chapterId, blockIndex = 2, codePointOffset = 1)
            assertEquals(
                NovelFingerprints.block("重复段落。"),
                stored.blockFingerprint,
                "duplicates share a fingerprint, so the index must disambiguate",
            )

            open(text).useImport { reopened ->
                val resolution = reopened.resolvePosition(stored)
                assertEquals(PositionResolution.ResolutionKind.EXACT, resolution.resolution)
                assertEquals(2, resolution.position.blockIndex, "must not snap to the first duplicate")
            }
        }
    }

    @Test
    fun duplicateParagraphSurvivesIndicesShiftingTogether() {
        // Inserting one paragraph before two identical ones must keep the *second* duplicate anchored.
        val before = "第一章 甲\n重复段落。\n中间段。\n重复段落。\n末尾段。\n"
        val after = "第一章 甲\n新增段。\n重复段落。\n中间段。\n重复段落。\n末尾段。\n"
        open(before).useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            val stored = novel.positionOf(chapterId, blockIndex = 2, codePointOffset = 0)
            open(after).useImport { updated ->
                val resolution = updated.resolvePosition(stored)
                // Both duplicates have the same fingerprint; the closest-to-original index wins.
                assertEquals(3, resolution.position.blockIndex)
                assertEquals(PositionResolution.ResolutionKind.REANCHORED_BLOCK, resolution.resolution)
            }
        }
    }

    // ---- Emoji and supplementary-plane offsets ----

    @Test
    fun emojiOffsetsAreCountedInCodePoints() {
        val text = "第一章 甲\n他笑😀了，又说𠮷野家。\n下一段。\n"
        open(text).useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            val paragraph = paragraphsOf(novel, chapterId).first()
            assertEquals("他笑😀了，又说𠮷野家。", paragraph)

            // Offset 3 is right after the emoji: "他笑😀".
            val stored = novel.positionOf(chapterId, blockIndex = 0, codePointOffset = 3)
            assertEquals(3, stored.codePointOffset)
            // Converting to a UTF-16 index gives 4 because 😀 is two code units.
            assertEquals(4, CodePoints.toStringIndex(paragraph, stored.codePointOffset))

            open(text).useImport { reopened ->
                val resolution = reopened.resolvePosition(stored)
                assertEquals(PositionResolution.ResolutionKind.EXACT, resolution.resolution)
                assertEquals(3, resolution.position.codePointOffset)
            }
        }
    }

    @Test
    fun oversizedEmojiOffsetConvergesToParagraphEnd() {
        val before = "第一章 甲\n他笑😀了，很长的一段。\n"
        val after = "第一章 甲\n他笑😀了。\n"
        val shortened = "他笑😀了。"
        open(before).useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            // The offset itself is clamped at position creation too.
            val stored = novel.positionOf(chapterId, blockIndex = 0, codePointOffset = 50)
            assertEquals(CodePoints.length("他笑😀了，很长的一段。"), stored.codePointOffset)

            open(after).useImport { updated ->
                val resolution = updated.resolvePosition(stored)
                assertEquals(0, resolution.position.blockIndex)
                // Text shortened: the anchor paragraph is gone, so recovery is the next-paragraph rule
                // (nothing precedes it) rather than an exact hit.
                assertEquals(PositionResolution.ResolutionKind.NEXT_PARAGRAPH, resolution.resolution)
                // The stored offset is clamped to the *end* of the shortened paragraph, exactly.
                // "他笑😀了。" is 5 code points: 他 笑 😀 了 。 — the emoji counts once.
                assertEquals(5, CodePoints.length(shortened))
                assertEquals(5, resolution.position.codePointOffset)
            }
        }
    }

    // ---- Chapters added or removed ----

    @Test
    fun deletedChapterFallsBackToNeighbourChapter() {
        val before = "第一章 甲\n甲段。\n\n第二章 乙\n乙段。\n\n第三章 丙\n丙段。\n"
        val after = "第一章 甲\n甲段。\n\n第三章 丙\n丙段。\n"
        open(before).useImport { novel ->
            val toc = novel.tableOfContents
            val chapterTwo = toc.first { it.title.contains("第二章") }
            val stored = novel.positionOf(chapterTwo.id, blockIndex = 0, codePointOffset = 1)

            open(after).useImport { updated ->
                val resolution = updated.resolvePosition(stored)
                assertEquals(
                    PositionResolution.ResolutionKind.NEIGHBOR_CHAPTER,
                    resolution.resolution,
                )
                // The removed chapter's ordinal position (index 1) now holds 第三章 丙.
                val newToc = updated.tableOfContents
                assertEquals(newToc[1].id, resolution.position.chapterId)
                assertEquals(0, resolution.position.blockIndex)
                assertEquals(0, resolution.position.codePointOffset)
            }
        }
    }

    @Test
    fun addedChapterBeforeAnchorKeepsAnchorById() {
        val before = "第一章 甲\n甲段。\n\n第二章 乙\n乙段。\n"
        val after = "序章 新\n新段。\n\n第一章 甲\n甲段。\n\n第二章 乙\n乙段。\n"
        open(before).useImport { novel ->
            val chapterTwo = novel.tableOfContents.first { it.title.contains("第二章") }
            val stored = novel.positionOf(chapterTwo.id, blockIndex = 0, codePointOffset = 2)
            open(after).useImport { updated ->
                val resolution = updated.resolvePosition(stored)
                // Chapter ids are content-derived, so the same chapter keeps its id and the anchor is
                // exact even though its ordinal position moved.
                assertEquals(PositionResolution.ResolutionKind.EXACT, resolution.resolution)
                assertEquals(chapterTwo.id, resolution.position.chapterId)
                assertEquals(2, resolution.position.codePointOffset)
            }
        }
    }

    // ---- Non-paragraph anchors ----

    @Test
    fun outOfRangeBlockIndexMovesToNearestParagraph() {
        open("第一章 甲\n第一段。\n").useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            // TXT sections contain only paragraphs, so index 99 is out of range and must clamp to a
            // real paragraph (block 0) rather than producing an unrenderable position.
            val position = novel.positionOf(chapterId, blockIndex = 99, codePointOffset = 0)
            assertEquals(0, position.blockIndex)
            assertEquals(NovelFingerprints.block("第一段。"), position.blockFingerprint)
        }
    }

    @Test
    fun positionOfClampsOffsetToParagraphLength() {
        open("第一章 甲\n短句。\n").useImport { novel ->
            val chapterId = novel.tableOfContents.single().id
            val position = novel.positionOf(chapterId, blockIndex = 0, codePointOffset = 999)
            assertEquals(3, position.codePointOffset) // "短句。" is 3 code points
        }
    }

    // ---- Serialization ----

    @Test
    fun readingPositionSurvivesJsonRoundTrip() {
        val position = ReadingPosition(
            chapterId = "txt-0123456789abcdef",
            contentVersion = "deadbeefcafe0000",
            blockIndex = 7,
            blockFingerprint = "00112233445566aa",
            codePointOffset = 42,
        )
        val json = kotlinx.serialization.json.Json.encodeToString(ReadingPosition.serializer(), position)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(ReadingPosition.serializer(), json)
        assertEquals(position, decoded)
        assertTrue(json.contains("\"codePointOffset\":42"))
    }
}
