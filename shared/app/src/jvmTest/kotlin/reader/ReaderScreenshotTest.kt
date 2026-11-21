package app.koyura.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.koyura.app.AppScaffold
import app.koyura.app.browse.BrowseSession
import app.koyura.app.browse.FakeBrowseService
import app.koyura.app.navigation.ComicReaderRoute
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.domain.reader.ChapterContent
import app.koyura.features.reader.comic.FakeReaderService
import app.koyura.features.reader.comic.ReaderStoreFactory
import app.koyura.features.reader.comic.ReaderStoreHolder
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the reader offscreen so reviewers can compare the narrow and wide layouts.
 *
 * Kept in the test source set so the repository stays buildable on CI without a display server.
 */
class ReaderScreenshotTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun sceneServices(): Triple<FakeBrowseService, FakeReaderService, ReaderStoreHolder> {
        val browse = FakeBrowseService()
        val reader = FakeReaderService(
            chapters = mutableMapOf(
                Triple("example_source", "demo", "ep_1") to ChapterContent(
                    chapterId = "ep_1",
                    title = "第一章",
                    pageKeys = listOf("shot0", "shot1", "shot2"),
                    startPageIndex = 0,
                ),
            ),
            pageBytes = mutableMapOf(
                "shot0" to pngBytes(0x2B3A55),
                "shot1" to pngBytes(0x7A4A2B),
                "shot2" to pngBytes(0xB5462B),
            ),
        )
        return Triple(browse, reader, ReaderStoreHolder(ReaderStoreFactory().create(reader)))
    }

    private fun render(name: String, widthDp: Int, heightDp: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        // Compose Desktop creates AWT-backed resources, which must be touched on the EDT.
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(
                width = widthDp,
                height = heightDp,
                density = Density(1f),
            ) {
                ReaderTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
                }
            }
            try {
                // Let the open call and the first page loads settle before capturing.
                scene.render(2_000_000_000)
                scene.render(2_500_000_000)
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
    fun captureNarrowReader() {
        val (browse, reader, holder) = sceneServices()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(
            ComicReaderRoute(
                sourceKey = "example_source",
                workId = "demo",
                chapterId = "ep_1",
                title = "星图",
                chapterIds = listOf("ep_1"),
                chapterTitles = listOf("第一章"),
            ),
        )
        render("reader-narrow", 420, 780) {
            Box(Modifier.size(width = 420.dp, height = 780.dp)) {
                AppScaffold(
                    navigation,
                    useRail = false,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                    readerHolder = holder,
                )
            }
        }
    }

    @Test
    fun captureWideReader() {
        val (browse, reader, holder) = sceneServices()
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(
            ComicReaderRoute(
                sourceKey = "example_source",
                workId = "demo",
                chapterId = "ep_1",
                title = "星图",
                chapterIds = listOf("ep_1"),
                chapterTitles = listOf("第一章"),
            ),
        )
        render("reader-wide", 1280, 800) {
            Box(Modifier.size(width = 1280.dp, height = 800.dp)) {
                AppScaffold(
                    navigation,
                    useRail = true,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                    readerHolder = holder,
                )
            }
        }
    }
}
