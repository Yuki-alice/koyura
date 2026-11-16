package dev.reader.media.epub

/** Parsed `META-INF/container.xml`: the package document location. */
internal data class EpubPackageLocation(
    val packageDocumentPath: String,
    val mediaType: String?,
)

/** One `<item>` of the OPF manifest, with [path] already normalized against the OPF directory. */
internal data class EpubManifestItem(
    val id: String,
    val path: String,
    val mediaType: String?,
    val properties: Set<String>,
) {
    fun hasProperty(property: String): Boolean = property in properties
}

/**
 * One `<itemref>` of the OPF spine.
 *
 * [linear] mirrors `linear="no"`; [isAuxiliary] is `true` when the item is explicitly non-linear, and
 * such items are excluded from the default reading sequence.
 */
internal data class EpubSpineItem(
    val idref: String,
    val linear: Boolean,
) {
    val isAuxiliary: Boolean get() = !linear
}

/** Parsed OPF package document. Paths are normalized, archive-root relative and `/` separated. */
internal data class EpubPackage(
    val opfPath: String,
    val version: String?,
    val title: String,
    val authors: List<String>,
    val language: String?,
    val manifest: Map<String, EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    /** `cover-image` item when declared, else the EPUB 2 `meta name="cover"` target. */
    val cover: EpubManifestItem?,
    /** Manifest item carrying `properties="nav"`. */
    val navItem: EpubManifestItem?,
    /** Media type declared by the EPUB 2 `meta name="cover"` when used as fallback. */
    val coverMetaTarget: String?,
)
