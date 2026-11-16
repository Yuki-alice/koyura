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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavior that needs exact bytes but has no committed fixture.
 *
 * Uses an in-memory filesystem so the importer still goes through the real Okio call path while the
 * input stays readable in the test source.
 */
class TxtInMemoryContentTest {

    private fun open(name: String, text: String): NovelImport =
        open(name, text.encodeToByteArray())

    private fun open(name: String, bytes: ByteArray): NovelImport {
        val fs = SingleFileFileSystem(bytes)
        return openTxtNovel(fs, name.toPath())
    }

    @Test
    fun whitespaceOnlyFileIsEmptyNotEncodingError() {
        val error = assertFailsWith<NovelImportException.Empty> {
            open("blank.txt", "\n\n   \n\u3000\u3000\n")
        }
        assertTrue(error.byteSize > 0)
    }

    @Test
    fun mixedLineEndingsAreNormalized() {
        open("mixed.txt", "甲\r\n乙\n丙\r\n").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            assertEquals(listOf("甲", "乙", "丙"), chapter.blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text })
        }
    }

    @Test
    fun imageWithoutAltTextHasNullAlt() {
        open("img.txt", "第一章 图\n![alt](a.png)\n![](b.png)\n").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            val images = chapter.blocks.filterIsInstance<NovelBlock.Image>().map { it.reference }
            assertEquals(listOf(ImageReference("a.png", "alt"), ImageReference("b.png", null)), images)
        }
    }

    @Test
    fun separatorStylesAreClassified() {
        open("sep.txt", "第一章 分隔\n甲\n---\n乙\n***\n丙\n···\n").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            assertEquals(
                listOf(SeparatorStyle.DASHES, SeparatorStyle.ASTERISKS, SeparatorStyle.DOTS),
                chapter.blocks.filterIsInstance<NovelBlock.Separator>().map { it.style },
            )
        }
    }

    @Test
    fun indentedLayoutJoinsHardWrappedLines() {
        // Indented paragraphs with continuation lines that have no indentation.
        val text = "第一章 换行\n" +
            "\u3000\u3000第一段的第一行，\n" +
            "接着是同一段的第二行。\n" +
            "\u3000\u3000第二段。\n"
        open("wrap.txt", text).useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            assertEquals(
                listOf("第一段的第一行，接着是同一段的第二行。", "第二段。"),
                chapter.blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    @Test
    fun lineLayoutKeepsOneParagraphPerLine() {
        val text = "第一章 行\n第一行\n第二行\n第三行\n"
        open("lines.txt", text).useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            assertEquals(
                listOf("第一行", "第二行", "第三行"),
                chapter.blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    @Test
    fun chapterBeforeAnyVolumeHasNoParent() {
        open("order.txt", "第一章 甲\n正文\n第二卷 后卷\n第二章 乙\n正文\n").useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(listOf("第一章 甲", "第二卷 后卷", "第二章 乙"), toc.map { it.title })
            assertNull(toc[0].parentId)
            assertNull(toc[1].parentId)
            assertEquals(toc[1].id, toc[2].parentId)
        }
    }

    @Test
    fun preambleOnlyBeforeFirstHeadingBecomesFrontMatterSection() {
        open("front.txt", "书名\n作者：某人\n\n第一章 起\n正文。\n").useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(listOf("书名", "第一章 起"), toc.map { it.title })
            val front = novel.readChapter(toc.first().id)
            assertEquals(listOf("书名", "作者：某人"), front.blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text })
            assertEquals("某人", novel.metadata.author)
        }
    }

    // ---- Finding 1: two-line paragraphs and all-indented files must not be split ----

    @Test
    fun twoLineParagraphsWithExactlyHalfIndentedAreJoined() {
        // 20 paragraphs, each "indented first line + unindented continuation": exactly 50% indented.
        val body = (1..20).joinToString(separator = "") { index ->
            "\u3000\u3000第${index}段的第一行，\n" +
                "接着是同一段的第二行。\n"
        }
        open("two-line.txt", "第一章 甲\n$body").useImport { novel ->
            val paragraphs = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Paragraph>()
            assertEquals(20, paragraphs.size, "each two-line paragraph must stay one block")
            assertEquals("第1段的第一行，接着是同一段的第二行。", paragraphs.first().text)
            assertEquals("第20段的第一行，接着是同一段的第二行。", paragraphs.last().text)
        }
    }

    @Test
    fun singleTwoLineParagraphAtFiftyPercentIsNotSplit() {
        open("one-two-line.txt", "第一章 甲\n\u3000\u3000第一行，\n第二行。\n").useImport { novel ->
            assertEquals(
                listOf("第一行，第二行。"),
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    @Test
    fun fullyIndentedFileKeepsOneParagraphPerIndentedLine() {
        // Every line indented: an indented line starts a new paragraph, no merging.
        val body = (1..4).joinToString(separator = "") { "\u3000\u3000第${it}段。\n" }
        open("all-indented.txt", "第一章 乙\n$body").useImport { novel ->
            assertEquals(
                listOf("第1段。", "第2段。", "第3段。", "第4段。"),
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    @Test
    fun aSingleLeadingSpaceDoesNotFlipTheLayout() {
        // One stray leading space must not turn a line-layout chapter into indented layout,
        // which would merge all paragraphs into one.
        open("stray-space.txt", "第一章 甲\n第一行\n 第二行\n第三行\n").useImport { novel ->
            assertEquals(
                listOf("第一行", "第二行", "第三行"),
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    // ---- Round 2 / M1: dialogue-heavy chapters and zero/one-indent chapters ----

    @Test
    fun indentedNarrationDoesNotMergeUnindentedDialogueLines() {
        // One indented narration line plus 40 unindented dialogue lines: every line is a paragraph.
        val dialogue = (1..40).joinToString(separator = "") { index ->
            if (index % 2 == 1) "“你来了$index。”\n" else "“我来了$index。”\n"
        }
        open("dialogue.txt", "第一章 对话\n\u3000\u3000他推开门。\n$dialogue").useImport { novel ->
            val paragraphs = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Paragraph>()
            assertEquals(41, paragraphs.size, "narration plus 40 dialogue lines must stay separate")
            assertEquals("他推开门。", paragraphs.first().text)
            assertEquals("“你来了1。”", paragraphs[1].text)
            assertEquals("“我来了40。”", paragraphs.last().text)
        }
    }

    @Test
    fun longDialogueRunKeepsEachSpeakerOnItsOwnLine() {
        val body = "\u3000\u3000他推开门。\n“你来了。”\n“我来了。”\n“坐吧。”\n"
        open("dialogue-short.txt", "第一章 对话\n$body").useImport { novel ->
            assertEquals(
                listOf("他推开门。", "“你来了。”", "“我来了。”", "“坐吧。”"),
                paragraphs(novel.readChapter(novel.tableOfContents.single().id)),
            )
        }
    }

    @Test
    fun oneHundredLineParagraphsWithSingleStrayIndentStayOneHundred() {
        val body = (1..100).joinToString(separator = "") { index ->
            if (index == 50) "\u3000\u3000第${index}行带一个游离缩进\n"
            else "第${index}行没有句末标点的段落内容\n"
        }
        open("stray-indent.txt", "第一章 行\n$body").useImport { novel ->
            val paragraphs = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Paragraph>()
            assertEquals(100, paragraphs.size)
            assertEquals("第50行带一个游离缩进", paragraphs[49].text)
        }
    }

    @Test
    fun threeLineWrappedParagraphsAtOneThirdIndentedAreJoined() {
        val body = (1..10).joinToString(separator = "") { index ->
            "\u3000\u3000第${index}段首行，\n第二行继续，\n第三行结束。\n"
        }
        open("three-line.txt", "第一章 丙\n$body").useImport { novel ->
            assertEquals(10, paragraphs(novel.readChapter(novel.tableOfContents.single().id)).size)
        }
    }

    // ---- Round 2 / M2: a 目录 word inside real prose must never delete content ----

    @Test
    fun tocWordInsideChapterBodyStaysInTheBody() {
        val text = "第一章 甲\n正文第一行。\n目录\n第二章 乙\n正文二。\n"
        open("body-toc-word.txt", text).useImport { novel ->
            // "目录" is kept in 甲's body; "第二章 乙" is a genuine heading with text, so it becomes
            // its own chapter. No line is deleted either way.
            assertEquals(listOf("第一章 甲", "第二章 乙"), novel.tableOfContents.map { it.title })
            assertEquals(
                listOf("正文第一行。", "目录"),
                paragraphs(novel.readChapter(novel.tableOfContents[0].id)),
            )
            assertEquals(listOf("正文二。"), paragraphs(novel.readChapter(novel.tableOfContents[1].id)))
        }
    }

    @Test
    fun chapterAfterAChapterLevelTocWordIsNotSwallowed() {
        val text = "第一章 甲\n目录\n第一章 夜雨\n正文甲。\n"
        open("body-toc-word2.txt", text).useImport { novel ->
            // "第一章 夜雨" is a real chapter inside 甲's text, not a TOC entry; nothing is deleted.
            assertEquals(listOf("第一章 甲", "第一章 夜雨"), novel.tableOfContents.map { it.title })
            assertEquals(listOf("目录"), paragraphs(novel.readChapter(novel.tableOfContents[0].id)))
            assertEquals(listOf("正文甲。"), paragraphs(novel.readChapter(novel.tableOfContents[1].id)))
        }
    }

    // ---- Round 2 / M3: TOC listing followed by a real volume must keep the volume ----

    @Test
    fun tocListingDoesNotSwallowTheRealVolumeHeading() {
        val text = "目录\n第一卷 启程\n第一章 甲\n\n第一卷 启程\n第一章 甲\n正文甲。\n"
        open("toc-volume.txt", text).useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(listOf(0 to "第一卷 启程", 1 to "第一章 甲"), toc.map { it.level to it.title })
            assertEquals(toc[0].id, toc[1].parentId, "chapter must link to the surviving real volume")
        }
    }

    @Test
    fun tocListingWithTwoListedChaptersKeepsVolumeHierarchy() {
        val text = "目录\n第一卷 启程\n第一章 甲\n第二章 乙\n\n" +
            "第一卷 启程\n第一章 甲\n正文甲。\n\n第二章 乙\n正文乙。\n"
        open("toc-volume2.txt", text).useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(
                listOf(0 to "第一卷 启程", 1 to "第一章 甲", 1 to "第二章 乙"),
                toc.map { it.level to it.title },
            )
            assertEquals(toc[0].id, toc[1].parentId)
            assertEquals(toc[0].id, toc[2].parentId)
            assertEquals(listOf(1, 1), listOf(toc[1], toc[2]).map { novel.readChapter(it.id).blocks.size })
        }
    }

    @Test
    fun tocListingKeepsALaterRealVolumeAndItsChapters() {
        val text = "目录\n第一卷 启程\n第一章 甲\n第二章 乙\n\n" +
            "第一卷 启程\n第一章 甲\n正文甲。\n\n第二卷 远行\n第二章 乙\n正文乙。\n"
        open("toc-two-volumes.txt", text).useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(
                listOf(0 to "第一卷 启程", 1 to "第一章 甲", 0 to "第二卷 远行", 1 to "第二章 乙"),
                toc.map { it.level to it.title },
            )
            assertNull(toc[0].parentId)
            assertNull(toc[2].parentId)
            assertEquals(toc[0].id, toc[1].parentId)
            assertEquals(toc[2].id, toc[3].parentId)
        }
    }

    // ---- Round 2 / M4: a title page after the TOC listing must become front matter ----

    @Test
    fun titlePageAfterTocListingBecomesFrontMatterNotChapterBody() {
        val text = "目录\n第一章 夜雨\n第二章 旧信\n\n" +
            "长夜行\n作者：佚名\n\n第一章 夜雨\n雨下了一整夜。\n\n第二章 旧信\n信纸泛黄。\n"
        open("toc-titlepage.txt", text).useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(listOf("长夜行", "第一章 夜雨", "第二章 旧信"), toc.map { it.title })
            // The title page lives in its own front-matter section, never inside chapter two.
            assertEquals(
                listOf("长夜行", "作者：佚名"),
                paragraphs(novel.readChapter(toc[0].id)),
            )
            assertEquals(listOf("雨下了一整夜。"), paragraphs(novel.readChapter(toc[1].id)))
            assertEquals(listOf("信纸泛黄。"), paragraphs(novel.readChapter(toc[2].id)))
        }
    }

    @Test
    fun danglingListedChapterIsDroppedWithoutLosingRealChapters() {
        // The listing names three chapters but the body only has two, so the third listed title must
        // vanish rather than leaving an empty chapter.
        val text = "目录\n第一章 甲\n第二章 乙\n第三章 丙\n\n" +
            "第一章 甲\n正文甲。\n\n第二章 乙\n正文乙。\n"
        open("toc-dangling.txt", text).useImport { novel ->
            assertEquals(
                listOf("第一章 甲", "第二章 乙"),
                novel.tableOfContents.map { it.title },
            )
            assertTrue(novel.tableOfContents.all { novel.readChapter(it.id).blocks.isNotEmpty() })
        }
    }

    @Test
    fun genuinelyEmptyChapterWithNoLaterTwinIsKept() {
        // No TOC listing exists, so an empty chapter must not be deleted as if it were an entry.
        val text = "第一章 甲\n\n第二章 乙\n正文乙。\n"
        open("empty-real-chapter.txt", text).useImport { novel ->
            assertEquals(listOf("第一章 甲", "第二章 乙"), novel.tableOfContents.map { it.title })
            assertEquals(emptyList(), novel.readChapter(novel.tableOfContents[0].id).blocks)
            assertEquals(listOf("正文乙。"), paragraphs(novel.readChapter(novel.tableOfContents[1].id)))
        }
    }

    @Test
    fun tocWordAtEndOfFileStaysInTheBody() {
        // A 目录 line after the chapter text is content, not a listing marker.
        open("toc-tail.txt", "第一章 甲\n正文。\n目录\n").useImport { novel ->
            assertEquals(
                listOf("正文。", "目录"),
                paragraphs(novel.readChapter(novel.tableOfContents.single().id)),
            )
        }
    }

    // ---- Round 2 / M5 (low): marker spacing ----

    @Test
    fun tocMarkerWithInnerWhitespaceIsRecognized() {
        open("toc-ideographic.txt", "目\u3000录\n第一章 甲\n正文甲。\n").useImport { novel ->
            assertEquals(listOf("第一章 甲"), novel.tableOfContents.map { it.title })
        }
        open("toc-space.txt", "目 录\n第一章 甲\n正文甲。\n").useImport { novel ->
            assertEquals(listOf("第一章 甲"), novel.tableOfContents.map { it.title })
        }
    }

    // ---- Finding 3: additional common heading forms ----

    @Test
    fun recognizesVolumePrefixAndAdditionalNamedHeadings() {
        val text = "卷一 少年游\n正文一。\n番外篇\n正文二。\n第１章 夜雨\n正文三。\n" +
            "第1節 灯火\n正文四。\n序\n正文五。\n引子\n正文六。\n终章\n正文七。\n跋\n正文八。\n"
        open("forms.txt", text).useImport { novel ->
            assertEquals(
                listOf("卷一 少年游", "番外篇", "第１章 夜雨", "第1節 灯火", "序", "引子", "终章", "跋"),
                novel.tableOfContents.map { it.title },
            )
            // 卷一 is a volume; the rest are readable chapters.
            assertEquals(listOf(0, 1, 1, 1, 1, 1, 1, 1), novel.tableOfContents.map { it.level })
            // Full-width digits are preserved verbatim in the title.
            assertEquals(
                listOf("正文三。"),
                paragraphs(novel.readChapter(novel.tableOfContents.single { it.title == "第１章 夜雨" }.id)),
            )
        }
    }

    // ---- Finding 4: embedded 目录 pages ----

    @Test
    fun embeddedTableOfContentsPageProducesNoEmptyChapters() {
        val text = "目录\n第一章 夜雨\n第二章 旧信\n\n" +
            "第一章 夜雨\n雨下了一整夜。\n\n第二章 旧信\n信纸已经泛黄。\n"
        open("toc-page.txt", text).useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(listOf("第一章 夜雨", "第二章 旧信"), toc.map { it.title })
            // No duplicate ids and every listed chapter has real content.
            assertEquals(toc.size, toc.map { it.id }.distinct().size)
            assertEquals(listOf("雨下了一整夜。"), paragraphs(novel.readChapter(toc[0].id)))
            assertEquals(listOf("信纸已经泛黄。"), paragraphs(novel.readChapter(toc[1].id)))
        }
    }

    @Test
    fun tableOfContentsPageWithoutMarkerLineIsAlsoDropped() {
        val text = "第一章 夜雨\n第二章 旧信\n\n第一章 夜雨\n雨下了一整夜。\n\n第二章 旧信\n信纸已经泛黄。\n"
        open("toc-nomarker.txt", text).useImport { novel ->
            assertEquals(listOf("第一章 夜雨", "第二章 旧信"), novel.tableOfContents.map { it.title })
            assertTrue(novel.tableOfContents.all { novel.readChapter(it.id).blocks.isNotEmpty() })
        }
    }

    @Test
    fun tocPageDoesNotSwallowRealHeadingsWhenBodyFollows() {
        // A real file where the first heading has content must not be treated as a TOC entry.
        val text = "第一章 夜雨\n雨下了一整夜。\n第二章 旧信\n信纸已经泛黄。\n"
        open("real-only.txt", text).useImport { novel ->
            assertEquals(listOf("第一章 夜雨", "第二章 旧信"), novel.tableOfContents.map { it.title })
            assertEquals(listOf("雨下了一整夜。"), paragraphs(novel.readChapter(novel.tableOfContents[0].id)))
        }
    }

    // ---- Finding 6: lone CR line endings ----

    @Test
    fun loneCarriageReturnLineEndingsAreNormalized() {
        open("cr.txt", "第一章 甲\r第一行\r第二行\r").useImport { novel ->
            assertEquals(listOf("第一章 甲"), novel.tableOfContents.map { it.title })
            assertEquals(
                listOf("第一行", "第二行"),
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    // ---- Finding 2: close() releases retained bodies ----

    @Test
    fun closeReleasesRetainedSectionBodies() {
        val body = "正文行。\n".repeat(5000)
        val novel = open("big.txt", "第一章 夜雨\n$body") as dev.reader.media.novel.txt.TxtNovel
        val id = novel.tableOfContents.single().id
        novel.readChapter(id)
        // This file has no text before the first heading, so there is one section, no front matter.
        assertEquals(1, novel.retainedSectionCount, "chapter section before close")
        novel.close()
        assertEquals(0, novel.retainedSectionCount, "close() must drop section bodies")
        assertFailsWith<NovelClosedException> { novel.readChapter(id) }
    }

    @Test
    fun useImportClosesWhenTheBlockThrows() {
        val novel = open("throws.txt", "第一章 甲\n正文。\n")
        val id = novel.tableOfContents.single().id
        assertFailsWith<IllegalStateException> {
            novel.useImport { book ->
                book.readChapter(id)
                error("boom from block")
            }
        }
        assertFailsWith<NovelClosedException> { novel.readChapter(id) }
    }

    @Test
    fun emptyVolumeHeadingsStillAppearInTableOfContents() {
        open("volumes.txt", "第一卷 空卷\n第二卷 有内容\n第一章 一\n正文。\n").useImport { novel ->
            assertEquals(
                listOf("第一卷 空卷", "第二卷 有内容", "第一章 一"),
                novel.tableOfContents.map { it.title },
            )
            val emptyVolume = novel.readChapter(novel.tableOfContents.first().id)
            assertEquals(emptyList(), emptyVolume.blocks)
        }
    }
}

/** Minimal in-memory filesystem serving one file; everything else throws [UnsupportedOperationException]. */
private class SingleFileFileSystem(private val content: ByteArray) : FileSystem() {
    private val canonical = "/memory.txt".toPath()

    override fun canonicalize(path: Path): Path = path
    override fun metadataOrNull(path: Path) = if (path.name == canonical.name) {
        okio.FileMetadata(isRegularFile = true, isDirectory = false, size = content.size.toLong())
    } else {
        null
    }

    override fun list(dir: Path): List<Path> = emptyList()
    override fun listOrNull(dir: Path): List<Path>? = emptyList()
    override fun openReadOnly(file: Path): okio.FileHandle = throw UnsupportedOperationException("not used")
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): okio.FileHandle =
        throw UnsupportedOperationException("not used")

    override fun source(file: Path): Source = object : Source {
        private val buffer = Buffer().write(content)
        private var closed = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (closed) throw okio.IOException("closed")
            return buffer.read(sink, byteCount)
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
            buffer.clear()
        }
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink = throw UnsupportedOperationException("read-only")
    override fun appendingSink(file: Path, mustExist: Boolean): Sink = throw UnsupportedOperationException("read-only")
    override fun createDirectory(dir: Path, mustCreate: Boolean) = throw UnsupportedOperationException("read-only")
    override fun atomicMove(source: Path, target: Path) = throw UnsupportedOperationException("read-only")
    override fun delete(path: Path, mustExist: Boolean) = throw UnsupportedOperationException("read-only")
    override fun createSymlink(source: Path, target: Path) = throw UnsupportedOperationException("read-only")
    override fun toString(): String = "SingleFileFileSystem(${content.size} bytes)"
}
