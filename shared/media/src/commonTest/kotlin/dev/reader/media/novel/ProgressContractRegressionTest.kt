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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Regression tests for the docs/contract review.
 *
 * Each test pins a claim that the documentation makes, so the claim cannot silently become false.
 */
class ProgressContractRegressionTest {

    /** In-memory filesystem serving one file. */
    private class MemFs(private val bytes: ByteArray) : FileSystem() {
        override fun canonicalize(path: Path): Path = path
        override fun metadataOrNull(path: Path) =
            okio.FileMetadata(isRegularFile = true, isDirectory = false, size = bytes.size.toLong())
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

    private fun openBytes(bytes: ByteArray): NovelImport =
        openTxtNovel(MemFs(bytes), "book.txt".toPath())

    // ---- M1: content version must react to whitespace edits that change paragraph structure ----

    @Test
    fun contentVersionChangesWhenBlankLineSplitsAParagraph() {
        // One paragraph that wraps onto two lines.
        val before = "第一章 甲\n雨下了一整夜，林舟推门而入\n看见檐下的陌生人。\n"
        // A blank line between them splits it into two paragraphs.
        val after = "第一章 甲\n雨下了一整夜，林舟推门而入\n\n看见檐下的陌生人。\n"

        val firstVersion = open(before).let { it.contentVersion.also { _ -> it.close() } }
        val secondVersion = open(after).let { it.contentVersion.also { _ -> it.close() } }

        assertNotEquals(
            firstVersion,
            secondVersion,
            "a blank line that changes paragraph structure must change contentVersion",
        )
    }

    @Test
    fun contentVersionChangesOnSingleCharacterEdit() {
        val a = open("第一章 甲\n一二三。\n").let { it.contentVersion.also { _ -> it.close() } }
        val b = open("第一章 甲\n一二四。\n").let { it.contentVersion.also { _ -> it.close() } }
        assertNotEquals(a, b)
    }

    @Test
    fun contentVersionIsStableForIdenticalBytes() {
        val text = "第一章 甲\n第一段。\n第二段。\n"
        val a = open(text).let { it.contentVersion.also { _ -> it.close() } }
        val b = open(text).let { it.contentVersion.also { _ -> it.close() } }
        assertEquals(a, b)
    }

    @Test
    fun blockFingerprintStillIgnoresWhitespaceSoRewrapKeepsAnchor() {
        // The anchor fingerprint intentionally normalizes whitespace: re-wrapping must not lose it.
        assertEquals(
            NovelFingerprints.block("雨下了一整夜，林舟推门而入 看见檐下的陌生人。"),
            NovelFingerprints.block("雨下了一整夜，林舟推门而入\n看见檐下的陌生人。"),
        )
        // But a real edit does change it.
        assertNotEquals(
            NovelFingerprints.block("雨下了一整夜。"),
            NovelFingerprints.block("雨下了两整夜。"),
        )
    }

    // ---- M2: TXT readResource follows close semantics ----

    @Test
    fun txtReadResourceIsUnsupportedWhileOpen() {
        val novel = open("第一章 甲\n第一段。\n")
        try {
            assertFailsWith<UnsupportedOperationException> {
                novel.readResource("images/x.png")
            }
        } finally {
            novel.close()
        }
    }

    @Test
    fun txtReadResourceAfterCloseThrowsClosedException() {
        val novel = open("第一章 甲\n第一段。\n")
        novel.close()
        assertFailsWith<NovelClosedException> {
            novel.readResource("images/x.png")
        }
    }

    @Test
    fun allContentReadsThrowClosedAfterClose() {
        val novel = open("第一章 甲\n第一段。\n")
        val chapterId = novel.tableOfContents.single().id
        val stored = novel.positionOf(chapterId, 0, 0)
        novel.close()
        assertFailsWith<NovelClosedException> { novel.readChapter(chapterId) }
        assertFailsWith<NovelClosedException> { novel.readResource("x") }
        assertFailsWith<NovelClosedException> { novel.positionOf(chapterId, 0, 0) }
        assertFailsWith<NovelClosedException> { novel.resolvePosition(stored) }
    }

    // ---- M6: BOM-less UTF-16 must not silently decode as control-character garbage ----

    private fun utf16leWithoutBom(text: String): ByteArray {
        val out = ByteArray(text.length * 2)
        text.forEachIndexed { index, char ->
            out[index * 2] = (char.code and 0xFF).toByte()
            out[index * 2 + 1] = ((char.code shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun bomlessUtf16ChineseProseIsRejectedNotSilentlyMojibake() {
        // This is the reviewer's reproduction: it decodes as valid UTF-8 but yields control chars.
        val bytes = utf16leWithoutBom("这是一个测试。")
        val error = assertFailsWith<NovelImportException.Encoding> {
            openBytes(bytes)
        }
        assertEquals("UTF-8", error.encoding)
    }

    @Test
    fun bomlessUtf16NovelSizedChineseIsRejected() {
        val text = "第一章 夜雨\n雨下了一整夜，林舟推开木门，看见檐下站着一个陌生人。".repeat(50)
        assertFailsWith<NovelImportException.Encoding> { openBytes(utf16leWithoutBom(text)) }
    }

    @Test
    fun normalUtf8ChineseStillDecodes() {
        // Guard against over-rejection: ordinary UTF-8 prose, including emoji, must keep working.
        open("第一章 甲\n雨下了一整夜，林舟推门而入。😀\n").useImport { novel ->
            assertEquals(
                listOf("雨下了一整夜，林舟推门而入。😀"),
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    @Test
    fun textWithATabAndNewlineStillDecodes() {
        // Tabs and newlines are legal prose whitespace and must not trigger the control-character guard.
        open("第一章 甲\n甲\t乙\n丙。\n").useImport { novel ->
            // Tabs survive and newlines separate paragraphs; neither is treated as garbage.
            val paragraphs = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
            assertEquals(listOf("甲\t乙", "丙。"), paragraphs)
        }
    }
}
