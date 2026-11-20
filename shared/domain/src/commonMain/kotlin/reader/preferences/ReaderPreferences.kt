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
 * Reader background around the pages (Komikku `pref_reader_theme_key` semantics):
 * Automatic follows the app theme (dark -> #202125 gray, light -> white);
 * Black/Gray force a dark surface regardless of theme, White forces a light one.
 */
enum class ReaderBackground {
    AUTOMATIC,
    BLACK,
    GRAY,
    WHITE,
}

/**
 * Tap-zone layout (Komikku navigation modes, mirrored by the viewer's tap-zone enum):
 * Default is thirds along the reading axis (prev / menu / next);
 * LeftRight halves it without a menu zone; Kindle is mostly-next with a narrow prev strip.
 */
enum class ReaderTapZone {
    DEFAULT,
    LEFT_RIGHT,
    KINDLE,
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
    /** Background around the pages; Automatic follows the app theme. */
    val background: ReaderBackground = ReaderBackground.AUTOMATIC,
    /** Tap-zone layout; Default is the classic thirds behavior. */
    val tapZone: ReaderTapZone = ReaderTapZone.DEFAULT,
    /** Swap the prev/next tap regions (the menu region is unaffected). */
    val invertTapZone: Boolean = false,
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
    val background: ReaderBackground? = null,
    val tapZone: ReaderTapZone? = null,
    val invertTapZone: Boolean? = null,
    val firstPageSingle: Boolean? = null,
    val dualPageReversed: Boolean? = null,
    val cropBorders: Boolean? = null,
    val zoomOnDoubleTap: Boolean? = null,
) {
    fun isEmpty(): Boolean =
        mode == null && layout == null && fit == null && background == null &&
            tapZone == null && invertTapZone == null &&
            firstPageSingle == null &&
            dualPageReversed == null && cropBorders == null && zoomOnDoubleTap == null

    /** Resolves the effective settings against [base]. */
    fun resolvedOver(base: ReaderSettings): ReaderSettings =
        ReaderSettings(
            mode = mode ?: base.mode,
            layout = layout ?: base.layout,
            fit = fit ?: base.fit,
            background = background ?: base.background,
            tapZone = tapZone ?: base.tapZone,
            invertTapZone = invertTapZone ?: base.invertTapZone,
            firstPageSingle = firstPageSingle ?: base.firstPageSingle,
            dualPageReversed = dualPageReversed ?: base.dualPageReversed,
            cropBorders = cropBorders ?: base.cropBorders,
            zoomOnDoubleTap = zoomOnDoubleTap ?: base.zoomOnDoubleTap,
        )
}

/**
 * One field of a [WorkReaderOverridePatch]. Three states are explicit so callers can tell
 * "leave it alone" apart from "clear it back to the global default":
 * - [Keep]: do not touch the stored field;
 * - [Set]: store [value];
 * - [Clear]: drop the stored field so it inherits the global value again.
 */
sealed interface OverrideField<out T> {
    data object Keep : OverrideField<Nothing>

    data class Set<T>(val value: T) : OverrideField<T>

    data object Clear : OverrideField<Nothing>
}

/**
 * A field-by-field patch for [WorkReaderOverride]. Unset fields ([OverrideField.Keep]) are left
 * alone, which is what makes overlapping writes from different writers merge instead of clobber.
 */
data class WorkReaderOverridePatch(
    val mode: OverrideField<ReadingMode> = OverrideField.Keep,
    val layout: OverrideField<ReaderLayout> = OverrideField.Keep,
    val fit: OverrideField<ReaderFit> = OverrideField.Keep,
    val background: OverrideField<ReaderBackground> = OverrideField.Keep,
    val tapZone: OverrideField<ReaderTapZone> = OverrideField.Keep,
    val invertTapZone: OverrideField<Boolean> = OverrideField.Keep,
    val firstPageSingle: OverrideField<Boolean> = OverrideField.Keep,
    val dualPageReversed: OverrideField<Boolean> = OverrideField.Keep,
    val cropBorders: OverrideField<Boolean> = OverrideField.Keep,
    val zoomOnDoubleTap: OverrideField<Boolean> = OverrideField.Keep,
) {
    fun isEmpty(): Boolean =
        mode is OverrideField.Keep && layout is OverrideField.Keep && fit is OverrideField.Keep &&
            background is OverrideField.Keep &&
            tapZone is OverrideField.Keep && invertTapZone is OverrideField.Keep &&
            firstPageSingle is OverrideField.Keep && dualPageReversed is OverrideField.Keep &&
            cropBorders is OverrideField.Keep && zoomOnDoubleTap is OverrideField.Keep
}

private fun <T> currentOr(current: T?, patch: OverrideField<T>): T? = when (patch) {
    OverrideField.Keep -> current
    is OverrideField.Set -> patch.value
    OverrideField.Clear -> null
}

/** Returns a copy with every non-[OverrideField.Keep] field of [patch] applied. */
fun WorkReaderOverride.patchedWith(patch: WorkReaderOverridePatch): WorkReaderOverride =
    WorkReaderOverride(
        mode = currentOr(mode, patch.mode),
        layout = currentOr(layout, patch.layout),
        fit = currentOr(fit, patch.fit),
        background = currentOr(background, patch.background),
        tapZone = currentOr(tapZone, patch.tapZone),
        invertTapZone = currentOr(invertTapZone, patch.invertTapZone),
        firstPageSingle = currentOr(firstPageSingle, patch.firstPageSingle),
        dualPageReversed = currentOr(dualPageReversed, patch.dualPageReversed),
        cropBorders = currentOr(cropBorders, patch.cropBorders),
        zoomOnDoubleTap = currentOr(zoomOnDoubleTap, patch.zoomOnDoubleTap),
    )

/** Resolves the effective settings: [override] merged over this global, or this when null. */
fun ReaderSettings.withOverride(override: WorkReaderOverride?): ReaderSettings =
    override?.resolvedOver(this) ?: this
