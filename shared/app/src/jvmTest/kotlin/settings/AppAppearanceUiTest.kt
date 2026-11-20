package app.koyura.app.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.koyura.app.App
import app.koyura.app.NavigationBarTestTag
import app.koyura.app.browse.FakeBrowseService
import app.koyura.designsystem.theme.ThemeMode
import app.koyura.features.more.moreRowTag
import app.koyura.features.settings.FakeSettingsRepository
import app.koyura.features.settings.LocalSettingsRepository
import app.koyura.features.settings.SettingsMasterPaneTestTag
import app.koyura.features.settings.SettingsPreferences
import app.koyura.features.settings.SettingsSubpageRootTestTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppAppearanceUiTest {
    @Test
    fun changingAppearanceUpdatesShellAndKeepsTheCurrentRoute() = runComposeUiTest {
        val repository = FakeSettingsRepository(SettingsPreferences(themeMode = ThemeMode.LIGHT))
        setContent {
            Box(Modifier.requiredSize(360.dp, 800.dp)) {
                CompositionLocalProvider(
                    LocalSettingsRepository provides repository,
                    LocalDensity provides Density(1f, fontScale = 2f),
                ) {
                    App(browseService = FakeBrowseService())
                }
            }
        }

        val bar = onNodeWithTag(NavigationBarTestTag)
        val before = bar.captureToImage().toPixelMap()[4, 4].toArgb()
        // Navigation items retain their ordinal meaning and accessible names at large font scales.
        bar.onTabAt(4).performClick()
        onNodeWithTag(moreRowTag("settings")).performScrollTo().performClick()
        onNodeWithTag("$SettingsMasterPaneTestTag-APPEARANCE").performClick()
        onNodeWithTag("settings-theme-mode-DARK").assertIsDisplayed().performClick()
        assertEquals(ThemeMode.DARK, repository.load().themeMode)
        onNodeWithTag("settings-amoled-switch", useUnmergedTree = true).performScrollTo()
            .assertIsDisplayed().performClick()
        val after = bar.captureToImage().toPixelMap()[4, 4].toArgb()
        assertNotEquals(before, after)
        assertEquals(0xFF0C0C0C.toInt(), after)

        onNodeWithTag("settings-custom-seed").performScrollTo().performClick()
        onNodeWithTag(SettingsSubpageRootTestTag).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.onTabAt(index: Int) =
        onChildren()[0].onChildren()[index]
}
