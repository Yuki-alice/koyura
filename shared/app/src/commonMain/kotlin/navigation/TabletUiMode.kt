package app.koyura.app.navigation

import androidx.compose.ui.unit.Dp
import app.koyura.designsystem.DesignTokens

/**
 * Forces the wide (navigation rail / two-pane) layout, mirroring Komikku `TabletUiMode`.
 *
 * The shell has no settings store yet, so the call site defaults to [AUTOMATIC], matching a fresh
 * Komikku install (`UiPreferences.tabletUiMode` default). Once the More settings land, this value
 * comes from the persisted preference instead of the call-site default.
 */
enum class TabletUiMode {
    /** Portrait smallest width >= 700 dp or landscape smallest width >= 600 dp uses the rail. */
    AUTOMATIC,

    /** Always uses the rail, regardless of window size. */
    ALWAYS,

    /** Uses the rail only in landscape orientation, regardless of window size. */
    LANDSCAPE,

    /** Always uses the bottom bar, regardless of window size. */
    NEVER,
}

/**
 * Window form-factor rule (whitepaper 03 §3.1).
 *
 * Uses the *smallest* width (`minOf(width, height)`, Komikku `smallestScreenWidthDp`) so rotating
 * the window never flickers back to the phone layout mid-rotation. Thresholds come from
 * [DesignTokens.Breakpoints]: 720 dp base (`isTabletUi`), 700 dp automatic portrait
 * (`TABLET_UI_MIN_SCREEN_WIDTH_PORTRAIT_DP`), 600 dp automatic landscape
 * (`TABLET_UI_MIN_SCREEN_WIDTH_LANDSCAPE_DP`, keeps rail icons fitting).
 */
fun usesNavigationRailForWindow(
    width: Dp,
    height: Dp,
    mode: TabletUiMode = TabletUiMode.AUTOMATIC,
): Boolean {
    val portrait = height >= width
    val smallest = minOf(width, height).value
    return when (mode) {
        TabletUiMode.ALWAYS -> true
        TabletUiMode.NEVER -> false
        TabletUiMode.LANDSCAPE -> !portrait
        TabletUiMode.AUTOMATIC -> {
            smallest >= if (portrait) {
                DesignTokens.Breakpoints.TabletAutomaticPortraitDp
            } else {
                DesignTokens.Breakpoints.TabletAutomaticLandscapeDp
            }
        }
    }
}
