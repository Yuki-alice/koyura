package app.koyura.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.library.LocalProductionSettings
import app.koyura.app.library.ProductionSettingsRepository
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.features.onboarding.OnboardingRootTag
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.uuid.Uuid

/**
 * 首启自动进入引导：生产装配 + 标记缺失时推一次；标记完成后不再打扰。
 */
class OnboardingAutoEnterTest {
    private data class Harness(val session: ExtensionSession, val settings: ProductionSettingsRepository)

    private suspend fun openHarness(root: Path): Harness {
        val session = openExtensionSession(root, VeneraEnvironment("1.16.0", "en", "test"))
        val settings = ProductionSettingsRepository(
            session.library,
            session.readerPreferences,
            session.downloadTriggers,
            session.updateScheduler,
        )
        return Harness(session, settings)
    }

    @Test
    fun freshInstallAutoEntersOnboarding() = runComposeUiTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "onboarding-${Uuid.random()}"
        val harness = runBlocking { openHarness(root) }
        try {
            setContent {
                ReaderTheme {
                    CompositionLocalProvider(LocalProductionSettings provides harness.settings) {
                        AppScaffold(
                            navigation = createAppNavigationState(initialTab = NavTab.Library),
                            useRail = false,
                            enableOnboardingAutoEnter = true,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithTag(OnboardingRootTag).assertExists()
        } finally {
            runBlocking { harness.session.close() }
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun completedOnboardingStaysOnLibrary() = runComposeUiTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "onboarding-done-${Uuid.random()}"
        val harness = runBlocking {
            openHarness(root).also { it.settings.setOnboardingCompleted() }
        }
        try {
            setContent {
                ReaderTheme {
                    CompositionLocalProvider(LocalProductionSettings provides harness.settings) {
                        AppScaffold(
                            navigation = createAppNavigationState(initialTab = NavTab.Library),
                            useRail = false,
                            enableOnboardingAutoEnter = true,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithTag(OnboardingRootTag).assertDoesNotExist()
        } finally {
            runBlocking { harness.session.close() }
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
