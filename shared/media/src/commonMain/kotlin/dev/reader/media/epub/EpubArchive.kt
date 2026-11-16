package dev.reader.media.epub

import com.fleeksoft.ksoup.Ksoup
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/**
 * Root file declared by an EPUB `META-INF/container.xml`.
 */
data class EpubContainer(
    val packageDocumentPath: String,
    val packageDocumentMediaType: String?,
)

/**
 * Minimal read-only EPUB archive backed by Okio's zip [FileSystem].
 *
 * This is the skeleton the local novel importer grows from: it resolves the
 * package document through `META-INF/container.xml` and exposes raw entry reads.
 * Parsing the package document, spine, manifest and chapter text comes later.
 */
class EpubArchive private constructor(
    private val zipFileSystem: FileSystem,
    val container: EpubContainer,
) {
    /** Reads a zip entry as UTF-8 text. [entryPath] is zip-root relative, e.g. `META-INF/container.xml`. */
    fun readText(entryPath: String): String =
        zipFileSystem.read(entryPath.toZipPath()) { readUtf8() }

    /** Reads a zip entry as bytes. */
    fun readBytes(entryPath: String): ByteArray =
        zipFileSystem.read(entryPath.toZipPath()) { readByteArray() }

    /** The package document referenced by the container, as UTF-8 text. */
    fun readPackageDocument(): String = readText(container.packageDocumentPath)

    companion object {
        /** Entry name mandated by the EPUB 3 container specification. */
        const val CONTAINER_ENTRY: String = "META-INF/container.xml"

        /**
         * Opens [epubPath] on [fileSystem] and resolves its container root file.
         *
         * @throws IllegalStateException when the container is missing or declares no root file.
         */
        fun open(fileSystem: FileSystem, epubPath: Path): EpubArchive {
            val zipFileSystem = fileSystem.openZip(epubPath)
            val containerXml = zipFileSystem.read(CONTAINER_ENTRY.toZipPath()) { readUtf8() }
            val rootFile = Ksoup.parseXml(containerXml).selectFirst("rootfile")
                ?: error("$epubPath declares no rootfile in $CONTAINER_ENTRY")
            val fullPath = rootFile.attr("full-path")
                .takeIf { it.isNotBlank() }
                ?: error("$epubPath has a rootfile without full-path")
            return EpubArchive(
                zipFileSystem = zipFileSystem,
                container = EpubContainer(
                    packageDocumentPath = fullPath,
                    packageDocumentMediaType = rootFile.attr("media-type"),
                ),
            )
        }
    }
}

/** Zip entries live under the archive root. */
private fun String.toZipPath(): Path = ("/" + trimStart('/')).toPath()
