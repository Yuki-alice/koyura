package app.koyura.data.downloads

import app.koyura.domain.extension.ComicPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.buffer

@Serializable
internal data class ContentPage(val locator: String, val imageDigest: String, val size: Long, val checksum: String)

@Serializable
internal data class ChapterManifest(val version: Int, val jobId: String, val pages: List<ContentPage>) {
    fun comicPages(): List<ComicPage> = pages.mapIndexed { index, page ->
        ComicPage(page.locator, index, page.locator)
    }
}

/** A chapter is visible only once the entire temporary package has been atomically renamed. */
internal class ChapterContentPackages(
    private val files: FileSystem,
    private val directory: Path,
) {
    fun prepare(jobId: String) {
        files.createDirectories(directory)
        discardTemporary(jobId)
        files.createDirectory(temporary(jobId))
    }

    fun writePage(jobId: String, page: ComicPage, bytes: ByteArray): ContentPage {
        require(bytes.isNotEmpty()) { "A downloaded page was empty" }
        files.write(temporary(jobId) / pageFile(page.index)) { write(bytes) }
        return ContentPage(
            locator = locator(page.pageKey),
            imageDigest = locator(page.imageKey),
            size = bytes.size.toLong(),
            checksum = bytes.toByteString().sha256().hex(),
        )
    }

    fun commit(jobId: String, pages: List<ContentPage>) {
        check(pages.isNotEmpty()) { "A downloaded chapter has no pages" }
        val manifest = ChapterManifest(VERSION, jobId, pages)
        files.write(temporary(jobId) / MANIFEST_NAME) { writeUtf8(Json.encodeToString(manifest)) }
        check(readManifest(temporary(jobId), jobId) == manifest && pages.indices.all {
            verifies(temporary(jobId), it, pages[it])
        }) { "Could not verify chapter content package" }
        files.atomicMove(temporary(jobId), committed(jobId))
    }

    fun verifiedManifest(jobId: String): ChapterManifest? =
        manifest(jobId)?.takeIf { manifest ->
            manifest.pages.indices.all { verifies(committed(jobId), it, manifest.pages[it]) }
        }

    fun manifest(jobId: String): ChapterManifest? = readManifest(committed(jobId), jobId)

    fun readPage(jobId: String, key: String): ByteArray? {
        val manifest = manifest(jobId) ?: return null
        val digest = if (key.startsWith(LOCATOR_PREFIX)) key else locator(key)
        val index = manifest.pages.indexOfFirst { it.locator == digest || it.imageDigest == digest }
        if (index < 0) return null
        val page = manifest.pages[index]
        val path = committed(jobId) / pageFile(index)
        return runCatching {
            if (files.metadata(path).size != page.size) return@runCatching null
            files.read(path) { readByteArray(page.size) }.takeIf { it.toByteString().sha256().hex() == page.checksum }
        }.getOrNull()
    }

    fun discardTemporary(jobId: String) {
        files.deleteRecursively(temporary(jobId), mustExist = false)
    }

    fun discardCommitted(jobId: String) {
        files.deleteRecursively(committed(jobId), mustExist = false)
    }

    fun discardOrphans(knownJobs: Set<String>) {
        for (path in files.listOrNull(directory).orEmpty()) {
            val jobId = path.name.removeSuffix(TEMP_SUFFIX)
            if (jobId.matches(JOB_ID) && jobId !in knownJobs) files.deleteRecursively(path, mustExist = false)
        }
    }

    private fun verifies(packagePath: Path, index: Int, page: ContentPage): Boolean = runCatching {
        val path = packagePath / pageFile(index)
        if (files.metadata(path).size != page.size) return@runCatching false
        HashingSource.sha256(files.source(path)).use { source ->
            source.buffer().skip(page.size)
            source.hash.hex() == page.checksum
        }
    }.getOrDefault(false)

    private fun readManifest(packagePath: Path, jobId: String): ChapterManifest? = runCatching {
        val path = packagePath / MANIFEST_NAME
        val size = files.metadata(path).size ?: return@runCatching null
        if (size <= 0 || size > MAX_MANIFEST_BYTES) return@runCatching null
        files.read(path) { Json.decodeFromString<ChapterManifest>(readUtf8(size)) }.takeIf { manifest ->
            manifest.version == VERSION && manifest.jobId == jobId &&
                manifest.pages.isNotEmpty() && manifest.pages.map { it.locator }.toSet().size == manifest.pages.size &&
                manifest.pages.all { it.size > 0 && it.locator.startsWith(LOCATOR_PREFIX) }
        }
    }.getOrNull()

    private fun pageFile(index: Int) = "$index.page"

    private fun committed(jobId: String): Path {
        require(jobId.matches(JOB_ID))
        return directory / jobId
    }

    private fun temporary(jobId: String): Path {
        require(jobId.matches(JOB_ID))
        return directory / "$jobId$TEMP_SUFFIX"
    }

    companion object {
        const val LOCATOR_PREFIX = "offline:"
        private const val TEMP_SUFFIX = ".partial"
        private const val MANIFEST_NAME = "chapter.json"
        private const val VERSION = 1
        private const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024
        private val JOB_ID = Regex("[0-9a-f-]{36}")
        fun locator(key: String) = LOCATOR_PREFIX + key.encodeUtf8().sha256().hex()
    }
}
