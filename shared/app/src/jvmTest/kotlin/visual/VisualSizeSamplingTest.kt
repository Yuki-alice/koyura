package app.koyura.app.visual

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.components.ChapterDownloadIndicator
import app.koyura.designsystem.components.ChapterDownloadState
import app.koyura.designsystem.components.ModalReaderSheet
import app.koyura.designsystem.components.ReaderSheetCardTag
import app.koyura.features.library.libraryCardTag
import app.koyura.features.settings.FakeSettingsRepository
import app.koyura.features.settings.SettingsMasterPaneTestTag
import app.koyura.features.settings.SettingsScreen
import app.koyura.features.settings.SettingsSession
import app.koyura.features.library.FakeLibraryRepository
import app.koyura.features.library.LibraryEntry
import app.koyura.features.library.LibraryScreen
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.library.defaultLibraryCategories
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T4.1 尺寸采样：把「6 个边缘像素点」升级为**布局尺寸断言**。
 *
 * density 固定 1f，因此语义节点 `size` 的 px 值即 dp 值；每组都在固定宽度容器里渲染，
 * 不依赖真实窗口大小。断言给出 1dp 容差，避免字体/内边距的舍入把整组测试变成噪声。
 */
class VisualSizeSamplingTest {

    private fun ComposeUiTest.widthOf(tag: String): Float =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().size.width.toFloat()

    private fun ComposeUiTest.assertWidth(tag: String, expected: Float, tolerance: Float = 1f) {
        val actual = widthOf(tag)
        assertTrue(
            abs(actual - expected) <= tolerance,
            "$tag 宽度应为 ${expected}dp(±${tolerance})，实测 ${actual}dp",
        )
    }

    private fun demoRepo(): FakeLibraryRepository = FakeLibraryRepository(
        entries = listOf(
            LibraryEntry(
                sourceKey = "srcA", workId = "w1", title = "同名作品",
                coverUrl = "", sourceName = "源A", language = "zh",
                totalChapters = 10, unreadCount = 3, favoriteAtMillis = 10L,
            ),
            LibraryEntry(
                sourceKey = "srcA", workId = "w2", title = "第二部",
                coverUrl = "", sourceName = "源A", language = "zh",
                totalChapters = 4, favoriteAtMillis = 20L,
            ),
            LibraryEntry(
                sourceKey = "srcA", workId = "w3", title = "第三部",
                coverUrl = "", sourceName = "源A", language = "zh",
                totalChapters = 6, favoriteAtMillis = 30L,
            ),
            LibraryEntry(
                sourceKey = "srcA", workId = "w4", title = "第四部",
                coverUrl = "", sourceName = "源A", language = "zh",
                totalChapters = 6, favoriteAtMillis = 40L,
            ),
        ),
        categories = defaultLibraryCategories(),
    )

    /** 手机 360dp：自适应网格落到两列，白皮书实测卡宽 162dp。 */
    @Test
    fun libraryCardWidthAt360() = runComposeUiTest {
        val repo = demoRepo()
        val session = LibrarySession(repo)
        setContent {
            ReaderTheme {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    CompositionLocalProvider(LocalLibraryRepository provides repo) {
                        LibraryScreen(
                            session = session,
                            decodeCover = { null },
                            onOpenWork = { _, _, _ -> },
                            onContinue = {},
                        )
                    }
                }
            }
        }
        waitUntil { session.visibleEntries.isNotEmpty() }
        assertWidth(libraryCardTag("srcA", "w1"), 162f, tolerance = 2f)
    }

    /** 宽屏 1280dp：书架卡按 120dp 上限排布。 */
    @Test
    fun libraryCardWidthAt1280() = runComposeUiTest {
        val repo = demoRepo()
        val session = LibrarySession(repo)
        setContent {
            ReaderTheme {
                Box(Modifier.width(1280.dp).fillMaxHeight()) {
                    CompositionLocalProvider(LocalLibraryRepository provides repo) {
                        LibraryScreen(
                            session = session,
                            decodeCover = { null },
                            onOpenWork = { _, _, _ -> },
                            onContinue = {},
                        )
                    }
                }
            }
        }
        waitUntil { session.visibleEntries.isNotEmpty() }
        assertWidth(libraryCardTag("srcA", "w1"), 120f, tolerance = 2f)
    }

    private fun settingsSession() = SettingsSession(FakeSettingsRepository())

    /** 平板设置双栏：左栏固定 450dp（白皮书 07 §7.14）。 */
    @Test
    fun settingsMasterPaneIs450OnTablet() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.width(1280.dp).fillMaxHeight()) {
                    SettingsScreen(session = settingsSession())
                }
            }
        }
        waitForIdle()
        assertWidth(SettingsMasterPaneTestTag, 450f, tolerance = 1f)
    }

    /** 平板弹层：卡片宽度 = min(屏宽, 460dp)，白皮书实测 458.7dp。 */
    @Test
    fun tabletSheetIsCappedAt460() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.width(1280.dp).fillMaxHeight()) {
                    ModalReaderSheet(onDismissRequest = {}) {
                        Box(Modifier.width(200.dp).size(60.dp))
                    }
                }
            }
        }
        waitForIdle()
        assertWidth(ReaderSheetCardTag, 460f, tolerance = 2f)
    }

    /** 章节下载指示器：直径 26dp（白皮书 06 §6.7）。 */
    @Test
    fun downloadIndicatorIs26() = runComposeUiTest {
        setContent {
            ReaderTheme {
                ChapterDownloadIndicator(
                    state = ChapterDownloadState.NOT_DOWNLOADED,
                    modifier = Modifier.testTag("sampled-indicator"),
                )
            }
        }
        waitForIdle()
        val size = onNodeWithTag("sampled-indicator", useUnmergedTree = true)
            .fetchSemanticsNode().size
        assertTrue(
            abs(size.width - 26f) <= 0.5f && abs(size.height - 26f) <= 0.5f,
            "指示器应为 26×26dp，实测 ${size.width}×${size.height}",
        )
    }
}
