package dev.reader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.reader.app.browse.BrowseRootTestTag
import dev.reader.app.navigation.DetailRoute
import dev.reader.app.navigation.TabRootRoute
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals

class AppShellUiTest {
    private val selectedTab = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun narrowShellShowsBottomBarAndDefaultsToLibrary() = runComposeUiTest {
        setContent {
            ReaderTheme {
                AppScaffold(navigation = createAppNavigationState(), useRail = false)
            }
        }

        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
        onNodeWithTag("tab-title-Library").assertIsDisplayed()
    }

    @Test
    fun wideShellShowsNavigationRail() = runComposeUiTest {
        setContent {
            ReaderTheme {
                AppScaffold(navigation = createAppNavigationState(), useRail = true)
            }
        }

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
    }

    @Test
    fun tappingSelectedTabEmitsReselectWithoutPushing() = runComposeUiTest {
        val events = mutableListOf<NavTab>()
        val navigation = createAppNavigationState(onTabReselect = { events += it })
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false)
            }
        }

        onNode(selectedTab).performClick()
        onNode(selectedTab).performClick()

        assertEquals(listOf(NavTab.Library, NavTab.Library), events)
        assertEquals(1, navigation.stackOf(NavTab.Library).size)
    }

    @Test
    fun detailRoutePushesAndPopsInsideItsOwnTab() = runComposeUiTest {
        val navigation = createAppNavigationState()
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true)
            }
        }

        onNodeWithTag("open-detail-Library").performClick()
        onNodeWithTag("detail-title").assertIsDisplayed()
        assertEquals(2, navigation.stackOf(NavTab.Library).size)

        navigation.handleBack()
        onNodeWithTag("tab-title-Library").assertIsDisplayed()
        assertEquals(1, navigation.stackOf(NavTab.Library).size)
    }

    @Test
    fun eachTabRendersItsOwnHeading() = runComposeUiTest {
        val navigation = createAppNavigationState()
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true)
            }
        }

        NavTab.entries.forEach { tab ->
            navigation.select(tab)
            if (tab == NavTab.Browse) {
                // Browse now renders its own surface instead of the placeholder root page.
                onNodeWithTag(BrowseRootTestTag).assertIsDisplayed()
            } else {
                onNodeWithTag("tab-title-${tab.name}").assertIsDisplayed()
            }
        }
    }

    @Test
    fun switchingLayoutKeepsSelectedTabAndStacks() = runComposeUiTest {
        val navigation = createAppNavigationState()
        var useRail by mutableStateOf(false)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = useRail)
            }
        }

        navigation.select(NavTab.Browse)
        navigation.push(DetailRoute(NavTab.Browse, 7))
        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()

        useRail = true
        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
        onNodeWithTag("detail-title").assertIsDisplayed()
        assertEquals(
            listOf(TabRootRoute(NavTab.Browse), DetailRoute(NavTab.Browse, 7)),
            navigation.stackOf(NavTab.Browse).toList(),
        )
    }

    @Test
    fun railMarksSelectedTab() = runComposeUiTest {
        val navigation = createAppNavigationState(initialTab = NavTab.History)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true)
            }
        }

        onNode(selectedTab).assertIsSelected()
    }
}
