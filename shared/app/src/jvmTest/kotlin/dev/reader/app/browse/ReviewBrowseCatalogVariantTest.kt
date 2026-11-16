package dev.reader.app.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.reader.app.AppScaffold
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import kotlin.test.Test

class ReviewBrowseCatalogVariantTest {
    @Test
    fun sameKeyCatalogVariantsAreBothDisplayable() = runComposeUiTest {
        val storeId = "store:1"
        val service = FakeBrowseService(catalogs = mapOf(storeId to StoreCatalog(storeId, listOf(
            StoreCatalogEntry("same_key", "Variant Alpha", "1.0.0", "en"),
            StoreCatalogEntry("same_key", "Variant Beta", "2.0.0", "en"),
        ))))
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        setContent { ReaderTheme { AppScaffold(navigation, useRail = true, browseService = service) } }
        onNodeWithTag(BrowseManageStoresTag).performClick()
        onNodeWithTag(storePreviewTag(storeId)).performClick()
        onNodeWithText("Variant Alpha").assertIsDisplayed()
        onNodeWithText("Variant Beta").assertIsDisplayed()
    }
}
