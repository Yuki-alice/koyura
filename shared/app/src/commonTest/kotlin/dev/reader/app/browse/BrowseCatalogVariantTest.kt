package dev.reader.app.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.reader.app.AppScaffold
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Catalog variant identity: repository, script key and resolved URL stay distinct all the way from
 * preview to the install call, and each row owns its own operation state.
 */
class BrowseCatalogVariantTest {
    private val alphaUrl = "https://example.com/alpha.js"
    private val betaUrl = "https://example.com/beta.js"

    @Test
    fun variantsWithSameKeyKeepDistinctIdentity() {
        val catalog = StoreCatalog(
            storeId = "store:1",
            entries = listOf(
                StoreCatalogEntry("same_key", "Variant Alpha", "1.0.0", "en", sourceKey = "same_key", scriptUrl = alphaUrl),
                StoreCatalogEntry("same_key", "Variant Beta", "2.0.0", "en", sourceKey = "same_key", scriptUrl = betaUrl),
            ),
        )

        val refs = catalog.variantRefs()

        assertEquals(2, refs.size)
        assertEquals(listOf(alphaUrl, betaUrl), refs.map { it.variant.scriptUrl })
        assertEquals(refs.map { it.variant.key }.toSet().size, 2, "variant keys must differ")
        assertTrue(refs.all { it.variant.repositoryId == "store:1" })
        assertTrue(refs.all { it.variant.sourceKey == "same_key" })
    }

    @Test
    fun identicalKeyAndUrlStillGetDistinctOrdinals() {
        val catalog = StoreCatalog(
            storeId = "store:1",
            entries = listOf(
                StoreCatalogEntry("dup", "First", "1.0", "en", scriptUrl = alphaUrl),
                StoreCatalogEntry("dup", "Second", "2.0", "en", scriptUrl = alphaUrl),
            ),
        )

        val refs = catalog.variantRefs()

        assertEquals(listOf(0, 1), refs.map { it.variant.ordinal })
        assertEquals(2, refs.map { it.variant.key }.toSet().size)
    }

    @Test
    fun installingSecondVariantSubmitsSecondIdentity() = runTest {
        val service = FakeBrowseService(
            catalogs = mapOf(
                "store:1" to StoreCatalog(
                    storeId = "store:1",
                    entries = listOf(
                        StoreCatalogEntry("same_key", "Variant Alpha", "1.0.0", "en", scriptUrl = alphaUrl),
                        StoreCatalogEntry("same_key", "Variant Beta", "2.0.0", "en", scriptUrl = betaUrl),
                    ),
                ),
            ),
        )
        val session = BrowseSession(service)
        session.refresh()

        val beta = StoreCatalog(
            storeId = "store:1",
            entries = listOf(
                StoreCatalogEntry("same_key", "Variant Alpha", "1.0.0", "en", scriptUrl = alphaUrl),
                StoreCatalogEntry("same_key", "Variant Beta", "2.0.0", "en", scriptUrl = betaUrl),
            ),
        ).variantRefs()[1]

        assertTrue(session.installVariant(beta.variant))

        assertEquals(betaUrl, service.installedVariants.single().scriptUrl)
        assertEquals("same_key", service.installedVariants.single().sourceKey)
        assertEquals("store:1", service.installedVariants.single().repositoryId)
    }

    @Test
    fun previewShowsBothVariantsAndInstallsTheTappedOne() = runComposeUiTest {
        val alpha = StoreCatalogEntry("same_key", "Variant Alpha", "1.0.0", "en", scriptUrl = alphaUrl)
        val beta = StoreCatalogEntry("same_key", "Variant Beta", "2.0.0", "en", scriptUrl = betaUrl)
        val service = FakeBrowseService(
            catalogs = mapOf("store:1" to StoreCatalog("store:1", listOf(alpha, beta))),
        )
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent { ReaderTheme { AppScaffold(navigation, useRail = true, browseService = service) } }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(storePreviewTag("store:1")).performClick()

        onNodeWithText("Variant Alpha").assertIsDisplayed()
        onNodeWithText("Variant Beta").assertIsDisplayed()

        val betaKey = CatalogVariantId("store:1", "same_key", betaUrl).key
        onNodeWithTag("catalog-install-$betaKey").performClick()

        assertEquals(betaUrl, service.installedVariants.single().scriptUrl)
        assertTrue(service.installedVariants.single().scriptUrl != alphaUrl)
    }

    @Test
    fun previewRowShowsRunningThenSuccess() = runComposeUiTest {
        val entry = StoreCatalogEntry("same_key", "Variant Alpha", "1.0.0", "en", scriptUrl = alphaUrl)
        val service = FakeBrowseService(
            catalogs = mapOf("store:1" to StoreCatalog("store:1", listOf(entry))),
        )
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent { ReaderTheme { AppScaffold(navigation, useRail = true, browseService = service) } }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(storePreviewTag("store:1")).performClick()

        val key = CatalogVariantId("store:1", "same_key", alphaUrl).key
        onNodeWithTag("catalog-install-$key").performClick()

        onNodeWithTag("operation-succeeded-$key", useUnmergedTree = true).assertExists()
        assertEquals(1, service.calls["installVariant:$key"])
    }

    @Test
    fun previewRowFailureRetriesFromTheSameVariant() = runComposeUiTest {
        val entry = StoreCatalogEntry("same_key", "Variant Alpha", "1.0.0", "en", scriptUrl = alphaUrl)
        val service = FakeBrowseService(
            catalogs = mapOf("store:1" to StoreCatalog("store:1", listOf(entry))),
        )
        val key = CatalogVariantId("store:1", "same_key", alphaUrl).key
        service.failures["installVariant:$key"] = "checksum mismatch"

        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent { ReaderTheme { AppScaffold(navigation, useRail = true, browseService = service) } }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(storePreviewTag("store:1")).performClick()
        onNodeWithTag("catalog-install-$key").performClick()

        onNodeWithTag("operation-failed-$key", useUnmergedTree = true).assertExists()
        onNodeWithTag("catalog-retry-$key").performClick()

        onNodeWithTag("operation-succeeded-$key", useUnmergedTree = true).assertExists()
        assertEquals(alphaUrl, service.installedVariants.last().scriptUrl)
    }

    @Test
    fun addStoreForwardsUserName() = runComposeUiTest {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent { ReaderTheme { AppScaffold(navigation, useRail = true, browseService = service) } }

        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(StoresAddTag).performClick()
        onNodeWithTag("store-name-field").performTextReplacement("My Repository")
        onNodeWithTag("store-url-field").performTextReplacement("https://mine.example/index.json")
        onNodeWithTag("store-save").performClick()

        assertEquals("My Repository", service.lastAddedStoreName)
        onNodeWithText("My Repository").assertIsDisplayed()
    }

    @Test
    fun editingStoreUrlInvalidatesOldPreview() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        session.previewStore("store:1")
        assertTrue(session.state.catalogStates["store:1"] is CatalogState.Ready)

        session.saveStore("store:1", "Example", "https://moved.example/index.json")

        assertEquals(null, session.state.catalogStates["store:1"], "old preview must be dropped")
    }

    @Test
    fun editingStoreNameKeepsPreview() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()
        session.previewStore("store:1")

        session.saveStore("store:1", "Renamed", "https://example.com/index.json")

        assertTrue(session.state.catalogStates["store:1"] is CatalogState.Ready)
    }
}
