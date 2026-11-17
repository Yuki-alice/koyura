package app.koyura.app.reader.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.OverrideField
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.WorkReaderOverridePatch
import app.koyura.features.reader.comic.chrome.ChapterEntry
import app.koyura.features.reader.comic.chrome.ReaderChrome
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeDialog
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Offscreen chrome captures for reviewers: narrow/wide, light/dark, every dialog, plus the
 * scripted flow (open menu -> jump -> pick chapter -> set work override -> reset default).
 * Screenshots land in -PreaderScreenshotDir (default build/screenshots).
 */
class ChromeScreenshots {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun demoState() = ReaderChromeState(
        workTitle = "星图",
        chapterId = "ep_2",
        chapters = listOf(ChapterEntry("ep_1", "第一章"), ChapterEntry("ep_2", "第二章")),
        pageIndex = 2,
        pageCount = 3,
        menuVisible = true,
        hasPrevChapter = true,
        hasNextChapter = false,
        global = ReaderSettings(),
        override = null,
    )

    private fun render(name: String, widthDp: Int, heightDp: Int, dark: Boolean, state: ReaderChromeState) {
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(
                width = widthDp,
                height = heightDp,
                density = Density(1f),
            ) {
                ReaderTheme(darkTheme = dark) {
                    // Gray stand-in for the viewer canvas behind the chrome.
                    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF1A1A1A) else Color(0xFF8A8A8A))) {
                        Box(Modifier.size(width = widthDp.dp, height = heightDp.dp)) {
                            ReaderChrome(state = state, onAction = {})
                        }
                    }
                }
            }
            try {
                scene.render(500_000_000)
                val image = scene.render(1_500_000_000)
                File(outputDir, "$name.png").writeBytes(
                    image.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                )
            } finally {
                scene.close()
            }
        }
    }

    private fun captureAll(baseName: String, state: ReaderChromeState) {
        render("${baseName}-narrow-light", 420, 780, dark = false, state)
        render("${baseName}-wide-light", 1280, 800, dark = false, state)
        render("${baseName}-narrow-dark", 420, 780, dark = true, state)
        render("${baseName}-wide-dark", 1280, 800, dark = true, state)
    }

    @Test
    fun captureMenuHidden() {
        captureAll("chrome-hidden", demoState().copy(menuVisible = false))
    }

    @Test
    fun captureMenuOpen() {
        captureAll("chrome-open", demoState())
    }

    @Test
    fun captureChapterList() {
        captureAll("chrome-chapters", demoState().copy(dialog = ReaderChromeDialog.ChapterList))
    }

    @Test
    fun captureReadingMode() {
        // 有作品覆盖：条漫选中。
        captureAll(
            "chrome-mode",
            demoState().copy(
                override = WorkReaderOverride(mode = ReadingMode.WEBTOON),
                dialog = ReaderChromeDialog.ReadingMode,
            ),
        )
    }

    @Test
    fun captureModeDefault() {
        // 跟随全局：无作品覆盖，Default 行选中。
        captureAll("chrome-mode-default", demoState().copy(dialog = ReaderChromeDialog.ReadingMode))
    }

    @Test
    fun captureSettings() {
        captureAll(
            "chrome-settings",
            demoState().copy(
                override = WorkReaderOverride(mode = ReadingMode.WEBTOON),
                dialog = ReaderChromeDialog.Settings,
            ),
        )
    }

    @Test
    fun captureScriptedFlow() {
        // 打开菜单 -> 跳页 -> 选章 -> 设置作品覆盖 -> 恢复默认：每步一张窄屏浅色截图。
        val host = ChromeTestHost(demoState().copy(menuVisible = false))
        fun snap(step: String) = render("chrome-flow-$step", 420, 780, dark = false, host.state)

        host.onAction(ReaderChromeAction.SetMenuVisible(true))
        snap("1-menu-open")
        host.onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.PageJump))
        snap("2-jump")
        host.onAction(ReaderChromeAction.JumpToPage(0))
        host.onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.ChapterList))
        snap("3-chapters")
        host.onAction(ReaderChromeAction.OpenChapter("ep_1"))
        host.onAction(
            ReaderChromeAction.ChangeWorkOverride(
                WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.RIGHT_TO_LEFT)),
            ),
        )
        host.onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.Settings))
        snap("4-override")
        host.onAction(ReaderChromeAction.ResetWorkOverride)
        snap("5-reset")
    }
}
