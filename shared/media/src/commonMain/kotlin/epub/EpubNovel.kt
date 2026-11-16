package app.koyura.media.epub

import app.koyura.media.novel.LocalNovelLimits
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelChapter
import app.koyura.media.novel.NovelClosedException
import app.koyura.media.novel.NovelImport
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.NovelMetadata
import app.koyura.media.novel.NovelResource
import app.koyura.media.novel.NovelFingerprints
import app.koyura.media.novel.PositionResolution
import app.koyura.media.novel.ReadingPosition
import app.koyura.media.novel.ReadingPositionResolver
import app.koyura.media.novel.ReadingPositions
import app.koyura.media.novel.TextEncoding
import app.koyura.media.novel.TocEntry
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/**
 * Opens an EPUB 3 (and EPUB 2-compatible) novel from [path] on [fileSystem].
 *
 * Opening reads `META-INF/container.xml`, the OPF package document, `META-INF/encryption.xml` and the
 * nav document, each capped by [LocalNovelLimits.maxMetadataBytes]. Chapter XHTML and images are read
 * on demand, so a shelf can list thousands of books without holding their text in memory.
 *
 * When the package has no usable nav, a table of contents is built from the spine. That fallback does
 * read each spine document's `<title>` once, which is the documented exception to lazy open; the
 * normal nav path never reads chapter bodies.
 *
 * Reading order is strictly the OPF spine. Non-linear (`linear="no"`) items are auxiliary and are
 * excluded from the chapter sequence; if that filtering removes everything, the full spine is used
 * instead so the book stays readable.
 *
 * The returned [NovelImport] closes the underlying ZIP filesystem on [NovelImport.close], on success
 * and on every failure path.
 *
 * @throws NovelImportException.CorruptedArchive when the file is not a ZIP or an XML document is broken.
 * @throws NovelImportException.MissingEntry when container/OPF/nav/spine targets are absent.
 * @throws NovelImportException.UnsafeReference when the container or a package reference is unsafe.
 * @throws NovelImportException.EncryptedContent when spine, nav or cover content is encrypted.
 * @throws NovelImportException.LimitExceeded when a [LocalNovelLimits] cap is hit while opening.
 */
fun openEpubNovel(
    fileSystem: FileSystem,
    path: Path,
    limits: LocalNovelLimits = LocalNovelLimits.DEFAULT,
): NovelImport = EpubNovel.open(fileSystem, path, limits)

private const val CONTAINER_ENTRY = "META-INF/container.xml"
private const val ENCRYPTION_ENTRY = "META-INF/encryption.xml"

/** Stable chapter id prefix for EPUB documents. */
internal object EpubChapterIds {
    private const val PREFIX = "epub-"

    /**
     * Stable id for a chapter document (or synthetic group key).
     *
     * FNV-1a over the key keeps ids stable across opens and platforms, so a saved progress key stays
     * valid after the app restarts. Keys are archive paths, which are unique per spine document.
     */
    fun idFor(documentPath: String): String =
        PREFIX + fnv1a64(documentPath).toULong().toString(16).padStart(16, '0')

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
 * EPUB implementation of [NovelImport].
 *
 * The ZIP filesystem stays open for the lifetime of the instance so chapters and images can be read
 * lazily. [close] is idempotent: it clears parsed chapters and calls the ZIP filesystem's `close()`.
 * Okio opens and closes one handle per read, so nothing else is held; `close()` exists to make the
 * instance unusable and drop cached text, not to release a persistent descriptor.
 */
internal class EpubNovel private constructor(
    override val file: String,
    override val metadata: NovelMetadata,
    private val zipFileSystem: FileSystem,
    private val limits: LocalNovelLimits,
    private val chapters: List<ChapterEntry>,
    private val encrypted: Map<String, String?>,
    /** Central-directory index for size/CRC checks; `null` for legacy streamed archives. */
    private val zipIndex: ZipCentralDirectory,
    /** Where the table of contents came from, exposed for diagnostics and tests. */
    internal val tocSource: TocSource,
    /**
     * Content version derived from the central directory (path, size and CRC per spine document).
     *
     * EPUB files have no cheap whole-book text digest that would not defeat lazy opening, so the
     * version is a fingerprint of the archive's own integrity records. It is stable for identical
     * bytes and changes whenever any spine document is edited, because a CRC change implies a content
     * change.
     */
    override val contentVersion: String,
) : NovelImport {

    private var closed = false

    /**
     * Bounded LRU of parsed chapters.
     *
     * [LocalNovelLimits.maxCachedChapters] caps how many chapters stay in memory, so browsing a whole
     * book cannot accumulate its text. Access order is tracked by re-inserting on hit.
     */
    private val chapterCache = LinkedHashMap<String, NovelChapter>()

    /** EPUB XHTML is XML, whose encoding is self-declared; the decoded text is Unicode. */
    override val encoding: TextEncoding = TextEncoding.UTF_8

    override val tableOfContents: List<TocEntry> = buildTocEntries()

    /** Number of chapter documents still cached; `0` after [close]. Used by tests to prove release. */
    internal val cachedChapterCount: Int get() = chapterCache.size

    /** Number of chapter documents known to the book, regardless of close. */
    internal val chapterCount: Int get() = chapters.size

    /** Ordered spine documents, exposed for tests and diagnostics; group labels are omitted. */
    internal val chapterDocuments: List<String> get() = chapters.mapNotNull { it.documentPath }

    /** The parsed package document, exposed for tests and diagnostics. */
    internal lateinit var packageDocument: EpubPackage
        private set

    override fun readChapter(id: String): NovelChapter {
        checkOpen()
        chapterCache.remove(id)?.let { cached ->
            chapterCache[id] = cached
            return cached
        }
        val chapter = chapters.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("$id is not a chapter of $file")
        val result = if (chapter.documentPath == null) {
            // Group label: a heading-only entry, so a reader can show the volume title.
            NovelChapter(
                id = chapter.id,
                title = chapter.title,
                level = chapter.level,
                parentId = chapter.parentId,
                blocks = listOf(NovelBlock.Heading(level = 1, text = chapter.title)),
            )
        } else {
            ensureNotEncrypted(chapter.documentPath)
            val xhtml = readEntryText(chapter.documentPath, limits.maxChapterBytes, LimitKind.CHAPTER)
            val parsed = EpubXhtmlParser.parse(file, chapter.documentPath, xhtml)
            NovelChapter(
                id = chapter.id,
                title = chapter.title,
                level = chapter.level,
                parentId = chapter.parentId,
                blocks = parsed.blocks,
            )
        }
        chapterCache[id] = result
        while (chapterCache.size > limits.maxCachedChapters) {
            val eldest = chapterCache.keys.firstOrNull() ?: break
            chapterCache.remove(eldest)
        }
        return result
    }

    override fun positionOf(chapterId: String, blockIndex: Int, codePointOffset: Int): ReadingPosition {
        checkOpen()
        val chapter = readChapter(chapterId)
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
     * Re-locates a stored position without reading the whole book.
     *
     * Only the table of contents is consulted up front; a chapter body is read through [readChapter]
     * (and its cache) when a resolver rule actually needs it. An exact resume therefore touches just
     * the anchored chapter, so an oversized or broken later chapter cannot fail it.
     */
    override fun resolvePosition(stored: ReadingPosition): PositionResolution {
        checkOpen()
        return ReadingPositionResolver.resolve(
            chapterIds = chapters.map { it.id },
            contentVersion = contentVersion,
            stored = stored,
            loader = ReadingPositionResolver.ChapterLoader { chapterId ->
                readChapter(chapterId).blocks
            },
        )
    }

    override fun readResource(path: String): ByteArray {
        checkOpen()
        // Normalize the path so a different spelling cannot bypass the encryption map. A leading `/`
        // is treated as archive-root relative (the EPUB convention for these paths) rather than as an
        // absolute filesystem path; every other unsafe form is a MissingEntry after normalization.
        val candidate = path.trim().trimStart('/')
        val canonical = try {
            if (candidate.isEmpty()) {
                throw NovelImportException.UnsafeReference(file, path, "", "empty")
            }
            EpubPathResolver.resolveStructural(file, null, candidate)
        } catch (e: NovelImportException.UnsafeReference) {
            throw NovelImportException.MissingEntry(path = file, entry = path)
        }
        ensureNotEncrypted(canonical)
        return readEntryBytes(canonical, limits.maxResourceBytes)
    }

    override fun close() {
        if (closed) return
        closed = true
        chapterCache.clear()
        try {
            zipFileSystem.close()
        } catch (_: IOException) {
            // Best-effort: Okio's ZipFileSystem opens and closes a handle per read, so nothing else is
            // held. A close error cannot be acted on by the caller.
        }
    }

    private fun checkOpen() {
        if (closed) throw NovelClosedException(file)
    }

    // ---------------------------------------------------------------------------------------------
    // Table of contents
    // ---------------------------------------------------------------------------------------------

    private fun buildTocEntries(): List<TocEntry> =
        chapters.map { chapter ->
            TocEntry(
                id = chapter.id,
                title = chapter.title,
                level = chapter.level,
                parentId = chapter.parentId,
            )
        }

    /**
     * One table-of-contents entry.
     *
     * [documentPath] is `null` for a volume/group label that only groups spine documents and owns no
     * content; reading such an entry returns a heading-only chapter. [id] is unique within the book
     * even when the spine repeats a document.
     */
    internal data class ChapterEntry(
        val id: String,
        val title: String,
        val level: Int,
        val parentId: String?,
        val documentPath: String?,
    )

    /** Where the table of contents was derived from. */
    internal enum class TocSource {
        NAV,
        SPINE,
    }

    private fun ensureNotEncrypted(entry: String) {
        if (encrypted.containsKey(EpubPackageParser.UNRESOLVED_ENCRYPTION_KEY)) {
            // Some encrypted content could not be mapped to a path; fail closed for any read.
            throw NovelImportException.EncryptedContent(
                path = file,
                entry = entry,
                algorithm = encrypted[EpubPackageParser.UNRESOLVED_ENCRYPTION_KEY],
            )
        }
        // Presence in the map means encrypted; the value (algorithm) may legitimately be null when the
        // document omits EncryptionMethod. Never treat a null algorithm as "not encrypted".
        if (!encrypted.containsKey(entry)) return
        throw NovelImportException.EncryptedContent(
            path = file,
            entry = entry,
            algorithm = encrypted[entry],
        )
    }

    private enum class LimitKind(val wireName: String) {
        CHAPTER("chapter-bytes"),
        RESOURCE("resource-bytes"),
        METADATA("metadata-bytes"),
    }

    private fun readEntryText(entry: String, limit: Long, kind: LimitKind): String =
        decodeUtf8(readEntryBytesChecked(entry, limit, kind))

    private fun readEntryBytes(entry: String, limit: Long): ByteArray =
        readEntryBytesChecked(entry, limit, kind = LimitKind.RESOURCE)

    /**
     * Reads [entry], enforcing [limit] and verifying the archive's own size and CRC records.
     *
     * Three checks run:
     * 1. the central-directory size is compared with the limit **before** reading, so an oversized
     *    entry never allocates;
     * 2. the bytes actually read are checked against the central-directory size, which catches a
     *    deliberately-shrunk declaration that would otherwise silently truncate the body;
     * 3. stored entries are CRC-checked, so in-place corruption is reported instead of served.
     *
     * Decompression or CRC failure is a [NovelImportException.CorruptedArchive], not `MissingEntry`,
     * because the entry exists and its bytes are wrong.
     */
    private fun readEntryBytesChecked(entry: String, limit: Long, kind: LimitKind): ByteArray {
        // The index is complete (read from the EOCD-declared range), so an entry absent from it does
        // not exist in the archive. A present index is mandatory; its absence was rejected at open.
        val index = zipIndex.entry(entry)
            ?: throw NovelImportException.MissingEntry(path = file, entry = entry)
        if (index.uncompressedSize > limit) {
            throw NovelImportException.LimitExceeded(
                path = file,
                limit = kind.wireName,
                maximum = limit,
                actual = index.uncompressedSize,
                entry = entry,
            )
        }

        val zipPath = entry.toZipPath()
        val metadata = try {
            zipFileSystem.metadataOrNull(zipPath)
        } catch (e: IOException) {
            throw NovelImportException.Io(path = file, phase = "read", cause = e)
        } catch (e: Throwable) {
            throw NovelImportException.CorruptedArchive(file, entry, "entry-unreadable", e)
        }
        if (metadata == null || metadata.isDirectory) {
            throw NovelImportException.MissingEntry(path = file, entry = entry)
        }
        val declaredSize = metadata.size
        if (declaredSize != null && declaredSize > limit) {
            throw NovelImportException.LimitExceeded(
                path = file,
                limit = kind.wireName,
                maximum = limit,
                actual = declaredSize,
                entry = entry,
            )
        }

        val bytes = try {
            zipFileSystem.read(zipPath) { readByteArray() }
        } catch (e: IOException) {
            // The entry exists in the directory but could not be decompressed: a corrupted archive.
            throw NovelImportException.CorruptedArchive(file, entry, "decompression-failed", e)
        }
        if (bytes.size.toLong() > limit) {
            throw NovelImportException.LimitExceeded(
                path = file,
                limit = kind.wireName,
                maximum = limit,
                actual = bytes.size.toLong(),
                entry = entry,
            )
        }

        // Okio truncates a read to the declared uncompressed size, so a shrunk declaration yields a
        // short body with no error. Compare with the central directory to detect that.
        if (bytes.size.toLong() != index.uncompressedSize) {
            throw NovelImportException.CorruptedArchive(
                path = file,
                entry = entry,
                detail = "size-mismatch",
            )
        }
        // CRC covers the uncompressed bytes for every method, so a deflated entry swapped for a
        // same-length stream is caught here just like a corrupted stored entry.
        if (crc32(bytes) != index.crc32) {
            throw NovelImportException.CorruptedArchive(
                path = file,
                entry = entry,
                detail = "crc-mismatch",
            )
        }
        return bytes
    }

    companion object {
        /**
         * Opens an EPUB from an already-opened ZIP [archiveFileSystem] rooted at [displayPath].
         *
         * Internal seam for tests: Okio's zip filesystem opens the archive by path, so a delegate
         * cannot observe which entries were read. Passing a wrapper around the *archive* filesystem
         * makes entry-level lazy-loading observable. The caller owns [archiveFileSystem]; this
         * function closes it on failure and on [NovelImport.close].
         */
        internal fun openArchiveFileSystem(
            displayPath: String,
            archiveFileSystem: FileSystem,
            limits: LocalNovelLimits,
            zipIndex: ZipCentralDirectory,
        ): NovelImport = try {
            openWithZip(displayPath, archiveFileSystem, limits, zipIndex)
        } catch (e: Throwable) {
            closeQuietly(archiveFileSystem)
            throw e
        }

        fun open(fileSystem: FileSystem, path: Path, limits: LocalNovelLimits): NovelImport {
            val displayPath = path.toString()

            // Distinguish "cannot reach the file" from "the file is not a zip". A missing file, a
            // permission error or a directory path is an I/O failure the user can act on; only a
            // readable non-ZIP is a corrupted archive.
            val metadata = try {
                fileSystem.metadataOrNull(path)
            } catch (e: IOException) {
                throw NovelImportException.Io(path = displayPath, phase = "open", cause = e)
            }
            if (metadata == null) {
                throw NovelImportException.Io(
                    path = displayPath,
                    phase = "open",
                    cause = IOException("no such file: $displayPath"),
                )
            }
            if (metadata.isDirectory) {
                throw NovelImportException.Io(
                    path = displayPath,
                    phase = "open",
                    cause = IOException("is a directory: $displayPath"),
                )
            }

            val zip = try {
                fileSystem.openZip(path)
            } catch (e: IOException) {
                throw NovelImportException.CorruptedArchive(
                    path = displayPath,
                    entry = null,
                    detail = "not-a-zip",
                    cause = e,
                )
            } catch (e: Throwable) {
                // Some platforms surface a non-IOException for malformed containers.
                throw NovelImportException.CorruptedArchive(
                    path = displayPath,
                    entry = null,
                    detail = "not-a-zip",
                    cause = e,
                )
            }

            try {
                // The central directory is mandatory for EPUB: without it there is no way to verify
                // that a chapter body was not silently truncated or swapped, so refuse the book rather
                // than serve unverified text.
                val index = try {
                    ZipCentralDirectory.read(fileSystem, path)
                } catch (_: Throwable) {
                    null
                } ?: throw NovelImportException.CorruptedArchive(
                    path = displayPath,
                    entry = null,
                    detail = "no-central-directory",
                )
                return openWithZip(displayPath, zip, limits, index)
            } catch (e: Throwable) {
                closeQuietly(zip)
                throw e
            }
        }

        private fun openWithZip(
            displayPath: String,
            zip: FileSystem,
            limits: LocalNovelLimits,
            zipIndex: ZipCentralDirectory,
        ): NovelImport {
            // Metadata documents are read at open, so they use the metadata cap: a zip bomb that
            // inflates to hundreds of MiB must be a LimitExceeded, never an OutOfMemoryError.
            val containerXml = readMetadataText(displayPath, zip, zipIndex, CONTAINER_ENTRY, limits)
            val location = EpubPackageParser.parseContainer(displayPath, containerXml)
            val opfXml = readMetadataText(
                displayPath, zip, zipIndex, location.packageDocumentPath, limits,
            )
            val pkg = EpubPackageParser.parsePackage(displayPath, location.packageDocumentPath, opfXml)

            val encryptionXml = readOptionalMetadataText(
                displayPath, zip, zipIndex, ENCRYPTION_ENTRY, limits,
            )
            val encrypted = EpubPackageParser.parseEncryption(displayPath, encryptionXml)
            // A present encryption.xml with no EncryptedData (empty element, XML declaration only, or
            // comments only) is legal OCF; it means "nothing is encrypted". Only a document that fails
            // to parse is an error, and parseEncryption reports that separately.
            if (encryptionXml != null && EpubPackageParser.isMalformedEncryption(encryptionXml)) {
                throw NovelImportException.CorruptedArchive(
                    displayPath,
                    ENCRYPTION_ENTRY,
                    "invalid-encryption",
                )
            }
            if (encrypted.containsKey(EpubPackageParser.UNRESOLVED_ENCRYPTION_KEY)) {
                throw NovelImportException.EncryptedContent(
                    path = displayPath,
                    entry = ENCRYPTION_ENTRY,
                    algorithm = encrypted[EpubPackageParser.UNRESOLVED_ENCRYPTION_KEY],
                )
            }

            if (pkg.spine.isEmpty()) {
                throw NovelImportException.CorruptedArchive(displayPath, pkg.opfPath, "empty-spine")
            }
            val readingSpine = pkg.spine.filter { !it.isAuxiliary }.ifEmpty { pkg.spine }
            if (readingSpine.size > limits.maxChapterCount) {
                throw NovelImportException.LimitExceeded(
                    path = displayPath,
                    limit = "chapter-count",
                    maximum = limits.maxChapterCount.toLong(),
                    actual = readingSpine.size.toLong(),
                )
            }

            val documents = readingSpine.map { item ->
                pkg.manifest[item.idref]
                    ?: throw NovelImportException.MissingEntry(displayPath, item.idref, pkg.opfPath)
            }
            documents.forEach { document ->
                // Fail at open time when a spine document needed for reading is encrypted.
                if (encrypted.containsKey(document.path)) {
                    throw NovelImportException.EncryptedContent(
                        path = displayPath,
                        entry = document.path,
                        algorithm = encrypted[document.path],
                    )
                }
                // Fail at open time when a spine/manifest target is absent, so a broken book is
                // reported when opened rather than halfway through reading.
                val metadata = zip.metadataOrNull(document.path.toZipPath())
                if (metadata == null || metadata.isDirectory) {
                    throw NovelImportException.MissingEntry(
                        path = displayPath,
                        entry = document.path,
                        declaredBy = pkg.opfPath,
                    )
                }
            }

            val cover = pkg.cover
            cover?.let { item ->
                if (encrypted.containsKey(item.path)) {
                    throw NovelImportException.EncryptedContent(
                        displayPath, item.path, encrypted[item.path],
                    )
                }
            }

            val (chapters, tocSource) = buildChapters(
                displayPath = displayPath,
                zip = zip,
                zipIndex = zipIndex,
                pkg = pkg,
                documents = documents.map { it.path },
                encrypted = encrypted,
                limits = limits,
            )

            val metadata = NovelMetadata(
                title = pkg.title.ifBlank { derivedTitle(displayPath) },
                authors = pkg.authors,
                language = pkg.language,
                cover = cover?.let { item -> NovelResource(path = item.path, mediaType = item.mediaType) },
            )

            // Version from the archive's own records: cheap, stable for identical bytes, and changes
            // whenever a spine document's content changes (a different CRC).
            val versionSource = buildString {
                for (document in documents) {
                    append(document.path).append('\u0000')
                    val entry = zipIndex.entry(document.path)
                    append(entry?.uncompressedSize ?: -1L).append('\u0001')
                    append(entry?.crc32 ?: -1L).append('\u0002')
                }
            }

            return EpubNovel(
                file = displayPath,
                metadata = metadata,
                zipFileSystem = zip,
                limits = limits,
                chapters = chapters,
                encrypted = encrypted,
                zipIndex = zipIndex,
                tocSource = tocSource,
                contentVersion = NovelFingerprints.content(versionSource),
            ).also { it.packageDocument = pkg }
        }

        /**
         * Builds the chapter list.
         *
         * Reading order is always the spine: one chapter per spine document, in spine order, so no
         * content can be skipped and a repeated `itemref` still yields unique ids. The nav supplies
         * titles and volume grouping labels on top of that order.
         *
         * When the nav is missing, unreadable or yields no readable entry, the table of contents comes
         * from the spine. That fallback reads each spine document's `<title>` once, which is the one
         * documented exception to lazy opening; the nav path never reads chapter bodies.
         */
        private fun buildChapters(
            displayPath: String,
            zip: FileSystem,
            zipIndex: ZipCentralDirectory,
            pkg: EpubPackage,
            documents: List<String>,
            encrypted: Map<String, String?>,
            limits: LocalNovelLimits,
        ): Pair<List<ChapterEntry>, TocSource> {
            val navItem = pkg.navItem
            if (navItem != null) {
                if (encrypted.containsKey(navItem.path)) {
                    throw NovelImportException.EncryptedContent(
                        displayPath, navItem.path, encrypted[navItem.path],
                    )
                }
                val navXml = readOptionalMetadataText(
                    displayPath, zip, zipIndex, navItem.path, limits,
                )
                if (navXml != null) {
                    val navEntries = EpubNavParser.parse(
                        archivePath = displayPath,
                        navPath = navItem.path,
                        navXml = navXml,
                        readableDocuments = documents.toSet(),
                    )
                    if (navEntries.any { it.level == 1 }) {
                        return navChapters(documents, navEntries) to TocSource.NAV
                    }
                }
            }
            return spineChapters(displayPath, zip, zipIndex, documents, limits) to TocSource.SPINE
        }

        /**
         * Merges nav data onto the spine order.
         *
         * Hierarchy comes from nav nesting, not from the order of labels: each readable nav entry
         * carries its `groupPath`, so a chapter links to the group entry that actually encloses it and
         * a chapter outside any group has no parent. Group entries are emitted immediately before the
         * first spine document that belongs to them, and two groups with the same title remain two
         * distinct entries because their ids include their position.
         */
        private fun navChapters(documents: List<String>, navEntries: List<EpubNavParser.NavEntry>): List<ChapterEntry> {
            // Title per document (first nav mention wins) and the group chain per document.
            val titleByDocument = LinkedHashMap<String, String>()
            val groupPathByDocument = LinkedHashMap<String, List<EpubNavParser.NavGroup>>()
            for (entry in navEntries) {
                if (entry.level == 1 && entry.path.isNotEmpty()) {
                    if (entry.path !in titleByDocument) titleByDocument[entry.path] = entry.title
                    if (entry.path !in groupPathByDocument) {
                        groupPathByDocument[entry.path] = entry.groupPath
                    }
                }
            }

            val entries = mutableListOf<ChapterEntry>()
            val occurrenceByDocument = mutableMapOf<String, Int>()
            // Group entries already emitted, keyed by their full path so identical labels at different
            // positions stay separate.
            val groupIdByPath = LinkedHashMap<List<EpubNavParser.NavGroup>, String>()

            for (document in documents) {
                val occurrence = occurrenceByDocument[document] ?: 0
                occurrenceByDocument[document] = occurrence + 1

                // Emit any not-yet-emitted ancestors of this document's group chain, shallowest first,
                // so the TOC shows groups before their chapters.
                val chain = groupPathByDocument[document].orEmpty()
                var parentId: String? = null
                for (depth in 1..chain.size) {
                    val prefix = chain.subList(0, depth)
                    val groupId = groupIdByPath.getOrPut(prefix) {
                        val key = prefix.joinToString("\u0000") { "${it.ordinal}:${it.title}" }
                        val id = EpubChapterIds.idFor("group|$key|${entries.size}")
                        entries += ChapterEntry(
                            id = id,
                            title = prefix.last().title,
                            level = 0,
                            parentId = parentId,
                            documentPath = null,
                        )
                        id
                    }
                    parentId = groupId
                }

                val title = titleByDocument[document] ?: derivedTitle(document)
                entries += ChapterEntry(
                    id = EpubChapterIds.idFor("chapter|$document|$occurrence"),
                    title = title,
                    level = 1,
                    parentId = parentId,
                    documentPath = document,
                )
            }
            return entries
        }

        /** Title used when the nav does not name a spine document; derived without reading it. */
        private fun derivedTitle(document: String): String {
            val name = document.substringAfterLast('/')
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            return base.ifBlank { document }
        }

        /**
         * Spine-only table of contents.
         *
         * Each document's own `<title>` (or first heading) is used when the document can be read within
         * the chapter limit; otherwise the title falls back to the file name. This is the only place
         * that reads chapter bodies during open, and it happens only when nav cannot supply a TOC.
         */
        private fun spineChapters(
            displayPath: String,
            zip: FileSystem,
            zipIndex: ZipCentralDirectory,
            documents: List<String>,
            limits: LocalNovelLimits,
        ): List<ChapterEntry> {
            val occurrences = mutableMapOf<String, Int>()
            return documents.map { document ->
                val occurrence = occurrences[document] ?: 0
                occurrences[document] = occurrence + 1
                val title = readDocumentTitle(displayPath, zip, zipIndex, document, limits)
                    ?: derivedTitle(document)
                ChapterEntry(
                    id = EpubChapterIds.idFor("chapter|$document|$occurrence"),
                    title = title,
                    level = 1,
                    parentId = null,
                    documentPath = document,
                )
            }
        }

        private fun readDocumentTitle(
            displayPath: String,
            zip: FileSystem,
            zipIndex: ZipCentralDirectory,
            document: String,
            limits: LocalNovelLimits,
        ): String? {
            val xhtml = try {
                decodeUtf8(
                    readEntryBytesChecked(
                        displayPath, zip, zipIndex, document, limits.maxChapterBytes, LimitKind.CHAPTER,
                    ),
                )
            } catch (_: NovelImportException) {
                return null
            }
            return EpubXhtmlParser.parse(displayPath, document, xhtml).fallbackTitle
        }

        /** Reads a bounded metadata document, reporting missing entries normally. */
        private fun readMetadataText(
            displayPath: String,
            zip: FileSystem,
            zipIndex: ZipCentralDirectory,
            entry: String,
            limits: LocalNovelLimits,
        ): String = decodeUtf8(
            readEntryBytesChecked(displayPath, zip, zipIndex, entry, limits.maxMetadataBytes, LimitKind.METADATA),
        )

        /** Reads an optional bounded metadata document; `null` only when it is absent. */
        private fun readOptionalMetadataText(
            displayPath: String,
            zip: FileSystem,
            zipIndex: ZipCentralDirectory,
            entry: String,
            limits: LocalNovelLimits,
        ): String? {
            val metadata = try {
                zip.metadataOrNull(entry.toZipPath())
            } catch (_: IOException) {
                null
            } ?: return null
            if (metadata.isDirectory) return null
            return readMetadataText(displayPath, zip, zipIndex, entry, limits)
        }

        /**
         * Reads and verifies an entry against [limit], the central-directory size and the stored CRC.
         *
         * See [EpubNovel.readEntryBytesChecked] for the three checks; this companion version exists so
         * open-time reads share exactly the same rules.
         */
        private fun readEntryBytesChecked(
            displayPath: String,
            zip: FileSystem,
            zipIndex: ZipCentralDirectory,
            entry: String,
            limit: Long,
            kind: LimitKind,
        ): ByteArray {
            val index = zipIndex.entry(entry)
                ?: throw NovelImportException.MissingEntry(path = displayPath, entry = entry)
            if (index.uncompressedSize > limit) {
                throw NovelImportException.LimitExceeded(
                    path = displayPath,
                    limit = kind.wireName,
                    maximum = limit,
                    actual = index.uncompressedSize,
                    entry = entry,
                )
            }

            val zipPath = entry.toZipPath()
            val metadata = try {
                zip.metadataOrNull(zipPath)
            } catch (e: IOException) {
                throw NovelImportException.Io(path = displayPath, phase = "read", cause = e)
            } catch (e: Throwable) {
                throw NovelImportException.CorruptedArchive(displayPath, entry, "entry-unreadable", e)
            }
            if (metadata == null || metadata.isDirectory) {
                throw NovelImportException.MissingEntry(path = displayPath, entry = entry)
            }
            val declaredSize = metadata.size
            if (declaredSize != null && declaredSize > limit) {
                throw NovelImportException.LimitExceeded(
                    path = displayPath,
                    limit = kind.wireName,
                    maximum = limit,
                    actual = declaredSize,
                    entry = entry,
                )
            }
            val bytes = try {
                zip.read(zipPath) { readByteArray() }
            } catch (e: IOException) {
                throw NovelImportException.CorruptedArchive(displayPath, entry, "decompression-failed", e)
            }
            if (bytes.size.toLong() > limit) {
                throw NovelImportException.LimitExceeded(
                    path = displayPath,
                    limit = kind.wireName,
                    maximum = limit,
                    actual = bytes.size.toLong(),
                    entry = entry,
                )
            }
            if (bytes.size.toLong() != index.uncompressedSize) {
                throw NovelImportException.CorruptedArchive(displayPath, entry, "size-mismatch")
            }
            // CRC covers uncompressed bytes for every method; a same-length swap is caught here.
            if (crc32(bytes) != index.crc32) {
                throw NovelImportException.CorruptedArchive(displayPath, entry, "crc-mismatch")
            }
            return bytes
        }

        /**
         * Decodes XML text as UTF-8, stripping a BOM.
         *
         * XHTML and OPF are XML, so their declared encodings are UTF-8/UTF-16 and the practical case
         * is UTF-8. Malformed text is reported as a corrupted entry rather than as an encoding
         * problem, because it means the archive content is not valid XML text.
         */
        private fun decodeUtf8(bytes: ByteArray): String {
            var start = 0
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            ) {
                start = 3
            }
            return bytes.decodeToString(start, bytes.size)
        }

        private fun closeQuietly(zip: FileSystem) {
            try {
                zip.close()
            } catch (_: IOException) {
                // Best-effort on failure paths; the original exception is more useful.
            }
        }
    }
}

/** Zip entries live under the archive root. */
private fun String.toZipPath(): Path = ("/" + trimStart('/')).toPath()

/**
 * CRC-32 (IEEE 802.3), matching the value stored in a ZIP central directory.
 *
 * Implemented locally because the common Kotlin stdlib has no CRC and Okio does not expose ZIP CRCs.
 * It runs on already-in-memory byte arrays of every compression method, so stored and deflated
 * entries are both verified.
 */
private val CRC32_TABLE: IntArray = IntArray(256) { index ->
    var value = index
    repeat(8) {
        value = if (value and 1 != 0) (value ushr 1) xor 0xEDB88320.toInt() else value ushr 1
    }
    value
}

private fun crc32(bytes: ByteArray): Long {
    var crc = -1
    for (byte in bytes) {
        crc = (crc ushr 8) xor CRC32_TABLE[(crc xor byte.toInt()) and 0xFF]
    }
    return (crc.inv().toLong() and 0xFFFFFFFFL)
}
