package dev.reader.app.browse.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.reader.app.AppScaffold
import dev.reader.app.browse.BrowsePane
import dev.reader.app.browse.BrowseSession
import dev.reader.app.browse.variantRefs
import dev.reader.app.browse.SourceDetailScreen
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * Browse screenshots backed by the production Venera stack.
 *
 * Run with `-PreaderScreenshotDir=shared/app/docs/screenshots`.
 */
class VeneraBrowseScreenshotsTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun preparedSession(): Pair<VeneraHarness, BrowseSession> {
        val harness = VeneraHarness()
        runBlocking {
            harness.service.open()
            harness.service.restore()
        }
        val session = BrowseSession(harness.browse())
        runBlocking {
            session.refresh()
            check(session.addStore("Example", VeneraFixtures.IndexUrl))
            val storeId = session.state.stores.single().id
            session.previewStore(storeId)
            val variant = (session.state.catalogStates[storeId]
                as dev.reader.app.browse.CatalogState.Ready).catalog.variantRefs().single().variant
            check(session.installVariant(variant))
            session.refresh()
        }
        return harness to session
    }

    private fun render(
        name: String,
        width: Int,
        height: Int,
        settleMillis: Long = 0L,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                ReaderTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color.White)) { content() }
                }
            }
            try {
                scene.render(2_000_000_000)
                if (settleMillis > 0) Thread.sleep(settleMillis)
                val image = scene.render(3_000_000_000)
                File(outputDir, "$name.png").writeBytes(
                    image.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                )
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun narrowSourcesInstalled() {
        val (harness, session) = preparedSession()
        try {
            val navigation = createAppNavigationState(initialTab = NavTab.Browse)
            render("browse-venera-narrow-sources", 420, 760) {
                Box(Modifier.size(420.dp, 760.dp)) {
                    AppScaffold(navigation, useRail = false, browseService = null, browseSession = session)
                }
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun wideExtensionsInstalled() {
        val (harness, session) = preparedSession()
        try {
            val navigation = createAppNavigationState(initialTab = NavTab.Browse)
            session.selectPane(BrowsePane.Extensions)
            render("browse-venera-wide-extensions", 1100, 760) {
                Box(Modifier.size(1100.dp, 760.dp)) {
                    AppScaffold(navigation, useRail = true, browseService = null, browseSession = session)
                }
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun wideSourceDetail() {
        val (harness, session) = preparedSession()
        try {
            render("browse-venera-wide-detail", 1100, 760, settleMillis = 4000) {
                Box(Modifier.size(1100.dp, 760.dp)) {
                    SourceDetailScreen(
                        session = session,
                        sourceKey = "example_source",
                        workId = "demo",
                        fallbackTitle = "星图",
                        onBack = {},
                    )
                }
            }
        } finally {
            harness.close()
        }
    }
}
