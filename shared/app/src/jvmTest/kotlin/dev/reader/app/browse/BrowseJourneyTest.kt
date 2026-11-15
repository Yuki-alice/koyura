package dev.reader.app.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reader.app.AppScaffold
import dev.reader.app.NavigationBarTestTag
import dev.reader.app.NavigationRailTestTag
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Milestone 3: one continuous journey through the Browse entry point.
 *
 * Add store → preview catalog → install → update → uninstall, including a forced failure and retry,
 * a cross-tab round trip and a live 719 ↔ 720 dp resize. Everything runs through the real shell with
 * an in-memory [BrowseService], which is exactly how the real runtime will be injected.
 */
class BrowseJourneyTest {
    @Test
    fun addStorePreviewInstallUpdateUninstall() = runComposeUiTest {
        val service = FakeBrowseService()
        // The first preview fails so the journey also covers error + retry.
        service.previewFailure = "index unreachable"
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false, browseService = service)
            }
        }

        // 1. Open the extension page, then store management.
        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(StoresScreenTestTag).assertIsDisplayed()

        // 2. Add a store; invalid URL first, then a valid one.
        onNodeWithTag(StoresAddTag).performClick()
        onNodeWithTag("store-save").performClick()
        onNodeWithTag("store-url-error").assertIsDisplayed()
        onNodeWithTag("store-url-field").performTextReplacement("https://journey.example/index.json")
        onNodeWithTag("store-save").performClick()
        onNodeWithTag(storeRowTag("store:3")).assertIsDisplayed()

        // 3. Preview the existing store; the injected service fails it once, then retry succeeds.
        onNodeWithTag(storePreviewTag("store:1")).performClick()
        onNodeWithTag(CatalogDialogTag).assertIsDisplayed()
        onNodeWithTag("catalog-retry").assertIsDisplayed()
        onNodeWithTag("catalog-retry").performClick()
        onNodeWithTag("catalog-install-com.example.new").assertIsDisplayed()

        // 4. Install an entry directly from the open catalog preview.
        onNodeWithTag("catalog-install-com.example.new").performClick()
        assertEquals(1, service.calls["install:store:1::com.example.new"])
        onNodeWithTag("catalog-close").performClick()

        // 5. Back to extensions: update the pending row, then uninstall it.
        onNodeWithTag("stores-back").performClick()
        onNodeWithTag(BrowseExtensionsTabTag).assertIsDisplayed()
        onNodeWithTag(BrowseExtensionQueryTag).performTextReplacement("updates")
        onNodeWithTag(extensionActionTag("store:1::com.example.updates", "update")).performClick()
        onNodeWithTag("operation-succeeded-store:1::com.example.updates", useUnmergedTree = true).assertExists()

        onNodeWithTag(extensionActionTag("store:1::com.example.updates", "uninstall")).performClick()
        assertTrue(service.snapshot.extensions.none { it.key == "store:1::com.example.updates" })

        // 6. Clearing the filter shows the remaining extensions again.
        onNodeWithTag(BrowseExtensionQueryTag).performTextReplacement("")
        onNodeWithTag(BrowseExtensionsListTag).assertIsDisplayed()
        onNodeWithTag(extensionRowTag("store:1::com.example.installed")).assertIsDisplayed()
    }

    @Test
    fun journeySurvivesTabSwitchAndWidthChange() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        var width by mutableStateOf<Dp>(719.dp)
        setContent {
            ReaderTheme {
                Box(Modifier.size(width, 700.dp)) {
                    AppScaffold(
                        navigation = navigation,
                        useRail = width >= 720.dp,
                        browseService = service,
                    )
                }
            }
        }

        // Reach a detail page with an active search.
        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag(BrowseExtensionQueryTag).performTextReplacement("Installed")
        onNodeWithTag(extensionActionTag("store:1::com.example.installed", "open")).performClick()
        onNodeWithTag(ExtensionDetailsTestTag).assertIsDisplayed()

        // Leave for another tab and come back: the detail page is still on the Browse stack.
        navigation.select(NavTab.Updates)
        navigation.select(NavTab.Browse)
        onNodeWithTag(ExtensionDetailsTestTag).assertIsDisplayed()

        // Resize across the breakpoint while staying on the same route.
        width = 720.dp
        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
        onNodeWithTag(ExtensionDetailsTestTag).assertIsDisplayed()
        assertEquals(2, navigation.stackOf(NavTab.Browse).size)

        // Back returns to the filtered list with the query intact.
        navigation.handleBack()
        onNodeWithTag(BrowseExtensionQueryTag).assertIsDisplayed()
        onNodeWithTag(extensionRowTag("store:1::com.example.installed")).assertIsDisplayed()
    }

    @Test
    fun unavailableEntryPointStillReachesBrowse() = runComposeUiTest {
        val navigation = createAppNavigationState(initialTab = NavTab.Library)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false, browseService = null)
            }
        }

        navigation.select(NavTab.Browse)

        onNodeWithTag(BrowseUnavailableTag).assertIsDisplayed()
        // The rest of the shell remains operable.
        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
    }

}
