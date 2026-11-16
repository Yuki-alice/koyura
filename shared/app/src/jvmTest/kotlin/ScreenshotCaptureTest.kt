package app.koyura.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.app.navigation.DetailRoute
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the shell offscreen and writes PNGs so reviewers can compare the two layouts.
 *
 * Kept in the test source set so the repository stays buildable on CI without a display server.
 */
class ScreenshotCaptureTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun render(name: String, widthDp: Int, heightDp: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        // Compose Desktop creates AWT-backed resources, which must be touched on the EDT.
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(
                width = widthDp,
                height = heightDp,
                density = Density(1f),
            ) {
                ReaderTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color.White)) { content() }
                }
            }
            try {
                // Let NavDisplay's enter transition settle before capturing.
                scene.render(2_000_000_000)
                val image = scene.render(2_500_000_000)
                File(outputDir, "$name.png").writeBytes(
                    image.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                )
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun captureNarrowShell() {
        render("narrow", 420, 780) {
            Box(Modifier.size(width = 420.dp, height = 780.dp)) { AppShell() }
        }
    }

    @Test
    fun captureWideShell() {
        render("wide", 1100, 780) {
            Box(Modifier.size(width = 1100.dp, height = 780.dp)) { AppShell() }
        }
    }

    @Test
    fun captureWideShellWithChildPage() {
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Browse)
        navigation.push(DetailRoute(NavTab.Browse, 1))
        render("wide-detail", 1100, 780) {
            Box(Modifier.size(width = 1100.dp, height = 780.dp)) {
                AppScaffold(navigation = navigation, useRail = true)
            }
        }
    }
}
