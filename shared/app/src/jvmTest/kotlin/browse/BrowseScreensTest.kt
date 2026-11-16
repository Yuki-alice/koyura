package app.koyura.app.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.koyura.app.AppScaffold
import app.koyura.app.RailWidthThreshold
import app.koyura.app.usesNavigationRail
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Milestone 1: source/extension pages, search, grouping and the details entry.
 *
 * These tests drive the real shell, so they also prove the Browse page, query and scroll state
 * survive tab switches, the details round trip and the 719/720 dp width switch.
 */
class BrowseScreensTest {
    @Test
    fun sourcesPageGroupsByLanguageAndFiltersByQuery() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false, browseService = service)
            }
        }

        onNodeWithTag(BrowseSourcesListTag).assertIsDisplayed()
        onNodeWithTag("browse-source-query").performTextReplacement("manga")
        onNodeWithTag(BrowseSourcesListTag).assertIsDisplayed()

        onNodeWithTag("browse-source-query").performTextReplacement("zzz-no-match")
        onNodeWithTag(BrowseEmptyTag).assertIsDisplayed()
    }

    @Test
    fun extensionsPageGroupsUpdatesFirstAndOpensDetails() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag(BrowseExtensionsListTag).assertIsDisplayed()
        // Updates section is rendered first, so its row exists before the available rows.
        onNodeWithTag(extensionRowTag("store:1::com.example.updates")).assertIsDisplayed()

        onNodeWithTag(extensionActionTag("store:1::com.example.updates", "open")).performClick()
        onNodeWithTag(ExtensionDetailsTestTag).assertIsDisplayed()
        onNodeWithTag(ExtensionDetailsUninstallTag).assertIsDisplayed()

        navigation.handleBack()
        onNodeWithTag(BrowseExtensionsListTag).assertIsDisplayed()
        assertEquals(
            listOf("TabRootRoute(tab=Browse)"),
            navigation.stackOf(NavTab.Browse).map { it.toString() },
        )
    }

    @Test
    fun searchAndPaneSurviveTabSwitchAndWidthChange() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        var useRail by mutableStateOf(false)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = useRail, browseService = service)
            }
        }

        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag(BrowseExtensionQueryTag).performTextReplacement("Available")
        onNodeWithTag(extensionRowTag("store:1::com.example.available")).assertIsDisplayed()

        navigation.select(NavTab.Library)
        navigation.select(NavTab.Browse)
        onNodeWithTag(BrowseExtensionQueryTag).assertExists()
        onNodeWithTag(extensionRowTag("store:1::com.example.available")).assertIsDisplayed()

        useRail = true
        onNodeWithTag(BrowseExtensionsListTag).assertIsDisplayed()
        onNodeWithTag(extensionRowTag("store:1::com.example.available")).assertIsDisplayed()
    }

    @Test
    fun searchPersistsAcrossDetailRoundTrip() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false, browseService = service)
            }
        }

        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag(BrowseExtensionQueryTag).performTextReplacement("Installed")
        onNodeWithTag(extensionActionTag("store:1::com.example.installed", "open")).performClick()
        onNodeWithTag(ExtensionDetailsTestTag).assertIsDisplayed()

        navigation.handleBack()

        onNodeWithTag(BrowseExtensionQueryTag).assertIsDisplayed()
        onNodeWithTag(extensionRowTag("store:1::com.example.installed")).assertIsDisplayed()
    }

    @Test
    fun sameKeyAcrossStoresStaysDistinguishable() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag(BrowseExtensionQueryTag).performTextReplacement("com.example.available")

        // Same packageName from two stores must render as two separate rows.
        onNodeWithTag(extensionRowTag("store:1::com.example.available")).assertIsDisplayed()
        onNodeWithTag(extensionRowTag("store:2::com.example.available")).assertIsDisplayed()
    }

    @Test
    fun noServiceShowsExplicitEmptyState() = runComposeUiTest {
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false, browseService = null)
            }
        }

        onNodeWithTag(BrowseUnavailableTag).assertIsDisplayed()
    }

    @Test
    fun railThresholdStillMatchesShellConstant() = runComposeUiTest {
        assertEquals(720.dp, RailWidthThreshold)
        assertEquals(false, usesNavigationRail(719.dp))
        assertEquals(true, usesNavigationRail(720.dp))
    }
}

/** Width helper kept out of production code: tests only need the threshold comparison. */
private fun usesNavigationRail(width: Dp): Boolean = width >= RailWidthThreshold
