package app.koyura.media.novel.epub

import app.koyura.media.epub.EpubNovel
import app.koyura.media.epub.openEpubNovel
import app.koyura.media.fixtureRoot
import app.koyura.media.novel.LocalNovelLimits
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelClosedException
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.useImport
import okio.Buffer
import okio.FileHandle
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import okio.openZip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wraps the *archive* filesystem (not the containing one) and records every entry read.
 *
 * Okio's `openZip` re-opens the archive by path for each read, so a wrapper around the containing
 * filesystem only sees the .epub path. Wrapping the zip filesystem itself makes entry-level reads
 * observable, which is exactly what "opening did not read chapters" means.
 */
private class EntryRecordingFileSystem(private val delegate: FileSystem) : FileSystem() {
    val readEntries = mutableListOf<String>()

    override fun canonicalize(path: Path): Path = delegate.canonicalize(path)
    override fun metadataOrNull(path: Path) = delegate.metadataOrNull(path)
    override fun list(dir: Path): List<Path> = delegate.list(dir)
    override fun listOrNull(dir: Path): List<Path>? = delegate.listOrNull(dir)
    override fun openReadOnly(file: Path): FileHandle {
        readEntries += file.toString()
        return delegate.openReadOnly(file)
    }

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
        delegate.openReadWrite(file, mustCreate, mustExist)

    override fun source(file: Path): Source {
        readEntries += file.toString()
        val inner = delegate.source(file)
        return object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = inner.read(sink, byteCount)
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = inner.close()
        }
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink = delegate.sink(file, mustCreate)
    override fun appendingSink(file: Path, mustExist: Boolean) = delegate.appendingSink(file, mustExist)
    override fun createDirectory(dir: Path, mustCreate: Boolean) = delegate.createDirectory(dir, mustCreate)
    override fun atomicMove(source: Path, target: Path) = delegate.atomicMove(source, target)
    override fun delete(path: Path, mustExist: Boolean) = delegate.delete(path, mustExist)
    override fun createSymlink(source: Path, target: Path) = delegate.createSymlink(source, target)
    override fun toString(): String = "EntryRecordingFileSystem"
}

/** Builds the mandatory central-directory index for a fixture. */
internal fun centralDirectoryFor(name: String): app.koyura.media.epub.ZipCentralDirectory =
    requireNotNull(
        app.koyura.media.epub.ZipCentralDirectory.read(FileSystem.SYSTEM, EpubFixtures.path(name)),
    ) { "fixture $name has no central directory" }

class EpubLazyLoadingTest {

    /** Opens [name], returning the novel plus the recording zip filesystem wrapper. */
    private fun openRecorded(name: String): Triple<app.koyura.media.novel.NovelImport, EntryRecordingFileSystem, FileSystem> {
        val container = FileSystem.SYSTEM
        val zip = container.openZip(EpubFixtures.path(name))
        val recorder = EntryRecordingFileSystem(zip)
        val novel = app.koyura.media.epub.EpubNovel.openArchiveFileSystem(
            displayPath = EpubFixtures.path(name).toString(),
            archiveFileSystem = recorder,
            limits = LocalNovelLimits.DEFAULT,
            zipIndex = centralDirectoryFor(name),
        )
        return Triple(novel, recorder, container)
    }

    private fun entryReads(recorder: EntryRecordingFileSystem): List<String> =
        recorder.readEntries.map { it.removePrefix("/") }

    @Test
    fun openingDoesNotReadChapterDocuments() {
        val (novel, recorder, _) = openRecorded("pride-and-prejudice.epub")
        try {
            val reads = entryReads(recorder)
            assertTrue(
                reads.none { Regex("text/chapter-\\d+\\.xhtml$").containsMatchIn(it) },
                "opening must not read chapter XHTML, but read $reads",
            )
            // Opening did read the container, the OPF and the nav.
            assertTrue(reads.any { it == "META-INF/container.xml" }, "container not read: $reads")
            assertTrue(reads.any { it == "epub/content.opf" }, "OPF not read: $reads")
            assertTrue(reads.any { it == "epub/toc.xhtml" }, "nav not read: $reads")

            val before = recorder.readEntries.size
            val first = novel.tableOfContents.first { it.title == "I" }
            novel.readChapter(first.id)
            val openedAfter = entryReads(recorder).drop(before)
            assertEquals(
                listOf("epub/text/chapter-1.xhtml"),
                openedAfter,
                "a single chapter read should open only that document",
            )
        } finally {
            novel.close()
        }
    }

    @Test
    fun secondReadOfTheSameChapterUsesTheCache() {
        val (novel, recorder, _) = openRecorded("pride-and-prejudice.epub")
        try {
            val id = novel.tableOfContents.first { it.title == "I" }.id
            novel.readChapter(id)
            val afterFirst = recorder.readEntries.size
            novel.readChapter(id)
            assertEquals(afterFirst, recorder.readEntries.size, "cached chapter must not be re-read")
        } finally {
            novel.close()
        }
    }

    @Test
    fun resourcesAreOnlyReadOnDemand() {
        val (novel, recorder, _) = openRecorded("pride-and-prejudice.epub")
        try {
            val coverPath = novel.metadata.cover!!.path
            assertTrue(
                entryReads(recorder).none { it == coverPath },
                "cover bytes must not be read at open",
            )
            novel.readResource(coverPath)
            assertTrue(entryReads(recorder).any { it == coverPath })
        } finally {
            novel.close()
        }
    }
}

class EpubLazyOpenContractTest {

    /** Records entry reads through the archive filesystem so "did open read chapters?" is provable. */
    private fun openRecorded(name: String): Pair<app.koyura.media.novel.NovelImport, List<String>> {
        val zip = FileSystem.SYSTEM.openZip(EpubFixtures.path(name))
        val recorder = EntryRecordingFileSystem(zip)
        val novel = app.koyura.media.epub.EpubNovel.openArchiveFileSystem(
            displayPath = EpubFixtures.path(name).toString(),
            archiveFileSystem = recorder,
            limits = LocalNovelLimits.DEFAULT,
            zipIndex = centralDirectoryFor(name),
        )
        return novel to recorder.readEntries.map { it.removePrefix("/") }
    }

    @Test
    fun navBackedOpenNeverReadsChapterDocuments() {
        val (novel, reads) = openRecorded("spine-order.epub")
        try {
            assertTrue(
                reads.none { it.endsWith("chapter-1.xhtml") || it.endsWith("chapter-2.xhtml") },
                "nav path read chapter bodies at open: $reads",
            )
        } finally {
            novel.close()
        }
    }

    @Test
    fun navlessOpenReadsTitlesAsDocumentedException() {
        // The no-nav fixture has nothing to derive titles from except the documents themselves.
        val (novel, reads) = openRecorded("no-nav.epub")
        try {
            assertTrue(
                reads.any { it.endsWith("c1.xhtml") },
                "spine fallback did not read titles, yet the TOC has titles: $reads",
            )
            assertEquals(listOf("Derived One", "Derived Two"), novel.tableOfContents.map { it.title })
        } finally {
            novel.close()
        }
    }

    /**
     * The exact bug this suite guards: a resume used to read every chapter body, so a later chapter
     * over the size cap broke an otherwise exact resume.
     */
    @Test
    fun exactResumeReadsOnlyTheAnchoredChapterDocument() {
        val zip = FileSystem.SYSTEM.openZip(EpubFixtures.path("spine-order.epub"))
        val recorder = EntryRecordingFileSystem(zip)
        val novel = app.koyura.media.epub.EpubNovel.openArchiveFileSystem(
            displayPath = EpubFixtures.path("spine-order.epub").toString(),
            archiveFileSystem = recorder,
            limits = LocalNovelLimits.DEFAULT,
            zipIndex = centralDirectoryFor("spine-order.epub"),
        )
        try {
            val entry = novel.tableOfContents.first { it.level == 1 }
            val paragraphIndex = novel.readChapter(entry.id).blocks
                .indexOfFirst { it is NovelBlock.Paragraph }
            val stored = novel.positionOf(entry.id, paragraphIndex, codePointOffset = 3)

            val before = recorder.readEntries.size
            val resolution = novel.resolvePosition(stored)
            assertEquals(
                app.koyura.media.novel.PositionResolution.ResolutionKind.EXACT,
                resolution.resolution,
            )
            val resumeReads = recorder.readEntries.drop(before)
                .map { it.removePrefix("/") }
                .filter { it.endsWith(".xhtml") }
            // The anchored chapter was already parsed by positionOf, so resolution serves it from the
            // cache: zero document reads. Reading any other chapter here would show up as an extra
            // entry and fail the assertion.
            assertTrue(
                resumeReads.isEmpty(),
                "an exact resume must not read any chapter document, got $resumeReads",
            )
        } finally {
            novel.close()
        }
    }

    /** A cross-chapter fallback reads candidate documents one at a time and stops at the first match. */
    @Test
    fun neighbourChapterFallbackReadsOnlyUntilItFindsAParagraph() {
        val zip = FileSystem.SYSTEM.openZip(EpubFixtures.path("no-nav.epub"))
        val recorder = EntryRecordingFileSystem(zip)
        val novel = app.koyura.media.epub.EpubNovel.openArchiveFileSystem(
            displayPath = EpubFixtures.path("no-nav.epub").toString(),
            archiveFileSystem = recorder,
            limits = LocalNovelLimits.DEFAULT,
            zipIndex = centralDirectoryFor("no-nav.epub"),
        )
        try {
            // A position whose chapter id does not exist in this book forces the neighbour-chapter rule.
            val stored = app.koyura.media.novel.ReadingPosition(
                chapterId = "epub-does-not-exist",
                contentVersion = novel.contentVersion,
                blockIndex = 0,
                blockFingerprint = app.koyura.media.novel.NovelFingerprints.block("x"),
                codePointOffset = 0,
                chapterIndex = 0,
            )
            val before = recorder.readEntries.size
            val resolution = novel.resolvePosition(stored)
            assertEquals(
                app.koyura.media.novel.PositionResolution.ResolutionKind.NEIGHBOR_CHAPTER,
                resolution.resolution,
            )
            val fallbackReads = recorder.readEntries.drop(before)
                .map { it.removePrefix("/") }
                .filter { it.endsWith(".xhtml") }
            assertEquals(
                listOf("OEBPS/text/c1.xhtml"),
                fallbackReads,
                "fallback must stop at the first readable chapter, got $fallbackReads",
            )
        } finally {
            novel.close()
        }
    }
}

class EpubContentSafetyTest {

    @Test
    fun scriptsAndStylesAreExcludedFromBlocks() {
        EpubFixtures.open("unsafe-ref.epub").useImport { novel ->
            val chapter = novel.readChapter(novel.tableOfContents.single().id)
            val text = chapter.blocks.joinToString("\n") { block ->
                when (block) {
                    is NovelBlock.Paragraph -> block.text
                    is NovelBlock.Heading -> block.text
                    else -> ""
                }
            }
            assertFalse(text.contains("fetch("), "script content leaked into blocks")
            assertFalse(text.contains("evil.js"))
            // Surrounding prose survives, so exclusion did not drop the chapter.
            assertTrue(text.contains("Before."))
            assertTrue(text.contains("After."))
        }
    }

    @Test
    fun remoteImagesAreNeverReadableResources() {
        EpubFixtures.open("unsafe-ref.epub").useImport { novel ->
            val images = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Image>()
            val remote = images.single { it.reference.altText == "remote" }
            assertEquals("https://example.com/remote.png", remote.reference.source)
            assertEquals(null, remote.reference.resource)
        }
    }

    @Test
    fun readingAResourceThatIsNotInTheArchiveIsMissingEntry() {
        EpubFixtures.open("refs.epub").useImport { novel ->
            val error = assertFailsWith<NovelImportException.MissingEntry> {
                novel.readResource("OEBPS/images/does-not-exist.png")
            }
            assertEquals("OEBPS/images/does-not-exist.png", error.entry)
        }
    }
}

class EpubLimitsTest {

    @Test
    fun chapterByteLimitIsEnforced() {
        val limits = LocalNovelLimits(maxChapterBytes = 200, maxResourceBytes = Long.MAX_VALUE, maxChapterCount = 5000)
        openEpubNovel(FileSystem.SYSTEM, EpubFixtures.path("spine-order.epub"), limits).useImport { novel ->
            val error = assertFailsWith<NovelImportException.LimitExceeded> {
                novel.readChapter(novel.tableOfContents.first().id)
            }
            assertEquals("chapter-bytes", error.limit)
            assertEquals(200L, error.maximum)
            assertTrue(error.actual > 200)
            assertTrue(error.entry!!.endsWith("chapter-1.xhtml"))
        }
    }

    @Test
    fun resourceByteLimitIsEnforced() {
        val limits = LocalNovelLimits(maxChapterBytes = Long.MAX_VALUE, maxResourceBytes = 16, maxChapterCount = 5000)
        openEpubNovel(FileSystem.SYSTEM, EpubFixtures.path("refs.epub"), limits).useImport { novel ->
            val error = assertFailsWith<NovelImportException.LimitExceeded> {
                novel.readResource("OEBPS/images/a b.png")
            }
            assertEquals("resource-bytes", error.limit)
            assertEquals(16L, error.maximum)
        }
    }

    @Test
    fun chapterCountLimitIsEnforcedAtOpen() {
        val limits = LocalNovelLimits(maxChapterBytes = Long.MAX_VALUE, maxResourceBytes = Long.MAX_VALUE, maxChapterCount = 2)
        val error = assertFailsWith<NovelImportException.LimitExceeded> {
            openEpubNovel(FileSystem.SYSTEM, EpubFixtures.path("pride-and-prejudice.epub"), limits)
        }
        assertEquals("chapter-count", error.limit)
        assertEquals(2L, error.maximum)
        assertTrue(error.actual > 2)
    }

    @Test
    fun limitsRejectNonPositiveValues() {
        assertFailsWith<IllegalArgumentException> { LocalNovelLimits(maxChapterBytes = 0) }
        assertFailsWith<IllegalArgumentException> { LocalNovelLimits(maxResourceBytes = 0) }
        assertFailsWith<IllegalArgumentException> { LocalNovelLimits(maxChapterCount = 0) }
    }
}

class EpubLifecycleTest {

    @Test
    fun closeReleasesParsedChapters() {
        val novel = EpubFixtures.open("spine-order.epub")
        val id = novel.tableOfContents.first().id
        novel.readChapter(id)
        assertEquals(1, (novel as EpubNovel).cachedChapterCount)
        novel.close()
        assertEquals(0, (novel as EpubNovel).cachedChapterCount)
    }

    @Test
    fun readAfterCloseThrowsClosedException() {
        val novel = EpubFixtures.open("spine-order.epub")
        val id = novel.tableOfContents.first().id
        assertEquals(2, novel.tableOfContents.size)
        novel.close()
        assertFailsWith<NovelClosedException> { novel.readChapter(id) }
        assertFailsWith<NovelClosedException> { novel.readResource("OEBPS/text/chapter-1.xhtml") }
    }

    @Test
    fun closeIsIdempotentAndLeavesMetadataReadable() {
        val novel = EpubFixtures.open("spine-order.epub")
        novel.close()
        novel.close()
        assertEquals("Spine Order", novel.metadata.title)
        assertEquals(listOf("Fixture Author"), novel.metadata.authors)
        assertEquals(2, novel.tableOfContents.size)
    }

    @Test
    fun chapterIdsAreStableAcrossOpens() {
        val first = EpubFixtures.open("spine-order.epub")
        val second = EpubFixtures.open("spine-order.epub")
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
    fun failedOpenClosesTheZipFilesystem() {
        // The missing-entry fixture opens a zip, then fails while validating the spine, which is the
        // failure path where a leaked handle would actually hurt.
        val container = FileSystem.SYSTEM
        val closedRecorder = CloseCountingFileSystem(container.openZip(EpubFixtures.path("missing-entry.epub")))
        val error = assertFailsWith<NovelImportException.MissingEntry> {
            app.koyura.media.epub.EpubNovel.openArchiveFileSystem(
                displayPath = "missing-entry.epub",
                archiveFileSystem = closedRecorder,
                limits = LocalNovelLimits.DEFAULT,
                zipIndex = centralDirectoryFor("missing-entry.epub"),
            )
        }
        assertTrue(error.entry.endsWith("chapter-2.xhtml"))
        assertEquals(1, closedRecorder.closeCount, "failed open must close the archive filesystem")

        // The corrupted fixture fails before a zip filesystem exists; it must not leak an exception
        // type other than CorruptedArchive.
        val corrupted = assertFailsWith<NovelImportException.CorruptedArchive> {
            openEpubNovel(container, EpubFixtures.path("corrupted.epub"))
        }
        assertEquals("not-a-zip", corrupted.detail)
    }

    @Test
    fun explicitCloseClosesTheArchiveFilesystemOnce() {
        val container = FileSystem.SYSTEM
        val recorder = CloseCountingFileSystem(container.openZip(EpubFixtures.path("spine-order.epub")))
        val novel = app.koyura.media.epub.EpubNovel.openArchiveFileSystem(
            displayPath = "spine-order.epub",
            archiveFileSystem = recorder,
            limits = LocalNovelLimits.DEFAULT,
            zipIndex = centralDirectoryFor("spine-order.epub"),
        )
        novel.close()
        assertEquals(1, recorder.closeCount)
        novel.close()
        assertEquals(1, recorder.closeCount, "close must be idempotent")
    }
}

/** Records how many times [close] was called on the wrapped archive filesystem. */
private class CloseCountingFileSystem(private val delegate: FileSystem) : FileSystem() {
    var closeCount = 0
        private set

    override fun canonicalize(path: Path): Path = delegate.canonicalize(path)
    override fun metadataOrNull(path: Path) = delegate.metadataOrNull(path)
    override fun list(dir: Path): List<Path> = delegate.list(dir)
    override fun listOrNull(dir: Path): List<Path>? = delegate.listOrNull(dir)
    override fun openReadOnly(file: Path) = delegate.openReadOnly(file)
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean) =
        delegate.openReadWrite(file, mustCreate, mustExist)
    override fun source(file: Path): Source = delegate.source(file)
    override fun sink(file: Path, mustCreate: Boolean): Sink = delegate.sink(file, mustCreate)
    override fun appendingSink(file: Path, mustExist: Boolean) = delegate.appendingSink(file, mustExist)
    override fun createDirectory(dir: Path, mustCreate: Boolean) = delegate.createDirectory(dir, mustCreate)
    override fun atomicMove(source: Path, target: Path) = delegate.atomicMove(source, target)
    override fun delete(path: Path, mustExist: Boolean) = delegate.delete(path, mustExist)
    override fun createSymlink(source: Path, target: Path) = delegate.createSymlink(source, target)
    override fun close() {
        closeCount++
        delegate.close()
    }
    override fun toString(): String = "CloseCountingFileSystem"
}
