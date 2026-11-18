package app.koyura.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.koyura.app.navigation.DetailRoute
import app.koyura.app.navigation.TabletUiMode
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.app.navigation.usesNavigationRailForWindow
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the real window measurement instead of the boolean flag.
 *
 * A fixed-size host drives [AppShell]'s own `BoxWithConstraints`, so the automatic-mode decision
 * is made by the production code path ([usesNavigationRailForWindow] with
 * `TabletUiMode.AUTOMATIC`): portrait smallest width >= 700 dp or landscape smallest width >=
 * 600 dp uses the rail. Growing the host keeps the same composition alive, which also checks
 * that the selected tab and its stack survive a live resize.
 */
class AppShellWidthUiTest {
    @Test
    fun sixHundredNinetyNineDpPortraitUsesBottomBar() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 699.dp, height = 800.dp)) {
                    AppShell()
                }
            }
        }

        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
    }

    @Test
    fun sevenHundredDpPortraitUsesRail() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 700.dp, height = 800.dp)) {
                    AppShell()
                }
            }
        }

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
    }

    @Test
    fun fiveHundredNinetyNineDpLandscapeUsesBottomBar() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 800.dp, height = 599.dp)) {
                    AppShell()
                }
            }
        }

        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
    }

    @Test
    fun sixHundredDpLandscapeUsesRail() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 800.dp, height = 600.dp)) {
                    AppShell()
                }
            }
        }

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
    }

    @Test
    fun rotatingKeepsRailWithoutFlicker() = runComposeUiTest {
        var size by mutableStateOf(700.dp to 1000.dp)
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = size.first, height = size.second)) {
                    AppShell()
                }
            }
        }

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()

        // Rotate to landscape: smallest width stays 700 dp, so the rail must not flicker back
        // to the bottom bar mid-rotation.
        size = 1000.dp to 700.dp

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
    }

    @Test
    fun windowRuleFollowsSmallestWidthAndMode() {
        // AUTOMATIC (default): portrait threshold 700 dp, landscape threshold 600 dp.
        assertEquals(false, usesNavigationRailForWindow(699.dp, 800.dp))
        assertEquals(true, usesNavigationRailForWindow(700.dp, 800.dp))
        assertEquals(false, usesNavigationRailForWindow(800.dp, 599.dp))
        assertEquals(true, usesNavigationRailForWindow(800.dp, 600.dp))
        // Rotation keeps the smallest width, so the morph never flickers mid-rotation.
        assertEquals(true, usesNavigationRailForWindow(700.dp, 1000.dp))
        assertEquals(true, usesNavigationRailForWindow(1000.dp, 700.dp))
        // Forced modes ignore the window size.
        assertEquals(true, usesNavigationRailForWindow(360.dp, 640.dp, TabletUiMode.ALWAYS))
        assertEquals(false, usesNavigationRailForWindow(1280.dp, 800.dp, TabletUiMode.NEVER))
        assertEquals(true, usesNavigationRailForWindow(1280.dp, 800.dp, TabletUiMode.LANDSCAPE))
        assertEquals(false, usesNavigationRailForWindow(800.dp, 1280.dp, TabletUiMode.LANDSCAPE))
    }

    @Test
    fun crossingTheBoundaryLiveKeepsTabAndStack() = runComposeUiTest {
        val navigation = createAppNavigationState()
        var width by mutableStateOf<Dp>(719.dp)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = usesNavigationRail(width))
            }
        }

        navigation.select(NavTab.Browse)
        navigation.push(DetailRoute(NavTab.Browse, 3))
        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
        onNodeWithTag("detail-title").assertIsDisplayed()

        width = 720.dp

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
        onNodeWithTag("detail-title").assertIsDisplayed()
        assertEquals(NavTab.Browse, navigation.selectedTab)
        assertEquals(2, navigation.stackOf(NavTab.Browse).size)
    }
}
