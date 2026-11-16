package app.koyura.media

/**
 * Coordinates and version of the `shared/media` module.
 *
 * The module owns text cleanup, resource loading, local file formats and offline
 * content packs. This stage provides the EPUB archive reader and the local TXT novel
 * importer (`app.koyura.media.novel`); importer pipelines for other formats are added
 * on top of it.
 */
object MediaModule {
    const val NAMESPACE: String = "app.koyura.media"
    const val IMPLEMENTATION_STAGE: String = "txt-novel-import"
}
