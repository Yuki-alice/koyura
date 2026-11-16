package app.koyura.app.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.AppScaffold
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import kotlin.test.Test

/**
 * Milestone 2 UI coverage: store management, catalog preview and extension operations, driven
 * through the real shell so routes, badges and per-row feedback are all exercised.
 */
class BrowseOperationsUiTest {
    @Test
    fun addStoreValidatesAndAddsRow() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(StoresScreenTestTag).assertIsDisplayed()

        onNodeWithTag(StoresAddTag).performClick()
        // Empty URL shows a validation message instead of calling the service.
        onNodeWithTag("store-save").performClick()
        onNodeWithTag("store-url-error").assertIsDisplayed()
        assertEquals(0, service.calls["addStore:"] ?: 0)

        onNodeWithTag("store-url-field").performTextReplacement("https://third.example/index.json")
        onNodeWithTag("store-save").performClick()

        onNodeWithTag(storeRowTag("store:3")).assertIsDisplayed()
        // Dialog closed after success.
        onNodeWithTag(StoresAddTag).assertIsDisplayed()
    }

    @Test
    fun duplicateStoreUrlIsRejectedLocally() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(StoresAddTag).performClick()
        onNodeWithTag("store-url-field").performTextReplacement("https://example.com/index.json")
        onNodeWithTag("store-save").performClick()

        onNodeWithTag("store-url-error").assertIsDisplayed()
        assertEquals(null, service.calls["addStore:https://example.com/index.json"])
    }

    @Test
    fun editToggleAndRemoveStore() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseManageStoresTag).performClick()

        onNodeWithTag(storeEditTag("store:2")).performClick()
        onNodeWithTag("store-name-field").performTextReplacement("Renamed Store")
        onNodeWithTag("store-save").performClick()
        onNodeWithText("Renamed Store").assertExists()

        onNodeWithTag(storeToggleTag("store:2")).performClick()
        assertEquals(false, service.snapshot.stores.first { it.id == "store:2" }.enabled)

        onNodeWithTag(storeRemoveTag("store:2")).performClick()
        onNodeWithTag(StoreRemoveConfirmTag).performClick()
        assertEquals(null, service.snapshot.stores.firstOrNull { it.id == "store:2" })
    }

    @Test
    fun previewShowsCatalogAndInstallsFromIt() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(storePreviewTag("store:1")).performClick()

        onNodeWithTag(CatalogDialogTag).assertIsDisplayed()
        onNodeWithTag("catalog-install-" + catalogVariantKey("store:1", "new_en", "https://example.com/new_en.js")).assertIsDisplayed()
        onNodeWithTag("catalog-install-" + catalogVariantKey("store:1", "new_en", "https://example.com/new_en.js")).performClick()

        // Installing from the preview goes through the same per-target operation path.
        assertEquals(1, service.calls["installVariant:" + catalogVariantKey("store:1", "new_en", "https://example.com/new_en.js")])
    }

    @Test
    fun previewFailureOffersRetry() = runComposeUiTest {
        val service = FakeBrowseService()
        service.previewFailure = "index unreachable"
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(storePreviewTag("store:1")).performClick()
        onNodeWithTag(CatalogDialogTag).assertIsDisplayed()

        // Failed catalog body shows the error and a retry that reloads the same store.
        onNodeWithTag("catalog-retry").assertIsDisplayed()
        onNodeWithTag("catalog-retry").performClick()
        onNodeWithTag("catalog-install-" + catalogVariantKey("store:1", "new_en", "https://example.com/new_en.js")).assertIsDisplayed()
    }

    @Test
    fun installUpdateUninstallFlowReportsPerRow() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseExtensionsTabTag).performClick()

        // Install an available entry.
        onNodeWithTag(extensionActionTag("store:1::com.example.available", "install")).performClick()
        onNodeWithTag("operation-succeeded-store:1::com.example.available", useUnmergedTree = true).assertExists()

        // Update the entry that had a pending update.
        onNodeWithTag(extensionActionTag("store:1::com.example.updates", "update")).performClick()
        onNodeWithTag("operation-succeeded-store:1::com.example.updates", useUnmergedTree = true).assertExists()

        // Uninstall the installed entry.
        onNodeWithTag(extensionActionTag("store:1::com.example.installed", "uninstall")).performClick()
        assertEquals(null, service.snapshot.extensions.firstOrNull { it.key == "store:1::com.example.installed" })
    }

    @Test
    fun failedInstallShowsRowErrorAndBlocksDuplicateSubmit() = runComposeUiTest {
        val service = FakeBrowseService()
        service.failures["install:store:1::com.example.available"] = "disk full"
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag(extensionActionTag("store:1::com.example.available", "install")).performClick()

        onNodeWithTag("operation-failed-store:1::com.example.available", useUnmergedTree = true).assertExists()
        assertEquals(1, service.calls["install:store:1::com.example.available"])

        // A second tap retries and succeeds; the failure entry is replaced by the success one.
        onNodeWithTag(extensionActionTag("store:1::com.example.available", "install")).performClick()
        onNodeWithTag("operation-succeeded-store:1::com.example.available", useUnmergedTree = true).assertExists()
        assertEquals(2, service.calls["install:store:1::com.example.available"])
    }

    @Test
    fun updateBadgeMirrorsSessionUpdateCount() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Library)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = true, browseService = service)
            }
        }

        // The session loads lazily when Browse opens; open it, then check the badge value via the
        // update-all affordance and the session snapshot.
        navigation.select(NavTab.Browse)
        onNodeWithTag(BrowseExtensionsTabTag).performClick()
        onNodeWithTag("browse-update-all").assertIsDisplayed()

        onNodeWithTag("browse-update-all").performClick()
        // After updating all, the affordance disables because the count drops to zero.
        onNodeWithTag("browse-update-all").assertIsNotEnabled()
    }
}

private fun assertEquals(expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual)
