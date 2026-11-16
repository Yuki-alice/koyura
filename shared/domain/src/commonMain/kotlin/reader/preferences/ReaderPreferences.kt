package app.koyura.domain.reader.preferences

import kotlinx.serialization.Serializable

/** Reading direction and pagination, mirroring Komikku's reading-mode IDs. */
enum class ReadingMode {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    VERTICAL,
    WEBTOON,
    CONTINUOUS_VERTICAL,
}

/** Page layout for paged modes. */
enum class ReaderLayout {
    SINGLE_PAGE,
    DOUBLE_PAGES,
    AUTOMATIC,
}

/** Image scaling inside the viewport. */
enum class ReaderFit {
    FIT,
    FIT_WIDTH,
    FIT_HEIGHT,
    ORIGINAL,
}

/**
 * Global reader defaults. Unknown JSON fields are ignored and missing fields fall back to these
 * defaults so old configs keep working.
 */
@Serializable
data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.LEFT_TO_RIGHT,
    val layout: ReaderLayout = ReaderLayout.AUTOMATIC,
    val fit: ReaderFit = ReaderFit.FIT,
    /** First page is always shown alone, even in double-page layout. */
    val firstPageSingle: Boolean = true,
    /** Pair pages right-to-left in double-page layout. */
    val dualPageReversed: Boolean = false,
    /** Trim solid borders before display. */
    val cropBorders: Boolean = false,
    /** Double tap toggles zoom. */
    val zoomOnDoubleTap: Boolean = true,
)

/**
 * Per-work override. Every field is nullable: `null` (DEFAULT) means inherit the global value.
 * Overrides are isolated by `(sourceKey, workId)` and merged field-by-field.
 */
@Serializable
data class WorkReaderOverride(
    val mode: ReadingMode? = null,
    val layout: ReaderLayout? = null,
    val fit: ReaderFit? = null,
    val firstPageSingle: Boolean? = null,
    val dualPageReversed: Boolean? = null,
    val cropBorders: Boolean? = null,
    val zoomOnDoubleTap: Boolean? = null,
) {
    fun isEmpty(): Boolean =
        mode == null && layout == null && fit == null && firstPageSingle == null &&
            dualPageReversed == null && cropBorders == null && zoomOnDoubleTap == null

    /** Returns a copy with every non-null field of [patch] applied. */
    fun patchedWith(patch: WorkReaderOverride): WorkReaderOverride =
        WorkReaderOverride(
            mode = patch.mode ?: mode,
            layout = patch.layout ?: layout,
            fit = patch.fit ?: fit,
            firstPageSingle = patch.firstPageSingle ?: firstPageSingle,
            dualPageReversed = patch.dualPageReversed ?: dualPageReversed,
            cropBorders = patch.cropBorders ?: cropBorders,
            zoomOnDoubleTap = patch.zoomOnDoubleTap ?: zoomOnDoubleTap,
        )

    /** Resolves the effective settings against [base]. */
    fun resolvedOver(base: ReaderSettings): ReaderSettings =
        ReaderSettings(
            mode = mode ?: base.mode,
            layout = layout ?: base.layout,
            fit = fit ?: base.fit,
            firstPageSingle = firstPageSingle ?: base.firstPageSingle,
            dualPageReversed = dualPageReversed ?: base.dualPageReversed,
            cropBorders = cropBorders ?: base.cropBorders,
            zoomOnDoubleTap = zoomOnDoubleTap ?: base.zoomOnDoubleTap,
        )
}

/** Resolves the effective settings: [override] merged over this global, or this when null. */
fun ReaderSettings.withOverride(override: WorkReaderOverride?): ReaderSettings =
    override?.resolvedOver(this) ?: this
