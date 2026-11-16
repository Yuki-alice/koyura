package app.koyura.media.novel.txt

import app.koyura.media.novel.ChapterHeadingDetector
import app.koyura.media.novel.ImageReference
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelChapter
import app.koyura.media.novel.NovelClosedException
import app.koyura.media.novel.NovelImport
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.NovelMetadata
import app.koyura.media.novel.PositionResolution
import app.koyura.media.novel.ReadingPosition
import app.koyura.media.novel.ReadingPositionResolver
import app.koyura.media.novel.ReadingPositions
import app.koyura.media.novel.SeparatorStyle
import app.koyura.media.novel.buildContentVersion
import app.koyura.media.novel.TextDecoder
import app.koyura.media.novel.TextEncoding
import app.koyura.media.novel.TocEntry
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer

/**
 * Opens a TXT novel from [path] on [fileSystem].
 *
 * The file is read and fully decoded during this call; the underlying handle is closed before
 * [openTxtNovel] returns, on success and on every failure path. Chapter bodies are parsed lazily
 * from the decoded in-memory text when [NovelImport.readChapter] is called, and [NovelImport.close]
 * drops the decoded text, the parsed chapter cache and the section index, so a long-lived instance
 * that has been closed does not keep the whole book resident.
 *
 * Use `FileSystem.SYSTEM` in production and an injected [FileSystem] in tests. Okio is the only
 * dependency; nothing here needs Android or Compose.
 *
 * @throws NovelImportException.Empty when the file has zero bytes or only blank lines.
 * @throws NovelImportException.Encoding when the bytes are not valid UTF-8/UTF-16 text.
 * @throws NovelImportException.UnsupportedFormat when the bytes are a different container.
 * @throws NovelImportException.Io when opening or reading the file fails.
 */
fun openTxtNovel(fileSystem: FileSystem, path: Path): NovelImport =
    TxtNovel.open(fileSystem = fileSystem, path = path)

/**
 * Stable TOC id format: `txt-` followed by 16 lowercase hex digits.
 *
 * The id hashes the title and the occurrence ordinal of that title, **not** the text offset. Offsets
 * shift whenever an earlier chapter grows or shrinks, which would silently change every later id and
 * invalidate saved positions. Hashing title + occurrence keeps a chapter's id stable while other
 * chapters are edited, added or removed, and two chapters that share a title still differ by
 * occurrence.
 */
internal object TxtChapterIds {
    private const val PREFIX = "txt-"

    /** FNV-1a over `level|occurrence|title`, rendered as unsigned hex. Deterministic across platforms. */
    fun idFor(level: Int, occurrence: Int, title: String): String =
        PREFIX + fnv1a64("$level|$occurrence|$title").toULong().toString(16).padStart(16, '0')

    private fun fnv1a64(input: String): Long {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (byte in input.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= 0x100000001b3L
        }
        return hash
    }
}

/**
 * TXT implementation of [NovelImport].
 *
 * [close] is idempotent. After it, [readChapter] throws [NovelClosedException], the section index and
 * parsed chapter cache are emptied, and [metadata], [encoding] and [tableOfContents] remain readable
 * as immutable snapshots.
 */
internal class TxtNovel private constructor(
    override val file: String,
    override val metadata: NovelMetadata,
    /** Encoding detected for the file, exposed for diagnostics and tests. */
    override val encoding: TextEncoding,
    initialSections: List<Section>,
) : NovelImport {

    private var closed = false

    /**
     * Section index backing [readChapter]; emptied by [close].
     *
     * Mutable on purpose: `close()` must drop the decoded bodies deterministically rather than
     * waiting for GC, because the shelf may keep the [NovelImport] alive for a long time.
     */
    private var sections: List<Section> = initialSections
    private val chapterCache = mutableMapOf<String, NovelChapter>()

    /**
     * How many section bodies have been parsed into blocks since the last cache clear.
     *
     * Parsing is the lazy work this class avoids doing eagerly, so this counter is what a test can
     * observe to prove an operation only touched the chapters it needed. It is monotonic while the
     * cache lives and resets when [close] drops it.
     */
    internal var parsedSectionCount: Int = 0
        private set

    /** Sections that produce a table-of-contents entry, in file order. */
    override val tableOfContents: List<TocEntry> =
        initialSections.map { TocEntry(id = it.id, title = it.title, level = it.level, parentId = it.parentId) }

    /** Number of section bodies still retained; `0` after [close]. Used by tests to prove release. */
    internal val retainedSectionCount: Int get() = sections.size

    /**
     * Content version, computed eagerly from the decoded sections.
     *
     * TXT has no manifest, so the version hashes chapter ids and their raw section text in order. It
     * is computed at open, before any section can be released, using cheap hashing rather than block
     * parsing: identical file bytes always produce the same value, and [close] keeps this small string
     * while dropping the bodies.
     */
    override val contentVersion: String = buildContentVersion(initialSections)

    override fun readChapter(id: String): NovelChapter {
        checkOpen()
        return requireChapter(id)
    }

    override fun positionOf(chapterId: String, blockIndex: Int, codePointOffset: Int): ReadingPosition {
        checkOpen()
        val chapter = requireChapter(chapterId)
        return ReadingPositions.forBlockInChapter(
            chapterId = chapterId,
            chapterIndex = tableOfContents.indexOfFirst { it.id == chapterId },
            contentVersion = contentVersion,
            blocks = chapter.blocks,
            blockIndex = blockIndex,
            codePointOffset = codePointOffset,
        )
    }

    /**
     * Re-locates a stored position without parsing the whole book.
     *
     * Only section ids are read up front; a section body is parsed through [requireChapter] (and its
     * cache) when a resolver rule needs it, so an exact resume parses exactly one chapter.
     */
    override fun resolvePosition(stored: ReadingPosition): PositionResolution {
        checkOpen()
        return ReadingPositionResolver.resolve(
            chapterIds = sections.map { it.id },
            contentVersion = contentVersion,
            stored = stored,
            loader = ReadingPositionResolver.ChapterLoader { chapterId ->
                requireChapter(chapterId).blocks
            },
        )
    }

    private fun requireChapter(chapterId: String): NovelChapter {
        chapterCache[chapterId]?.let { return it }
        val section = sections.firstOrNull { it.id == chapterId }
            ?: throw IllegalArgumentException("$chapterId is not a chapter of $file")
        val blocks = SectionParser.parse(section.body)
        parsedSectionCount++
        val chapter = NovelChapter(
            id = section.id,
            title = section.title,
            level = section.level,
            parentId = section.parentId,
            blocks = blocks,
        )
        chapterCache[chapterId] = chapter
        return chapter
    }

    /**
     * TXT has no binary resources: covers and inline images are text references only.
     *
     * `close()` is checked first, so a closed import reports [NovelClosedException] consistently with
     * [readChapter]; only an open TXT import reports [UnsupportedOperationException].
     *
     * @throws NovelClosedException when the import has been closed.
     * @throws UnsupportedOperationException when open (TXT stores no binary resources).
     */
    override fun readResource(path: String): ByteArray {
        checkOpen()
        throw UnsupportedOperationException("TXT novels have no binary resources")
    }

    /** Drops decoded text and parsed chapters. Safe to call more than once. */
    override fun close() {
        closed = true
        chapterCache.clear()
        parsedSectionCount = 0
        sections = emptyList()
    }

    private fun checkOpen() {
        if (closed) throw NovelClosedException(file)
    }

    /**
     * One heading-delimited region of text. [body] excludes the heading line itself.
     *
     * [text] is what the content version hashes; for TXT that is the body, so an edit to any paragraph
     * changes the version.
     */
    internal data class Section(
        override val id: String,
        val title: String,
        val level: Int,
        val parentId: String?,
        val body: String,
    ) : app.koyura.media.novel.ContentVersionSection {
        override val text: String get() = body
    }

    companion object {
        private val AUTHOR_LINE = Regex("^作者[：:]\\s*(.+)$")
        private const val FRONT_MATTER_TITLE = "卷首"

        /** Characters that end a sentence or a dialogue line, used to spot real prose. */
        private const val PROSE_TERMINATORS = "。！？!?…；;：:”’\"'）》」』】"

        /** Title-only lines that mark an embedded table-of-contents page, not a chapter. */
        private val TOC_MARKERS = setOf(
            "目录",
            "目錄",
            "目次",
            "contents",
            "content",
            "table of contents",
            "tableofcontents",
        )

        fun open(fileSystem: FileSystem, path: Path): NovelImport {
            val displayPath = path.toString()
            val bytes = readAllBytes(fileSystem, path, displayPath)
            val decoded = TextDecoder.decode(displayPath, bytes)
            val text = normalizeLineEndings(decoded.text)
            val sections = buildSections(text)
            if (sections.isEmpty()) {
                throw NovelImportException.Empty(path = displayPath, byteSize = bytes.size.toLong())
            }
            return TxtNovel(
                file = displayPath,
                metadata = buildMetadata(path, text),
                encoding = decoded.encoding,
                initialSections = sections,
            )
        }

        /**
         * Converts every line terminator to `\n`.
         *
         * LF and CRLF are the documented forms, but old Mac files use a lone CR; leaving it in place
         * would collapse the file into one paragraph containing raw `\r` characters. Normalizing once
         * here also keeps section offsets consistent for id generation.
         */
        private fun normalizeLineEndings(text: String): String {
            if (text.indexOf('\r') < 0) return text
            return text.replace("\r\n", "\n").replace('\r', '\n')
        }

        /**
         * Reads the whole file, closing the source in every path.
         *
         * The handle is opened, drained and closed before any decoding happens, so a decoding
         * failure cannot leave a descriptor open. A close failure is intentionally swallowed: the
         * read already finished, and replacing a successful read with a close error helps nobody.
         * That is why [NovelImportException.Io.phase] is only ever `open` or `read`.
         */
        private fun readAllBytes(fileSystem: FileSystem, path: Path, displayPath: String): ByteArray {
            val source = try {
                fileSystem.source(path)
            } catch (e: IOException) {
                throw NovelImportException.Io(path = displayPath, phase = "open", cause = e)
            }
            // `use` is not available for okio.Source on every native target, so close explicitly.
            try {
                return source.buffer().readByteArray()
            } catch (e: IOException) {
                throw NovelImportException.Io(path = displayPath, phase = "read", cause = e)
            } finally {
                try {
                    source.close()
                } catch (_: IOException) {
                    // A close failure must not replace the read failure above.
                }
            }
        }

        /**
         * Builds metadata from the file name and, when present, a leading `作者：…` line.
         *
         * Title: file name without extension. Author: the first `作者：…` line among the first five
         * non-empty lines. The author line stays in the body, because TXT files carry no separate
         * metadata channel and dropping content is worse than repeating one line.
         */
        private fun buildMetadata(path: Path, text: String): NovelMetadata {
            val fileName = path.name
            val dot = fileName.lastIndexOf('.')
            val title = (if (dot > 0) fileName.substring(0, dot) else fileName).ifBlank { "未命名" }
            val author = text.lineSequence()
                .filter { it.isNotBlank() }
                .take(5)
                .mapNotNull { AUTHOR_LINE.matchEntire(it.trim())?.groupValues?.get(1)?.trim() }
                .firstOrNull()
                ?.takeIf { it.isNotEmpty() }
            return NovelMetadata(
                title = title,
                authors = listOfNotNull(author),
                language = null,
                cover = null,
            )
        }

        /** A heading discovered while walking the file, before TOC-page filtering. */
        private data class RawSection(
            val title: String,
            val level: Int,
            val offset: Long,
            val body: StringBuilder,
            val isHeading: Boolean,
        )

        /** Line-level scan result: heading text, plain body, or a dropped TOC listing line. */
        private sealed interface ScannedLine {
            data class Heading(val title: String, val level: Int) : ScannedLine

            data object Body : ScannedLine

            data object Dropped : ScannedLine
        }

        /**
         * Splits decoded text into heading-delimited sections.
         *
         * Every recognized heading starts a section, including empty volume headings — they must
         * still appear in the table of contents so hierarchy is visible. Non-blank text before the
         * first heading becomes the `卷首` front-matter section, so nothing is dropped. With no
         * headings at all the whole text becomes one section, which guarantees a readable chapter.
         *
         * Embedded table-of-contents pages are removed first; see [scanLines].
         */
        private fun buildSections(text: String): List<Section> {
            val lines = text.split('\n')
            val scanned = scanLines(lines)

            val raws = mutableListOf<RawSection>()
            var current = RawSection(
                title = FRONT_MATTER_TITLE,
                level = 1,
                offset = 0L,
                body = StringBuilder(),
                isHeading = false,
            )
            var offset = 0L
            var sawHeading = false

            for (index in lines.indices) {
                val rawLine = lines[index]
                when (val scan = scanned[index]) {
                    is ScannedLine.Heading -> {
                        if (current.isHeading || current.body.isNotBlank()) raws += current
                        sawHeading = true
                        current = RawSection(
                            title = scan.title,
                            level = scan.level,
                            offset = offset,
                            body = StringBuilder(),
                            isHeading = true,
                        )
                    }
                    ScannedLine.Body -> {
                        if (rawLine.isNotBlank() || current.body.isNotEmpty()) {
                            current.body.append(rawLine).append('\n')
                        }
                    }
                    ScannedLine.Dropped -> Unit
                }
                // `rawLine` is the text between '\n' delimiters, so this is the exact char offset.
                offset += rawLine.length + 1
            }
            if (current.isHeading || current.body.isNotBlank()) raws += current

            if (!sawHeading) {
                val body = raws.joinToString(separator = "") { it.body.toString() }
                if (body.isBlank()) return emptyList()
                return listOf(
                    Section(
                        id = TxtChapterIds.idFor(level = 1, occurrence = 0, title = "single"),
                        title = "正文",
                        level = 1,
                        parentId = null,
                        body = body,
                    ),
                )
            }

            // Occurrence counts per (level, title), so duplicate titles get distinct ids while each
            // chapter's id stays put when unrelated chapters change.
            val occurrences = HashMap<String, Int>()
            val sections = raws.map { raw ->
                val title = if (raw.isHeading) raw.title else frontMatterTitle(raw.body)
                val key = raw.level.toString() + "|" + title
                val occurrence = occurrences[key] ?: 0
                occurrences[key] = occurrence + 1
                Section(
                    id = TxtChapterIds.idFor(raw.level, occurrence, title),
                    title = title,
                    level = raw.level,
                    parentId = null,
                    body = raw.body.toString(),
                )
            }
            return linkParents(sections)
        }

        /**
         * Classifies each line and removes embedded table-of-contents listings.
         *
         * A TOC page lists chapter titles without their text, and the real chapters repeat those
         * titles later with text. Detection therefore keys on a repeated *run* of headings, never on
         * a `目录` marker alone, so a marker word inside prose can never delete content.
         *
         * Algorithm, all indices into the heading occurrence list:
         * 1. Take the maximal **leading run** of headings, starting at the first heading, whose body
         *    contains no prose text. A bare book title or `作者：…` line is not prose, so a title page
         *    can sit inside this run without being mistaken for chapter text.
         * 2. Find the first later heading equal to the run's first heading and **truncate** the run to
         *    the headings before it. Truncation is what keeps a real volume heading that repeats the
         *    TOC entry from being dropped with the listing.
         * 3. Accept the listing when the truncated run shares at least two consecutive headings with
         *    the later run, or when a single non-volume title repeats and its later copy has text.
         * 4. Drop exactly the truncated run's heading lines. Trailing TOC entries with no later twin
         *    are dropped too, since they are part of that same leading run.
         * 5. A standalone `目录`/`目次`/`Contents` line is dropped when it sits before the first
         *    heading (a label position) or before a dropped listing; a marker word after a chapter
         *    has begun is body prose and is preserved. Whitespace inside the word is ignored, so
         *    `目　录` and `目 录` are recognized.
         *
         * Only heading lines are dropped; every non-heading line is returned as body, including the
         * title page. Nothing after the first prose line is ever reclassified, so real chapters and
         * dialogue are never touched.
         */
        private fun scanLines(lines: List<String>): List<ScannedLine> {
            val detected = lines.map { ChapterHeadingDetector.detect(it) }
            val marker = lines.map { isTocMarkerLine(it) }
            val headings = lines.indices.filter { detected[it] != null }

            // End index of each heading's body: the next heading position, or end of file.
            val nextHeading = IntArray(lines.size) { lines.size }
            var next = lines.size
            for (index in lines.indices.reversed()) {
                nextHeading[index] = next
                if (detected[index] != null) next = index
            }

            fun bodyHasProse(index: Int): Boolean {
                for (other in index + 1 until nextHeading[index]) {
                    if (marker[other]) continue
                    val trimmed = ChapterHeadingDetector.trimProseIndent(lines[other])
                    if (trimmed.isNotEmpty() && isProseLine(trimmed)) return true
                }
                return false
            }

            fun sameHeading(left: Int, right: Int): Boolean {
                val a = detected[left] ?: return false
                val b = detected[right] ?: return false
                return a.title == b.title && a.level == b.level
            }

            var droppedHeadings = emptyList<Int>()
            if (headings.isNotEmpty()) {
                // 1. Maximal leading run with no prose in any heading body.
                val leadingRun = mutableListOf<Int>()
                for (heading in headings) {
                    if (bodyHasProse(heading)) break
                    leadingRun += heading
                }

                // 2. First later heading equal to the run's first heading, then truncate.
                val first = leadingRun.firstOrNull()
                val laterStart = if (first == null) {
                    -1
                } else {
                    headings.indexOfFirst { it > first && sameHeading(first, it) }
                }
                if (first != null && laterStart > 0) {
                    val truncated = leadingRun.takeWhile { it < headings[laterStart] }
                    val later = headings.subList(laterStart, headings.size)
                    val matchLength = consecutiveMatchLength(truncated, later, ::sameHeading)
                    val singleWithBody = matchLength == 1 &&
                        (detected[first]?.level ?: 1) != 0 &&
                        bodyHasProse(later.first())
                    if (matchLength >= 2 || singleWithBody) {
                        droppedHeadings = truncated
                    }
                }
            }

            val firstDropped = droppedHeadings.minOrNull()
            val firstHeadingIndex = headings.firstOrNull() ?: Int.MAX_VALUE
            return lines.mapIndexed { index, line ->
                val heading = detected[index]
                // A standalone `目录`-like line is only a label before the first chapter; the same
                // word after a chapter has started is prose in the body and must be preserved.
                val isLeadingLabel = marker[index] && index < firstHeadingIndex
                val isListingLabel = firstDropped != null && marker[index] && index < firstDropped
                when {
                    index in droppedHeadings -> ScannedLine.Dropped
                    isLeadingLabel || isListingLabel -> ScannedLine.Dropped
                    heading == null -> ScannedLine.Body
                    else -> ScannedLine.Heading(heading.title, heading.level)
                }
            }
        }

        /** Length of the common prefix of [left] and [right] under [same]. */
        private inline fun consecutiveMatchLength(
            left: List<Int>,
            right: List<Int>,
            same: (Int, Int) -> Boolean,
        ): Int {
            val limit = minOf(left.size, right.size)
            var length = 0
            while (length < limit && same(left[length], right[length])) length++
            return length
        }

        /**
         * `true` for a non-blank line that reads like narrative text rather than a title or author.
         *
         * Length and terminal punctuation are both used so that `长夜行` / `作者：佚名` on a title page
         * do not count, while `雨下了一整夜。` and long wrapped lines do. A dialogue line ends with a
         * quote, which is why closing quotes are prose terminators.
         */
        private fun isProseLine(trimmed: String): Boolean {
            if (trimmed.length >= 10) return true
            return trimmed.last() in PROSE_TERMINATORS
        }

        /**
         * `true` for a standalone `目录` / `目次` / `Contents` line that marks a TOC page.
         *
         * Whitespace inside the word is ignored, so `目　录` (ideographic space) and `目 录` match.
         */
        private fun isTocMarkerLine(line: String): Boolean {
            val compact = ChapterHeadingDetector.trimProseIndent(line)
                .filterNot { it == ' ' || it == '\t' || it == '\u3000' }
                .trimEnd(':', '：')
                .lowercase()
            return compact in TOC_MARKERS
        }

        /**
         * Assigns `parentId` after filtering so removed TOC entries cannot leave dangling links.
         *
         * A level-1 section links to the most recent level-0 (volume) section; volumes themselves are
         * top level.
         */
        private fun linkParents(sections: List<Section>): List<Section> {
            var lastVolumeId: String? = null
            return sections.map { section ->
                if (section.level == 0) {
                    lastVolumeId = section.id
                    section.copy(parentId = null)
                } else {
                    section.copy(parentId = lastVolumeId)
                }
            }
        }

        /**
         * Names the front-matter TOC entry after the book's title line.
         *
         * The first non-blank line is used only when it plausibly is a title: short and free of
         * sentence punctuation. Otherwise the generic `卷首` label avoids putting prose in the TOC.
         */
        private fun frontMatterTitle(body: StringBuilder): String {
            val first = body.lineSequence().firstOrNull { it.isNotBlank() }
                ?.let(ChapterHeadingDetector::trimProseIndent)
                ?: return FRONT_MATTER_TITLE
            val looksLikeTitle = first.length <= 30 && first.none { it in "。；？！，," }
            return if (looksLikeTitle) first else FRONT_MATTER_TITLE
        }
    }
}

/**
 * Turns a chapter body into [NovelBlock]s.
 *
 * Paragraphs are detected per line, because real Chinese TXT files mix conventions inside one
 * chapter: indented narration, unindented dialogue, and hard-wrapped lines that continue the
 * paragraph before them.
 *
 * Wrapping evidence is decided once per chapter: a chapter is **wrapped** when indented prose lines
 * are at least a quarter of all prose lines. A wrapped export indents the first line of every
 * paragraph, so the ratio is 50% for two-line paragraphs, 33% for three-line, 25% for four-line —
 * all covered — while an unindented file with one or two stray indents stays far below the
 * threshold and keeps one paragraph per line. A single two-line paragraph (50%) is still joined.
 *
 * - **Unwrapped chapter** (indented lines below the quarter threshold): every non-blank line is its
 *   own paragraph, so a line-per-paragraph file and a dialogue run are never merged.
 * - **Wrapped chapter**: a new paragraph starts on a blank line, on an indented line, or after a
 *   line that ended a sentence (`。！？…；` or a closing quote/bracket). An unindented line whose
 *   predecessor did not end a sentence continues that paragraph, which joins hard-wrapped text
 *   without merging consecutive dialogue lines.
 *
 * `![alt](url)` becomes [NovelBlock.Image]; a line of one repeated `*`, `-`/`—`/`–` or `.`/`·`/`…`
 * becomes [NovelBlock.Separator]. Leading indentation is stripped so the reader controls wrapping.
 */
internal object SectionParser {

    private val IMAGE = Regex("^!\\[([^]]*)]\\(\\s*([^)\\s]+)\\s*\\)$")

    /** Characters that end a sentence or a dialogue line. */
    private const val SENTENCE_ENDERS = "。！？!?…；;：:”’\"'）》」』】"

    fun parse(body: String): List<NovelBlock> {
        val lines = body.split('\n').map { it.removeSuffix("\r") }
        val wrapped = hasWrappingEvidence(lines)

        val blocks = mutableListOf<NovelBlock>()
        val paragraph = StringBuilder()
        var previousLineWasBlank = true
        var previousEndedSentence = true

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += NovelBlock.Paragraph(paragraph.toString())
                paragraph.clear()
            }
        }

        for (withoutCr in lines) {
            val line = ChapterHeadingDetector.trimProseIndent(withoutCr)
            if (line.isEmpty()) {
                flushParagraph()
                previousLineWasBlank = true
                previousEndedSentence = true
                continue
            }

            val image = IMAGE.matchEntire(line)
            if (image != null) {
                flushParagraph()
                blocks += NovelBlock.Image(
                    ImageReference(
                        source = image.groupValues[2],
                        altText = image.groupValues[1].ifEmpty { null },
                    ),
                )
                previousLineWasBlank = false
                previousEndedSentence = true
                continue
            }

            val style = separatorStyle(line)
            if (style != null) {
                flushParagraph()
                blocks += NovelBlock.Separator(style)
                previousLineWasBlank = false
                previousEndedSentence = true
                continue
            }

            val startsNewParagraph = when {
                paragraph.isEmpty() -> true
                !wrapped -> true
                previousLineWasBlank -> true
                startsWithIndent(withoutCr) -> true
                previousEndedSentence -> true
                else -> false
            }
            if (startsNewParagraph) {
                flushParagraph()
                paragraph.append(line)
            } else {
                paragraph.append(joinSeparator(paragraph.last(), line.first())).append(line)
            }
            previousLineWasBlank = false
            previousEndedSentence = endsSentence(line)
        }
        flushParagraph()
        return blocks
    }

    /**
     * `true` when indented prose lines are at least a quarter of the prose lines.
     *
     * A wrapped export indents the first line of every paragraph, giving 50% for two-line
     * paragraphs, 33% for three-line and 25% for four-line, so the quarter threshold covers typical
     * column widths. An otherwise unindented file with a few stray indents stays below it, which
     * matters because treating a stray indent as wrapping would merge every paragraph in the
     * chapter.
     */
    private fun hasWrappingEvidence(lines: List<String>): Boolean {
        var prose = 0
        var indented = 0
        for (raw in lines) {
            val trimmed = ChapterHeadingDetector.trimProseIndent(raw)
            if (trimmed.isEmpty() || IMAGE.matches(trimmed) || separatorStyle(trimmed) != null) continue
            prose++
            if (startsWithIndent(raw)) indented++
        }
        return indented > 0 && indented * 4 >= prose
    }

    /**
     * `true` when [line] clearly ends a sentence, so the next unindented line is a new paragraph.
     *
     * Ending punctuation and closing quotes/brackets are both accepted, and a trailing ellipsis with
     * dialogue (`……”`) counts, which is what separates consecutive dialogue lines.
     */
    private fun endsSentence(line: String): Boolean {
        val last = line.lastOrNull() ?: return true
        return last in SENTENCE_ENDERS
    }

    /**
     * `true` when [line] begins with a real paragraph indent.
     *
     * Accepts an ideographic space (U+3000), a tab, or two or more ASCII spaces. A single ASCII
     * space is not treated as an indent because line-layout files occasionally carry one stray
     * leading space, and treating that as indentation would merge every paragraph in the chapter.
     */
    private fun startsWithIndent(line: String): Boolean = when {
        line.isEmpty() -> false
        line[0] == '\u3000' || line[0] == '\t' -> true
        line.length >= 2 && line[0] == ' ' && line[1] == ' ' -> true
        else -> false
    }

    /** `true` when [char] is a Han character or CJK punctuation, where no joining space is wanted. */
    private fun isCjk(char: Char): Boolean {
        val code = char.code
        return code in 0x2E80..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0xF900..0xFAFF ||
            code in 0x3000..0x303F ||
            code in 0xFF00..0xFFEF
    }

    private fun joinSeparator(left: Char, right: Char): String =
        if (isCjk(left) || isCjk(right)) "" else " "

    private fun separatorStyle(line: String): SeparatorStyle? {
        if (line.length < 3) return null
        val first = line[0]
        for (char in line) {
            if (char != first) return null
        }
        return when (first) {
            '*' -> SeparatorStyle.ASTERISKS
            '-', '—', '–' -> SeparatorStyle.DASHES
            '.', '·', '…' -> SeparatorStyle.DOTS
            else -> null
        }
    }
}
