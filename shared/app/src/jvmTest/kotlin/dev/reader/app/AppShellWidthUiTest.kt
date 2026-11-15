package dev.reader.app

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
import dev.reader.app.navigation.DetailRoute
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the real width measurement instead of the boolean flag.
 *
 * A fixed-size host drives [AppShell]'s own `BoxWithConstraints`, so the 719/720 dp decision is made
 * by the production code path. Growing the host keeps the same composition alive, which also checks
 * that the selected tab and its stack survive a live resize.
 */
class AppShellWidthUiTest {
    @Test
    fun sevenHundredNineteenDpUsesBottomBar() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 719.dp, height = 800.dp)) {
                    AppShell()
                }
            }
        }

        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
    }

    @Test
    fun sevenHundredTwentyDpUsesRail() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 720.dp, height = 800.dp)) {
                    AppShell()
                }
            }
        }

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
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
