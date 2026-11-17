package app.koyura.app.history

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
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.EncodedImageFormat

/**
 * History tab screenshots rendered from real SQLite data.
 *
 * Run with `-PreaderScreenshotDir=shared/app/docs/screenshots`.
 */
class HistoryScreenshotsTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    @Test
    fun narrowHistory() = withSeededSession { session, history ->
        render("history-narrow", 420, 780) { size ->
            Box(Modifier.size(size)) {
                AppScaffold(
                    navigation = createAppNavigationState(initialTab = NavTab.History),
                    useRail = false,
                    browseService = null,
                    historyService = history,
                    historySession = HistorySession(history),
                )
            }
        }
    }

    @Test
    fun wideHistory() = withSeededSession { session, history ->
        render("history-wide", 1100, 780) { size ->
            Box(Modifier.size(size)) {
                AppScaffold(
                    navigation = createAppNavigationState(initialTab = NavTab.History),
                    useRail = true,
                    browseService = null,
                    historyService = history,
                    historySession = HistorySession(history),
                )
            }
        }
    }

    private fun withSeededSession(block: (ExtensionSession, VeneraHistoryService) -> Unit) {
        val profile = Files.createTempDirectory("reader-history-shot").toFile()
        val session = runBlocking {
            openExtensionSession(
                app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
                    "jdbc:sqlite:${File(profile, "extensions.db").absolutePath}",
                ),
                FileSystem.SYSTEM,
                profile.toOkioPath(),
                HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() },
                VeneraEnvironment("1.16.0", "zh_CN", "desktop"),
            ).also { opened ->
                val now = System.currentTimeMillis()
                opened.history.save(
                    sourceKey = "example_source",
                    workId = "demo",
                    chapterId = "ep_2",
                    pageKey = "https://example.invalid/3.png",
                    pageIndex = 2,
                    readAtMillis = now,
                    workTitle = "星图",
                    chapterTitle = "第二章",
                )
                opened.history.save(
                    sourceKey = "example_source",
                    workId = "night",
                    chapterId = "ep_1",
                    pageKey = "https://example.invalid/1.png",
                    pageIndex = 0,
                    readAtMillis = now - 86_400_000,
                    workTitle = "夜行记",
                    chapterTitle = "第一话",
                )
                opened.history.save(
                    sourceKey = "other_source",
                    workId = "demo",
                    chapterId = null,
                    pageKey = "https://example.invalid/single.png",
                    pageIndex = 4,
                    readAtMillis = now - 2 * 86_400_000,
                    workTitle = "同名单话",
                    chapterTitle = null,
                )
            }
        }
        try {
            block(session, VeneraHistoryService(session.history, session.library))
        } finally {
            runBlocking { session.close() }
            profile.deleteRecursively()
        }
    }

    private fun render(
        name: String,
        width: Int,
        height: Int,
        content: @androidx.compose.runtime.Composable (androidx.compose.ui.unit.DpSize) -> Unit,
    ) {
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                ReaderTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        content(androidx.compose.ui.unit.DpSize(width.dp, height.dp))
                    }
                }
            }
            try {
                // Let the history refresh coroutine land before capturing.
                scene.render(2_000_000_000)
                Thread.sleep(500)
                val image = scene.render(3_000_000_000)
                File(outputDir, "$name.png").writeBytes(
                    image.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                )
            } finally {
                scene.close()
            }
        }
    }
}
