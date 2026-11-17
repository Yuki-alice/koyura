package app.koyura.features.reader.comic.viewer

/**
 * Navigation tap-zone layouts, mirroring Komikku's pager/webtoon navigation modes
 * (`PagerConfig` / `WebtoonConfig` navigation + invert档).
 *
 * Geometry is defined once as fractional tilings ([tapZoneRects]) in reading-axis space:
 * - [TapRect.alongFrom]/[alongTo] run along the reading axis (x on pagers, y on strips);
 * - [TapRect.acrossFrom]/[acrossTo] run across it.
 * The viewer transposes the axes for vertical modes; point lookup ([resolveComicTap]) and the
 * tap overlay consume the same tiling so logic and layout cannot drift apart.
 *
 * Fractional boundaries are best-effort Komikku parity (thirds/halves/fifths/sixths); exact
 * pixel parity against `PagerConfig`/`WebtoonConfig` stays a follow-up (see reader track notes).
 */
enum class ComicTapZoneMode(val id: String) {
    /** Thirds along the reading axis: prev / menu / next. */
    DEFAULT("DEFAULT"),

    /** Halves along the reading axis: prev / next, no menu zone. */
    LEFT_RIGHT("LEFT_RIGHT"),

    /** Mostly-next: a narrow prev strip, menu on the top/leading across band, next elsewhere. */
    KINDLE("KINDLE"),

    /** Only the outer edges turn; everything else opens the menu. */
    EDGE("EDGE"),

    /** L arms: full prev strip plus a next arm on the far across band. */
    L_SHAPE("L_SHAPE"),

    /** No tap navigation; taps fall through to gestures. */
    DISABLED("DISABLED"),
    ;

    companion object {
        fun fromId(id: String): ComicTapZoneMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Tap outcome in viewport terms; the viewer maps PREV/NEXT onto `goVisual(∓1)`. */
enum class ComicTapAction {
    PREVIOUS,
    NEXT,
    MENU,
    NONE,
}

/**
 * One tiled tap region in reading-axis fractions. Rects for a mode tile [0,1]² without overlap;
 * shared edges belong to the earlier rect ([alongTo]/[acrossTo] are exclusive except at 1).
 */
data class TapRect(
    val alongFrom: Float,
    val alongTo: Float,
    val acrossFrom: Float,
    val acrossTo: Float,
    val action: ComicTapAction,
)

/**
 * Fractional tiling for [zone]. Vertical modes transpose along/across when laying out.
 */
fun tapZoneRects(zone: ComicTapZoneMode): List<TapRect> = when (zone) {
    ComicTapZoneMode.DEFAULT -> listOf(
        TapRect(0f, 1f / 3f, 0f, 1f, ComicTapAction.PREVIOUS),
        TapRect(1f / 3f, 2f / 3f, 0f, 1f, ComicTapAction.MENU),
        TapRect(2f / 3f, 1f, 0f, 1f, ComicTapAction.NEXT),
    )
    ComicTapZoneMode.LEFT_RIGHT -> listOf(
        TapRect(0f, 0.5f, 0f, 1f, ComicTapAction.PREVIOUS),
        TapRect(0.5f, 1f, 0f, 1f, ComicTapAction.NEXT),
    )
    ComicTapZoneMode.KINDLE -> listOf(
        TapRect(0f, 0.2f, 0f, 1f, ComicTapAction.PREVIOUS),
        TapRect(0.2f, 1f, 0f, 1f / 6f, ComicTapAction.MENU),
        TapRect(0.2f, 1f, 1f / 6f, 1f, ComicTapAction.NEXT),
    )
    ComicTapZoneMode.EDGE -> listOf(
        TapRect(0f, 1f / 6f, 0f, 1f, ComicTapAction.PREVIOUS),
        TapRect(1f / 6f, 5f / 6f, 0f, 1f, ComicTapAction.MENU),
        TapRect(5f / 6f, 1f, 0f, 1f, ComicTapAction.NEXT),
    )
    ComicTapZoneMode.L_SHAPE -> listOf(
        TapRect(0f, 1f / 3f, 0f, 1f, ComicTapAction.PREVIOUS),
        TapRect(1f / 3f, 1f, 0f, 2f / 3f, ComicTapAction.MENU),
        TapRect(1f / 3f, 1f, 2f / 3f, 1f, ComicTapAction.NEXT),
    )
    ComicTapZoneMode.DISABLED -> emptyList()
}

/**
 * Resolves a tap at reading-axis fractions ([along], [across], each 0..1, clamped) to an action.
 * [invert] swaps PREVIOUS/NEXT (Komikku 反转档); MENU/NONE are unaffected.
 */
fun resolveComicTap(
    zone: ComicTapZoneMode,
    along: Float,
    across: Float,
    invert: Boolean,
): ComicTapAction {
    val action = tapZoneRects(zone).firstOrNull { rect ->
        along.coerceIn(0f, 1f) >= rect.alongFrom &&
            (along.coerceIn(0f, 1f) < rect.alongTo || rect.alongTo >= 1f) &&
            across.coerceIn(0f, 1f) >= rect.acrossFrom &&
            (across.coerceIn(0f, 1f) < rect.acrossTo || rect.acrossTo >= 1f)
    }?.action ?: ComicTapAction.NONE
    if (!invert) return action
    return when (action) {
        ComicTapAction.PREVIOUS -> ComicTapAction.NEXT
        ComicTapAction.NEXT -> ComicTapAction.PREVIOUS
        ComicTapAction.MENU, ComicTapAction.NONE -> action
    }
}
