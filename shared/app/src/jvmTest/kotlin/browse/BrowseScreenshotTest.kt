package app.koyura.app.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.koyura.app.AppScaffold
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the Browse surface in both layouts for the delivery screenshots.
 *
 * Run with `-PreaderScreenshotDir=shared/app/docs/screenshots`.
 */
class BrowseScreenshotTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun renderNarrow(name: String) = SwingUtilities.invokeAndWait {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        val scene = ImageComposeScene(width = 420, height = 760, density = Density(1f)) {
            ReaderTheme(darkTheme = false) {
                CompositionLocalProvider(LocalBrowseService provides service) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        AppScaffold(navigation = navigation, useRail = false, browseService = service)
                    }
                }
            }
        }
        try {
            scene.render(2_000_000_000)
            val image = scene.render(2_500_000_000)
            File(outputDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    private fun renderWide(name: String, pane: BrowsePane = BrowsePane.Sources) = SwingUtilities.invokeAndWait {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        val session = BrowseSession(service)
        kotlinx.coroutines.runBlocking { session.refresh() }
        session.selectPane(pane)
        val scene = ImageComposeScene(width = 1100, height = 760, density = Density(1f)) {
            ReaderTheme(darkTheme = false) {
                CompositionLocalProvider(LocalBrowseService provides service) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        AppScaffold(navigation = navigation, useRail = true, browseService = service)
                    }
                }
            }
        }
        try {
            scene.render(2_000_000_000)
            val image = scene.render(2_500_000_000)
            File(outputDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun narrowSources() = renderNarrow("browse-narrow-sources")

    @Test
    fun wideExtensions() = SwingUtilities.invokeAndWait {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        val session = BrowseSession(service)
        kotlinx.coroutines.runBlocking { session.refresh() }
        session.selectPane(BrowsePane.Extensions)
        val scene = ImageComposeScene(width = 1100, height = 760, density = Density(1f)) {
            ReaderTheme(darkTheme = false) {
                CompositionLocalProvider(LocalBrowseService provides service) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        AppScaffold(
                            navigation = navigation,
                            useRail = true,
                            browseService = service,
                            browseSession = session,
                        )
                    }
                }
            }
        }
        try {
            scene.render(2_000_000_000)
            val image = scene.render(2_500_000_000)
            File(outputDir, "browse-wide-extensions.png").writeBytes(
                image.encodeToData(EncodedImageFormat.PNG)!!.bytes,
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun wideStores() = SwingUtilities.invokeAndWait {
        val service = FakeBrowseService()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        val session = BrowseSession(service)
        val scene = ImageComposeScene(width = 1100, height = 760, density = Density(1f)) {
            ReaderTheme(darkTheme = false) {
                CompositionLocalProvider(LocalBrowseService provides service) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        ExtensionStoresScreen(
                            session = session,
                            initialStoreId = null,
                            onBack = {},
                            modifier = Modifier.size(1100.dp, 760.dp),
                        )
                    }
                }
            }
        }
        try {
            kotlinx.coroutines.runBlocking { session.refresh() }
            scene.render(2_000_000_000)
            val image = scene.render(2_500_000_000)
            File(outputDir, "browse-wide-stores.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
