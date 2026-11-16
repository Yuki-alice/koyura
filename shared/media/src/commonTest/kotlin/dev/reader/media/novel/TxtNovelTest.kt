package dev.reader.media.novel

import dev.reader.media.fixtureRoot
import dev.reader.media.novel.txt.openTxtNovel
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shared helpers for the TXT importer tests; every test reads the real checked-in fixtures. */
object TxtFixtures {
    val SAMPLE_PARAGRAPHS: List<String> = listOf(
        "长夜行 · 补充平面字符",
        "𠮷野家的猫在屋顶打盹。😀",
        "雨声落在瓦上，像一首慢歌。",
    )

    fun path(name: String): Path = fixtureRoot().toPath() / "txt" / name

    fun open(name: String): NovelImport = openTxtNovel(FileSystem.SYSTEM, path(name))
}

class TxtEncodingTest {

    @Test
    fun readsPlainUtf8() {
        TxtFixtures.open("sample-utf8.txt").useImport { novel ->
            assertEquals(TextEncoding.UTF_8, novel.encoding)
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            assertEquals(listOf("长夜行 · 补充平面字符", "𠮷野家的猫在屋顶打盹。😀", "雨声落在瓦上，像一首慢歌。"), paragraphs(chapter))
        }
    }

    @Test
    fun readsUtf8WithBomAndDropsTheMark() {
        TxtFixtures.open("sample-utf8-bom.txt").useImport { novel ->
            assertEquals(TextEncoding.UTF_8, novel.encoding)
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            // The BOM must not leak into the first paragraph as U+FEFF.
            assertEquals("长夜行 · 补充平面字符", paragraphs(chapter).first())
            assertTrue(paragraphs(chapter).none { it.startsWith('\uFEFF') })
        }
    }

    @Test
    fun readsUtf16LittleEndianWithBom() {
        TxtFixtures.open("sample-utf16le-bom.txt").useImport { novel ->
            assertEquals(TextEncoding.UTF_16_LE, novel.encoding)
            assertEquals(TxtFixtures.SAMPLE_PARAGRAPHS, paragraphs(novel.readChapter(novel.tableOfContents.single().id)))
        }
    }

    @Test
    fun readsUtf16BigEndianWithBom() {
        TxtFixtures.open("sample-utf16be-bom.txt").useImport { novel ->
            assertEquals(TextEncoding.UTF_16_BE, novel.encoding)
            assertEquals(TxtFixtures.SAMPLE_PARAGRAPHS, paragraphs(novel.readChapter(novel.tableOfContents.single().id)))
        }
    }

    @Test
    fun readsCrlfLineEndingsWithoutCarriageReturns() {
        TxtFixtures.open("sample-crlf.txt").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            assertEquals(TxtFixtures.SAMPLE_PARAGRAPHS, paragraphs(chapter))
            assertTrue(paragraphs(chapter).none { it.contains('\r') })
        }
    }

    @Test
    fun allFourEncodingsProduceTheSameText() {
        val names = listOf(
            "sample-utf8.txt",
            "sample-utf8-bom.txt",
            "sample-utf16le-bom.txt",
            "sample-utf16be-bom.txt",
        )
        val texts = names.map { name ->
            TxtFixtures.open(name).useImport { novel ->
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.map { (it as NovelBlock.Paragraph).text }
            }
        }
        assertEquals(1, texts.distinct().size, "encodings disagreed: $texts")
    }
}

/** Supplementary-plane coverage: U+20BB7 and an emoji, both encoded as UTF-16 surrogate pairs. */
class TxtSupplementaryCharacterTest {

    @Test
    fun preservesSupplementaryPlaneCharactersInUtf8() {
        TxtFixtures.open("sample-utf8.txt").useImport { novel ->
            val text = paragraphs(novel.readChapter(novel.tableOfContents.single().id))
                .single { it.contains("𠮷野") }
            assertEquals("𠮷野家的猫在屋顶打盹。😀", text)
            // U+20BB7 and U+1F600 are each one code point but two UTF-16 chars.
            assertTrue(text.contains('\uD842'))
            assertTrue(text.contains('\uDFB7'))
            assertTrue(text.contains('\uD83D'))
            assertTrue(text.contains('\uDE00'))
            assertEquals(2, text.count { it.isHighSurrogate() })
        }
    }

    @Test
    fun preservesSupplementaryPlaneCharactersInUtf16() {
        listOf("sample-utf16le-bom.txt", "sample-utf16be-bom.txt").forEach { name ->
            TxtFixtures.open(name).useImport { novel ->
                val text = paragraphs(novel.readChapter(novel.tableOfContents.single().id))
                    .single { it.contains("𠮷野") }
                assertEquals("𠮷野家的猫在屋顶打盹。😀", text)
                assertTrue(text.contains('\uD842'), "$name lost U+20BB7")
            }
        }
    }
}

class TxtChapterDetectionTest {

    @Test
    fun splitsMultiChapterFileInOrderWithStableIds() {
        TxtFixtures.open("chapters-utf8.txt").useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(
                listOf(
                    "长夜行",
                    "第一卷 启程",
                    "第一章 夜雨",
                    "第二章 旧信",
                    "第二卷 远行",
                    "第三章 归途",
                    "第一节 灯火",
                ),
                toc.map { it.title },
            )
            // The title/author preamble is front matter (level 1); 卷 headings are level 0.
            assertEquals(listOf(1, 0, 1, 1, 0, 1, 1), toc.map { it.level })

            val volume = toc.single { it.title == "第一卷 启程" }
            val chapter = toc.single { it.title == "第一章 夜雨" }
            assertNull(volume.parentId)
            assertEquals(volume.id, chapter.parentId)
            assertEquals(volume.id, toc.single { it.title == "第二章 旧信" }.parentId)
            val secondVolume = toc.single { it.title == "第二卷 远行" }
            assertEquals(secondVolume.id, toc.single { it.title == "第三章 归途" }.parentId)
            assertNull(toc.first().parentId)

            assertEquals(toc.size, toc.map { it.id }.distinct().size, "chapter ids must be unique")
            assertTrue(toc.all { it.id.matches(Regex("txt-[0-9a-f]{16}")) }, "unexpected id format: $toc")
        }
    }

    @Test
    fun frontMatterKeepsTitleAndAuthorButDoesNotRepeatHeadings() {
        TxtFixtures.open("chapters-utf8.txt").useImport { novel ->
            val front = novel.readChapter(novel.tableOfContents.first().id)
            assertEquals("长夜行", front.title)
            assertEquals(listOf("长夜行", "作者：佚名"), paragraphs(front))
        }
    }

    @Test
    fun chapterHeadingLineDoesNotRepeatInBody() {
        TxtFixtures.open("chapters-utf8.txt").useImport { novel ->
            val night = novel.readChapter(novel.tableOfContents.single { it.title == "第一章 夜雨" }.id)
            assertEquals("第一章 夜雨", night.title)
            assertEquals(
                listOf("雨下了一整夜。", "林舟推开木门，看见檐下站着一个陌生人。"),
                paragraphs(night),
            )
            assertTrue(paragraphs(night).none { it.contains("第一章") })
        }
    }

    @Test
    fun recognizesNamedAndInlineHeadings() {
        TxtFixtures.open("headings-utf8.txt").useImport { novel ->
            assertEquals(
                listOf(
                    "序章 起风",
                    "楔子",
                    "前言",
                    "第一章 初遇",
                    "第一节 试探",
                    "第二回 重逢",
                    "第一篇 旧梦",
                    "第4章 数字编号",
                    "后记",
                    "尾声",
                    "番外一 春日",
                ),
                novel.tableOfContents.map { it.title },
            )
            // 篇 is a structural heading; the rest are readable.
            assertEquals(
                listOf(1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1),
                novel.tableOfContents.map { it.level },
            )
        }
    }

    @Test
    fun namedHeadingsKeepTheirBody() {
        TxtFixtures.open("headings-utf8.txt").useImport { novel ->
            assertEquals(
                listOf("写在前面的话。"),
                paragraphs(novel.readChapter(novel.tableOfContents.single { it.title == "前言" }.id)),
            )
            assertEquals(
                listOf("他们养了一只猫。"),
                paragraphs(novel.readChapter(novel.tableOfContents.single { it.title == "番外一 春日" }.id)),
            )
        }
    }

    @Test
    fun proseStartingWithChapterWordsIsNotASection() {
        // The detector must not split a sentence such as "第一天他走了。" into a chapter.
        val detector = ChapterHeadingDetector
        assertNull(detector.detect("第一天他走了。"))
        assertNull(detector.detect("第二个问题还没有答案。"))
        assertNull(detector.detect("第一回合他就输了。"))
        assertNull(detector.detect("第三部分内容如下："))
        assertEquals(1, detector.detect("第一章 夜雨")?.level)
        assertEquals(0, detector.detect("第一卷")?.level)
        assertEquals(1, detector.detect("第1章")?.level)
        assertEquals(1, detector.detect("第1章 夜雨")?.level)
        assertEquals(1, detector.detect("楔子")?.level)
        assertEquals(1, detector.detect("番外")?.level)
        assertEquals(0, detector.detect("第二篇 旧事")?.level)
        assertEquals(1, detector.detect("第一节 试探")?.level)
        assertEquals(1, detector.detect("第二回 重逢")?.level)
        assertNull(detector.detect("第一章 夜雨。"))
        // Additional forms that must be recognized (finding: silent single-chapter fallback).
        assertEquals(0, detector.detect("卷一 少年游")?.level)
        assertEquals(0, detector.detect("卷1")?.level)
        assertEquals(1, detector.detect("番外篇")?.level)
        assertEquals(1, detector.detect("第１章 夜雨")?.level)
        assertEquals(1, detector.detect("第1節 灯火")?.level)
        assertEquals(1, detector.detect("序")?.level)
        assertEquals(1, detector.detect("引子")?.level)
        assertEquals(1, detector.detect("终章")?.level)
        assertEquals(1, detector.detect("跋")?.level)
        // Full-width digits are preserved verbatim in the matched title.
        assertEquals("第１章 夜雨", detector.detect("第１章 夜雨")?.title)
        // No false positives on prose that merely starts with these words.
        assertNull(detector.detect("序幕拉开了。"))
        assertNull(detector.detect("卷入了漩涡。"))
    }
}

class TxtMissingHeadingsTest {

    @Test
    fun readsWholeBodyWhenNoChapterHeadingExists() {
        TxtFixtures.open("no-headings-utf8.txt").useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(1, toc.size)
            assertEquals(1, toc.single().level)
            assertNull(toc.single().parentId)

            val body = paragraphs(novel.readChapter(toc.single().id))
            assertEquals(
                listOf(
                    "这是没有章节标题的完整正文。",
                    "第一段内容，讲述清晨的渡口。",
                    "第二段内容，讲到午后的雨。",
                    "第三段内容，包含补充平面字符：𠮷野和 😀。",
                    "结尾直接结束，没有任何章标题行。",
                ),
                body,
            )
        }
    }

    @Test
    fun keepsEveryNonBlankLineOfAnUntitledFile() {
        TxtFixtures.open("no-headings-utf8.txt").useImport { novel ->
            val text = paragraphs(novel.readChapter(novel.tableOfContents.single().id)).joinToString("\n")
            assertTrue(text.contains("清晨的渡口"))
            assertTrue(text.contains("午后的雨"))
            assertTrue(text.contains("结尾直接结束"))
        }
    }
}

class TxtRichContentTest {

    @Test
    fun separatesParagraphsAndImageReferences() {
        TxtFixtures.open("image-ref-utf8.txt").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single { it.title == "第一章 有图" }.id)
            assertEquals(
                listOf("正文之前。"),
                chapter.blocks.filterIsInstance<NovelBlock.Paragraph>().take(1).map { it.text },
            )
            val separator = chapter.blocks.filterIsInstance<NovelBlock.Separator>()
            assertEquals(1, separator.size)
            assertEquals(SeparatorStyle.ASTERISKS, separator.single().style)
            val image = chapter.blocks.filterIsInstance<NovelBlock.Image>().single()
            assertEquals("images/ferry.png", image.reference.source)
            assertEquals("渡口", image.reference.altText)
            assertEquals("正文之后。", chapter.blocks.filterIsInstance<NovelBlock.Paragraph>().last().text)
            assertEquals(1, chapter.blocks.count { it is NovelBlock.Image })
        }
    }

    @Test
    fun txtWithoutImagesStillUsesTheUnifiedBlockModel() {
        TxtFixtures.open("chapters-utf8.txt").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single { it.title == "第二章 旧信" }.id)
            assertTrue(chapter.blocks.isNotEmpty())
            assertTrue(chapter.blocks.all { it is NovelBlock.Paragraph })
        }
    }
}

class TxtImportFailureTest {

    @Test
    fun emptyFileIsReportedAsEmptyNotEncoding() {
        val error = assertFailsWith<NovelImportException.Empty> { TxtFixtures.open("empty.txt") }
        assertEquals(0L, error.byteSize)
        assertTrue(error.message!!.contains("empty.txt"))
        assertTrue(error.path.endsWith("empty.txt"))
    }

    @Test
    fun invalidUtf8BytesReportByteOffsetAndLine() {
        val error = assertFailsWith<NovelImportException.Encoding> { TxtFixtures.open("invalid-utf8.txt") }
        assertEquals("UTF-8", error.encoding)
        // "长夜行\n" is 10 bytes, "你好" is 6, so the stray 0xC3 0x28 begins at byte 16.
        assertEquals(16L, error.byteOffset)
        assertEquals(2, error.line)
        assertTrue(error.message!!.contains("invalid-utf8.txt"))
        assertTrue(error.message!!.contains("byte 16"))
    }

    @Test
    fun zipContainerIsUnsupportedFormatNotEncodingError() {
        val error = assertFailsWith<NovelImportException.UnsupportedFormat> {
            TxtFixtures.open("unsupported-format.txt")
        }
        assertEquals("zip-container", error.format)
        assertEquals("504b0304", error.detectedSignature)
        assertTrue(error.message!!.contains("unsupported-format.txt"))
    }    @Test
    fun missingFileIsIoFailureOnOpen() {
        val path = TxtFixtures.path("does-not-exist.txt")
        val error = assertFailsWith<NovelImportException.Io> { openTxtNovel(FileSystem.SYSTEM, path) }
        assertEquals("open", error.phase)
        assertTrue(error.path.endsWith("does-not-exist.txt"))
        assertTrue(error.message!!.contains("does-not-exist.txt"))
    }

    @Test
    fun readFailureIsIoFailureOnReadAndBehindItTheCause() {
        val failing = ReadFailingFileSystem()
        val error = assertFailsWith<NovelImportException.Io> {
            openTxtNovel(failing, TxtFixtures.path("sample-utf8.txt"))
        }
        assertEquals("read", error.phase)
        assertIs<okio.IOException>(error.cause)
        assertEquals("synthetic read failure", error.cause?.message)
    }
}

class TxtLifecycleTest {

    @Test
    fun chapterIdsAreStableAcrossRepeatedOpens() {
        val first = TxtFixtures.open("chapters-utf8.txt")
        val second = TxtFixtures.open("chapters-utf8.txt")
        try {
            assertEquals(
                first.tableOfContents.map { it.id to it.title },
                second.tableOfContents.map { it.id to it.title },
            )
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun repeatedChapterReadsReturnEqualContent() {
        // Compare across two independent imports, so this verifies parsing determinism rather than
        // the instance cache returning the same object twice.
        val ids = TxtFixtures.open("chapters-utf8.txt").useImport { novel ->
            val id = novel.tableOfContents.first().id
            val first = novel.readChapter(id)
            val second = novel.readChapter(id)
            assertEquals(first, second, "cache must return equal content")
            novel.tableOfContents.map { it.id }
        }
        TxtFixtures.open("chapters-utf8.txt").useImport { otherNovel ->
            val firstFromA = TxtFixtures.open("chapters-utf8.txt").useImport { a ->
                a.readChapter(ids.first())
            }
            val firstFromB = otherNovel.readChapter(ids.first())
            assertEquals(firstFromA, firstFromB, "two imports of the same bytes must parse identically")
        }
    }

    @Test
    fun readAfterCloseThrowsClosedException() {
        val novel = TxtFixtures.open("sample-utf8.txt")
        val id = novel.tableOfContents.single().id
        // Reading before close works, so the failure below is not a fixture problem.
        assertEquals(3, paragraphs(novel.readChapter(id)).size)
        novel.close()
        val error = assertFailsWith<NovelClosedException> { novel.readChapter(id) }
        assertTrue(error.message!!.contains("sample-utf8.txt"))
    }

    @Test
    fun closeIsIdempotentAndLeavesMetadataReadable() {
        val novel = TxtFixtures.open("sample-utf8.txt")
        novel.close()
        novel.close()
        assertEquals("sample-utf8", novel.metadata.title)
        assertEquals(TextEncoding.UTF_8, novel.encoding)
        assertEquals(1, novel.tableOfContents.size)
    }

    @Test
    fun unknownChapterIdIsRejected() {
        TxtFixtures.open("sample-utf8.txt").useImport { novel ->
            val error = assertFailsWith<IllegalArgumentException> { novel.readChapter("txt-0000000000000000") }
            assertTrue(error.message!!.contains("not a chapter"))
        }
    }

    @Test
    fun metadataComesFromFileNameAndAuthorLine() {
        TxtFixtures.open("chapters-utf8.txt").useImport { novel ->
            assertEquals("chapters-utf8", novel.metadata.title)
            assertEquals("佚名", novel.metadata.author)
        }
        TxtFixtures.open("no-headings-utf8.txt").useImport { novel ->
            assertEquals("no-headings-utf8", novel.metadata.title)
            assertNull(novel.metadata.author)
        }
    }
}

/** Paragraph texts of [chapter], in order. */
internal fun paragraphs(chapter: NovelChapter): List<String> =
    chapter.blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }

/**
 * Wraps the system filesystem and fails every `source` read.
 *
 * Only the open succeeds so the test can prove the importer closes the source and reports a read
 * failure rather than a decode failure.
 */
private class ReadFailingFileSystem : FileSystem() {
    private val delegate = FileSystem.SYSTEM

    override fun canonicalize(path: Path): Path = delegate.canonicalize(path)
    override fun metadataOrNull(path: Path) = delegate.metadataOrNull(path)
    override fun list(dir: Path): List<Path> = delegate.list(dir)
    override fun listOrNull(dir: Path): List<Path>? = delegate.listOrNull(dir)
    override fun openReadOnly(file: Path) = delegate.openReadOnly(file)
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean) =
        delegate.openReadWrite(file, mustCreate, mustExist)

    override fun source(file: Path): Source = object : Source {
        override fun read(sink: okio.Buffer, byteCount: Long): Long =
            throw okio.IOException("synthetic read failure")

        override fun timeout(): okio.Timeout = okio.Timeout.NONE

        override fun close() = Unit
    }

    override fun sink(file: Path, mustCreate: Boolean) = delegate.sink(file, mustCreate)
    override fun appendingSink(file: Path, mustExist: Boolean) = delegate.appendingSink(file, mustExist)
    override fun createDirectory(dir: Path, mustCreate: Boolean) = delegate.createDirectory(dir, mustCreate)
    override fun atomicMove(source: Path, target: Path) = delegate.atomicMove(source, target)
    override fun delete(path: Path, mustExist: Boolean) = delegate.delete(path, mustExist)
    override fun createSymlink(source: Path, target: Path) = delegate.createSymlink(source, target)
    override fun toString(): String = "ReadFailingFileSystem"
}
