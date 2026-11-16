package app.koyura.media.epub

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import app.koyura.media.novel.NovelImportException

/**
 * Parses the three XML documents an EPUB needs before any chapter is read:
 * `META-INF/container.xml`, the OPF package document, and `META-INF/encryption.xml`.
 *
 * Ksoup exposes namespace-prefixed tags and attributes literally (`dc:title`, `epub:type`), so the
 * parsers select on those literal names instead of a namespace API. Every reference read here is
 * routed through [EpubPathResolver], which means a package document that points outside the archive
 * fails at open time rather than at read time.
 */
internal object EpubPackageParser {

    /** Refers to the OPF path when a manifest/spine element is broken, for error messages. */
    private const val CONTAINER_ENTRY = "META-INF/container.xml"

    /**
     * Key used in the encryption map when a CipherReference URI cannot be normalized.
     *
     * Its presence does not name a readable entry; it is a sentinel that tells the importer "some
     * content is encrypted but we cannot tell which", so it must fail closed rather than serve bytes.
     */
    const val UNRESOLVED_ENCRYPTION_KEY: String = "\u0000unresolved-encryption"

    /**
     * Reads the rootfile declared by [containerXml].
     *
     * @throws NovelImportException.CorruptedArchive when the XML has no usable rootfile.
     * @throws NovelImportException.UnsafeReference when the declared full-path is not archive-relative.
     */
    fun parseContainer(archivePath: String, containerXml: String): EpubPackageLocation {
        val document = try {
            Ksoup.parseXml(containerXml)
        } catch (e: Throwable) {
            throw NovelImportException.CorruptedArchive(
                path = archivePath,
                entry = CONTAINER_ENTRY,
                detail = "invalid-xml",
                cause = e,
            )
        }
        val rootFiles = elementsNamed(document, "rootfile")
        if (rootFiles.isEmpty()) {
            throw NovelImportException.CorruptedArchive(
                path = archivePath,
                entry = CONTAINER_ENTRY,
                detail = "no-rootfile",
            )
        }

        // Prefer a rootfile whose media-type is the package document, then fall back to any rootfile
        // that has a usable full-path. A malformed first rootfile must not hide a later good one.
        val packageMediaType = "application/oebps-package+xml"
        val ordered = rootFiles.sortedBy { rootFile ->
            if (rootFile.attr("media-type").trim().equals(packageMediaType, ignoreCase = true)) 0 else 1
        }
        var firstFailure: NovelImportException? = null
        for (rootFile in ordered) {
            val fullPath = rootFile.attr("full-path").trim()
            if (fullPath.isEmpty()) {
                if (firstFailure == null) {
                    firstFailure = NovelImportException.CorruptedArchive(
                        path = archivePath,
                        entry = CONTAINER_ENTRY,
                        detail = "rootfile-without-full-path",
                    )
                }
                continue
            }
            val normalized = try {
                EpubPathResolver.resolveStructural(archivePath, null, fullPath)
            } catch (e: NovelImportException.UnsafeReference) {
                if (firstFailure == null) firstFailure = e
                continue
            }
            return EpubPackageLocation(
                packageDocumentPath = normalized,
                mediaType = rootFile.attr("media-type").takeIf { it.isNotBlank() },
            )
        }
        throw firstFailure ?: NovelImportException.CorruptedArchive(
            path = archivePath,
            entry = CONTAINER_ENTRY,
            detail = "no-usable-rootfile",
        )
    }

    /**
     * Parses the OPF document at [opfPath].
     *
     * `manifest` hrefs are resolved against the OPF directory, so a manifest item becomes a
     * normalized archive path. Manifest entries that are unusable (blank id or href, or a reference
     * that escapes the archive) are skipped rather than fatal, because one bad entry must not block a
     * whole book; the spine still validates its own idrefs. A spine idref with no manifest item is
     * reported as [NovelImportException.MissingEntry].
     */
    fun parsePackage(archivePath: String, opfPath: String, opfXml: String): EpubPackage {
        val document = try {
            Ksoup.parseXml(opfXml)
        } catch (e: Throwable) {
            throw NovelImportException.CorruptedArchive(
                path = archivePath,
                entry = opfPath,
                detail = "invalid-xml",
                cause = e,
            )
        }

        // Metadata is required by the EPUB spec, but a book with a broken metadata block is still
        // readable; only the fields are missing. Refusing it would lose content for no safety gain.
        val metadata = elementsNamed(document, "metadata").firstOrNull()
        val title = metadata?.let { textOf(it, "title") } ?: ""
        val authors = metadata
            ?.let { elementsNamed(it, "creator").map { creator -> creator.text().trim() } }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val language = metadata?.let { textOf(it, "language") }

        val manifest = LinkedHashMap<String, EpubManifestItem>()
        val manifestElement = elementsNamed(document, "manifest").firstOrNull()
        for (item in manifestElement?.let { elementsNamed(it, "item") } ?: emptyList()) {
            val id = item.attr("id").trim()
            val href = item.attr("href").trim()
            if (id.isEmpty() || href.isEmpty()) continue
            val resolved = try {
                EpubPathResolver.resolveStructural(archivePath, opfPath, href)
            } catch (_: NovelImportException.UnsafeReference) {
                continue
            }
            manifest[id] = EpubManifestItem(
                id = id,
                path = resolved,
                mediaType = item.attr("media-type").takeIf { it.isNotBlank() },
                properties = item.attr("properties").split(' ').filter { it.isNotEmpty() }.toSet(),
            )
        }

        val spine = ArrayList<EpubSpineItem>()
        val spineElement = elementsNamed(document, "spine").firstOrNull()
        for (itemref in spineElement?.let { elementsNamed(it, "itemref") } ?: emptyList()) {
            val idref = itemref.attr("idref").trim()
            if (idref.isEmpty()) continue
            if (idref !in manifest) {
                throw NovelImportException.MissingEntry(
                    path = archivePath,
                    entry = idref,
                    declaredBy = opfPath,
                )
            }
            spine += EpubSpineItem(
                idref = idref,
                linear = !itemref.attr("linear").equals("no", ignoreCase = true),
            )
        }

        val coverImage = manifest.values.firstOrNull { it.hasProperty("cover-image") }
        val metaElements = elementsNamed(document, "meta")
        val coverMeta = metaElements
            .firstOrNull { it.attr("name").equals("cover", ignoreCase = true) }
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val cover = coverImage ?: coverMeta?.let { manifest[it] }

        val navItem = manifest.values.firstOrNull { it.hasProperty("nav") }

        return EpubPackage(
            opfPath = opfPath,
            version = elementsNamed(document, "package").firstOrNull()?.attr("version")?.takeIf { it.isNotBlank() },
            title = title,
            authors = authors,
            language = language,
            manifest = manifest,
            spine = spine,
            cover = cover,
            navItem = navItem,
            coverMetaTarget = if (coverImage == null) coverMeta else null,
        )
    }

    /**
     * Parses `META-INF/encryption.xml` into archive paths and their declared algorithm.
     *
     * Presence in the returned map means "this entry is encrypted"; the value is the declared
     * algorithm **or `null` when `EncryptionMethod`/`Algorithm` is absent**. Callers must test key
     * presence (`containsKey`), never the value, otherwise an algorithm-less entry would be treated as
     * plaintext.
     *
     * An `EncryptedData` that cannot be mapped to a concrete archive path — no `CipherReference`, an
     * empty `URI`, a missing `CipherData`, or a URI that normalizes to nothing — is recorded under
     * [UNRESOLVED_ENCRYPTION_KEY]. The importer then fails closed for every read, because it cannot
     * know which content is ciphertext.
     */
    fun parseEncryption(archivePath: String, encryptionXml: String?): Map<String, String?> {
        if (encryptionXml == null || encryptionXml.isBlank()) return emptyMap()
        val document = try {
            Ksoup.parseXml(encryptionXml)
        } catch (_: Throwable) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, String?>()
        // Ksoup keeps namespace prefixes literally (`enc:EncryptedData`), so match by local name.
        for (encryptedData in elementsNamed(document, "EncryptedData")) {
            val algorithm = elementsNamed(encryptedData, "EncryptionMethod")
                .firstOrNull()
                ?.attr("Algorithm")
                ?.takeIf { it.isNotBlank() }
            val cipherReference = elementsNamed(encryptedData, "CipherReference").firstOrNull()
            val cipherData = elementsNamed(encryptedData, "CipherData").firstOrNull()
            val uri = cipherReference?.attr("URI")?.trim()
            if (cipherData == null || uri.isNullOrEmpty()) {
                // Declared encrypted but no usable reference: we cannot tell what is encrypted.
                result[UNRESOLVED_ENCRYPTION_KEY] = algorithm
                continue
            }
            val path = EpubPathResolver.normalizeEncryptionUri(uri)
            if (path == null) {
                result[UNRESOLVED_ENCRYPTION_KEY] = algorithm
            } else {
                // Last record wins, matching ZIP duplicate semantics.
                result[path] = algorithm
            }
        }
        return result
    }

    /**
     * `true` when [encryptionXml] is present but unusable.
     *
     * Two shapes are accepted as "nothing is encrypted", because real tooling writes them and they
     * cannot hide ciphertext: a document with no element at all (empty, XML declaration only, or
     * comments only), and a well-formed document with a single root element. Everything else — an
     * unterminated tag, mismatched open/close tags, or multiple roots — is malformed and must block
     * opening, because such a document could hide an `EncryptedData` we failed to read.
     */
    fun isMalformedEncryption(encryptionXml: String): Boolean {
        val body = stripDeclarationsAndComments(encryptionXml)
        if (body.isEmpty()) return false
        return !isWellFormedXml(encryptionXml)
    }

    /** Removes the XML declaration, comments and whitespace, leaving element markup only. */
    private fun stripDeclarationsAndComments(text: String): String {
        val builder = StringBuilder()
        var index = if (text.startsWith('\uFEFF')) 1 else 0
        while (index < text.length) {
            when {
                text.startsWith("<!--", index) -> {
                    val end = text.indexOf("-->", index + 4)
                    if (end < 0) {
                        builder.append(text, index, text.length)
                        break
                    }
                    index = end + 3
                }
                text.startsWith("<?", index) -> {
                    val end = text.indexOf("?>", index + 2)
                    if (end < 0) {
                        builder.append(text, index, text.length)
                        break
                    }
                    index = end + 2
                }
                else -> {
                    builder.append(text[index])
                    index++
                }
            }
        }
        return builder.toString().trim()
    }

    /**
     * Minimal XML well-formedness checker covering the shapes `encryption.xml` can take.
     *
     * Handles the XML declaration, comments, CDATA and DOCTYPE, then validates tag nesting and that
     * exactly one root element is present. It intentionally does not validate namespaces or attribute
     * values; those are not needed to decide whether content is ciphertext.
     */
    internal fun isWellFormedXml(text: String): Boolean {
        var index = if (text.startsWith('\uFEFF')) 1 else 0
        if (text.isBlank()) return false
        val stack = ArrayDeque<String>()
        var rootCount = 0
        var sawAny = false

        while (index < text.length) {
            if (text[index] != '<') {
                // Text outside any element (other than whitespace) at top level is malformed.
                if (stack.isEmpty() && !text[index].isWhitespace()) return false
                index++
                continue
            }
            sawAny = true
            when {
                text.startsWith("<!--", index) -> {
                    val end = text.indexOf("-->", index + 4)
                    if (end < 0) return false
                    index = end + 3
                }
                text.startsWith("<![CDATA[", index) -> {
                    val end = text.indexOf("]]>", index + 9)
                    if (end < 0) return false
                    if (stack.isEmpty()) return false
                    index = end + 3
                }
                text.startsWith("<?", index) -> {
                    val end = text.indexOf("?>", index + 2)
                    if (end < 0) return false
                    index = end + 2
                }
                text.startsWith("<!", index) -> {
                    // DOCTYPE or similar declaration; skip to the matching '>'.
                    val end = text.indexOf('>', index + 2)
                    if (end < 0) return false
                    index = end + 1
                }
                else -> {
                    val end = findTagEnd(text, index + 1)
                    if (end < 0) return false
                    val raw = text.substring(index + 1, end)
                    index = end + 1
                    if (raw.startsWith("/")) {
                        val name = raw.removePrefix("/").trim()
                        if (name.isEmpty()) return false
                        if (stack.isEmpty()) return false
                        if (stack.removeLast() != name) return false
                    } else {
                        val selfClosing = raw.endsWith("/")
                        val body = raw.removeSuffix("/").trim()
                        val name = body.substringBefore(' ').substringBefore('\t').substringBefore('\n')
                        if (name.isEmpty()) return false
                        if (stack.isEmpty()) {
                            rootCount++
                            if (rootCount > 1) return false
                        }
                        if (!selfClosing) stack.addLast(name)
                    }
                }
            }
        }
        return sawAny && stack.isEmpty() && rootCount == 1
    }

    /** Finds the `>` that ends a tag, skipping quoted attribute values. */
    private fun findTagEnd(text: String, from: Int): Int {
        var quote: Char? = null
        var index = from
        while (index < text.length) {
            val char = text[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else if (char == '"' || char == '\'') {
                quote = char
            } else if (char == '>') {
                return index
            }
            index++
        }
        return -1
    }

    /**
     * Selects elements whose local name is [name], matching Ksoup's literal `prefix:name` exposure.
     *
     * Matching the suffix keeps `dc:creator`, `creator` and any other prefix working, which matters
     * because EPUB 2 and 3 files disagree on prefixes.
     */
    private fun elementsNamed(parent: Element, name: String): List<Element> =
        parent.select("*").filter { localName(it.tagName()).equals(name, ignoreCase = true) }

    private fun textOf(parent: Element, name: String): String? =
        elementsNamed(parent, name).firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }

    private fun localName(tagName: String): String {
        val colon = tagName.lastIndexOf(':')
        return if (colon >= 0) tagName.substring(colon + 1) else tagName
    }
}
