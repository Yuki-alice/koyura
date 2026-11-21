package app.koyura.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.browse.BrowseRootTestTag
import app.koyura.app.history.HistoryRootTestTag
import app.koyura.app.navigation.DetailRoute
import app.koyura.app.navigation.TabRootRoute
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.features.library.LibraryEmptyTag
import app.koyura.features.library.LibraryFilterSheetTag
import app.koyura.features.library.LibraryRootTag
import app.koyura.features.more.MoreRootTestTag
import app.koyura.features.updates.UpdatesRootTestTag
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
        // 书架页签渲染真实书架屏（未装配仓储时呈空态），不再是占位页。
        onNodeWithTag(LibraryRootTag).assertIsDisplayed()
        onNodeWithTag(LibraryEmptyTag).assertIsDisplayed()
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
    fun tappingSelectedLibraryTabOpensFilterSheet() = runComposeUiTest {
        val navigation = createAppNavigationState()
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false)
            }
        }

        // Scope to the bottom bar: the Library page also uses tab semantics for its
        // category row, so a global selected-tab lookup would match twice.
        // G1：重复点击书架 tab 打开筛选 Sheet（页内状态），不进导航栈。
        val barSelected = selectedTab and hasAnyAncestor(hasTestTag(NavigationBarTestTag))
        onNode(barSelected).performClick()

        onNodeWithTag(LibraryFilterSheetTag).assertIsDisplayed()
        assertEquals(1, navigation.stackOf(NavTab.Library).size)
    }

    @Test
    fun reselectKeepsStackAndRunsAction() {
        val events = mutableListOf<NavTab>()
        val navigation = createAppNavigationState(onTabReselect = { events += it })

        navigation.push(DetailRoute(NavTab.Library, 1))
        assertEquals(2, navigation.stackOf(NavTab.Library).size)

        // G1：重复点击不再 popToRoot，只跑动作，子页栈原样保留。
        navigation.select(NavTab.Library)

        assertEquals(2, navigation.stackOf(NavTab.Library).size)
        assertEquals(listOf(NavTab.Library), events)
        assertEquals(NavTab.Library, navigation.selectedTab)
    }

    @Test
    fun detailRoutePushesAndPopsInsideItsOwnTab() = runComposeUiTest {
        val navigation = createAppNavigationState()
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true)
            }
        }

        onNodeWithTag(LibraryRootTag).assertIsDisplayed()
        navigation.push(DetailRoute(NavTab.Library, 1))
        onNodeWithTag("detail-title").assertIsDisplayed()
        assertEquals(2, navigation.stackOf(NavTab.Library).size)

        navigation.handleBack()
        onNodeWithTag(LibraryRootTag).assertIsDisplayed()
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
            } else if (tab == NavTab.History) {
                // History renders its own surface instead of the placeholder root page.
                onNodeWithTag(HistoryRootTestTag).assertIsDisplayed()
            } else if (tab == NavTab.Library) {
                // Library renders the real shelf instead of the placeholder root page.
                onNodeWithTag(LibraryRootTag).assertIsDisplayed()
            } else if (tab == NavTab.Updates) {
                // Updates renders the real grouped list instead of the placeholder root page.
                onNodeWithTag(UpdatesRootTestTag).assertIsDisplayed()
            } else if (tab == NavTab.More) {
                // More renders the real settings entry list instead of the placeholder root page.
                onNodeWithTag(MoreRootTestTag).assertIsDisplayed()
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

        // Scope to the rail for the same reason: the Library page owns tab semantics too.
        onNode(selectedTab and hasAnyAncestor(hasTestTag(NavigationRailTestTag))).assertIsSelected()
    }
}
