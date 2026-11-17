package app.koyura.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.designsystem.navigation.NavigationRailEntriesTag
import app.koyura.designsystem.navigation.ReaderNavigationRail
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the Komikku wide-rail contract: the entry group stays vertically centered in the
 * rail's available area instead of sticking to the top.
 *
 * Reference: Komikku `presentation/core/.../material/NavigationRail.kt` packs its full-height
 * column with `Arrangement.spacedBy(extraSmall, CenterVertically)`; `HomeScreen` renders the
 * five tabs inside it. The independent check measured the top-stuck group at y=162 against a
 * rail center of y=384 in a 1280x800 dp viewport.
 */
class NavigationRailCenteringTest {
    private fun centerY(tag: String, widthDp: Int = 96, heightDp: Int = 800): Pair<Float, Float> {
        var railCenter = 0f
        var entriesCenter = 0f
        runComposeUiTest {
            setContent {
                ReaderTheme {
                    Box(Modifier.size(widthDp.dp, heightDp.dp)) {
                        ReaderNavigationRail(
                            selectedTab = NavTab.Library,
                            onTabClick = {},
                            modifier = Modifier.testTag("test-rail"),
                            visibleTabs = visibleTabsFor(tag),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        )
                    }
                }
            }
            waitForIdle()
            val rail = onNodeWithTag("test-rail").fetchSemanticsNode()
            val entries = onNodeWithTag(NavigationRailEntriesTag).fetchSemanticsNode()
            val railBounds = rail.boundsInRoot
            val entriesBounds = entries.boundsInRoot
            railCenter = (railBounds.top + railBounds.bottom) / 2f
            entriesCenter = (entriesBounds.top + entriesBounds.bottom) / 2f
        }
        return railCenter to entriesCenter
    }

    private fun visibleTabsFor(tag: String): Set<NavTab> = when (tag) {
        "subset" -> setOf(NavTab.Library, NavTab.History, NavTab.More)
        else -> NavTab.entries.toSet()
    }

    @Test
    fun allFiveEntriesAreVerticallyCentered() {
        val (railCenter, entriesCenter) = centerY("all")
        val drift = abs(entriesCenter - railCenter)
        assertTrue(
            drift <= CenteringTolerancePx,
            "five entries must sit at the rail center: rail=$railCenter entries=$entriesCenter drift=$drift",
        )
    }

    @Test
    fun hiddenEntriesRemainCentered() {
        val (railCenter, entriesCenter) = centerY("subset")
        val drift = abs(entriesCenter - railCenter)
        assertTrue(
            drift <= CenteringTolerancePx,
            "visible subset must stay centered: rail=$railCenter entries=$entriesCenter drift=$drift",
        )
    }

    private companion object {
        /** Layout is exact; allow sub-pixel rounding plus one dp of slack. */
        const val CenteringTolerancePx = 2f
    }
}
