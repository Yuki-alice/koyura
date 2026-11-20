package app.koyura.features.reader.comic.chrome

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.domain.reader.preferences.ReaderBackground
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.OverrideField
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import app.koyura.features.reader.comic.chrome.ReaderSettingsDialog
import app.koyura.features.reader.comic.chrome.readerChromeGlobalOptionTag
import app.koyura.features.reader.comic.chrome.readerChromeOverrideOptionTag
import app.koyura.features.reader.comic.viewer.ComicTapZoneMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 阅读设置对话框：背景四档与点击区选择的读写路径（全局整份替换 / 覆盖字段补丁）。
 */
class ReaderSettingsDialogTest {
    private fun state(
        global: ReaderSettings = ReaderSettings(),
        override: WorkReaderOverride? = null,
        tapZone: ComicTapZoneMode = ComicTapZoneMode.DEFAULT,
        invertTapZone: Boolean = false,
    ) = ReaderChromeState(
        workTitle = "星图",
        chapterId = "c1",
        chapters = emptyList(),
        pageIndex = 0,
        pageCount = 1,
        menuVisible = true,
        hasPrevChapter = false,
        hasNextChapter = false,
        global = global,
        override = override,
        tapZone = tapZone,
        invertTapZone = invertTapZone,
    )

    @Test
    fun globalBackgroundChipReplacesSettings() = runComposeUiTest {
        val actions = mutableListOf<ReaderChromeAction>()
        setContent {
            ReaderTheme {
                ReaderSettingsDialog(
                    state = state(),
                    onAction = { actions += it },
                    onDismiss = {},
                )
            }
        }
        waitForIdle()

        onNodeWithTag(readerChromeGlobalOptionTag("background", "BLACK"))
            .performScrollTo()
            .performClick()

        val action = actions.filterIsInstance<ReaderChromeAction.ChangeGlobalSettings>().single()
        assertEquals(ReaderBackground.BLACK, action.settings.background)
        // 其它字段原样保留（整份替换不改无关项）。
        assertEquals(ReaderSettings().mode, action.settings.mode)
    }

    @Test
    fun overrideBackgroundWritesFieldPatchAndDefaultClears() = runComposeUiTest {
        val actions = mutableListOf<ReaderChromeAction>()
        setContent {
            ReaderTheme {
                ReaderSettingsDialog(
                    state = state(
                        global = ReaderSettings(background = ReaderBackground.GRAY),
                        override = WorkReaderOverride(background = ReaderBackground.WHITE),
                    ),
                    onAction = { actions += it },
                    onDismiss = {},
                )
            }
        }
        waitForIdle()

        onNodeWithTag(readerChromeOverrideOptionTag("background", "BLACK"))
            .performScrollTo()
            .performClick()
        val set = actions.filterIsInstance<ReaderChromeAction.ChangeWorkOverride>().last()
        assertEquals(OverrideField.Set(ReaderBackground.BLACK), set.patch.background)

        onNodeWithTag(readerChromeOverrideOptionTag("background", "DEFAULT"))
            .performScrollTo()
            .performClick()
        val clear = actions.filterIsInstance<ReaderChromeAction.ChangeWorkOverride>().last()
        assertEquals(OverrideField.Clear, clear.patch.background)
    }

    @Test
    fun tapZoneChipEmitsSetTapZoneAndInvertToggle() = runComposeUiTest {
        val actions = mutableListOf<ReaderChromeAction>()
        setContent {
            ReaderTheme {
                ReaderSettingsDialog(
                    state = state(),
                    onAction = { actions += it },
                    onDismiss = {},
                )
            }
        }
        waitForIdle()

        onNodeWithTag(readerChromeGlobalOptionTag("tapZone", "KINDLE"))
            .performScrollTo()
            .performClick()
        assertEquals(
            ReaderChromeAction.SetTapZone(ComicTapZoneMode.KINDLE),
            actions.filterIsInstance<ReaderChromeAction.SetTapZone>().single(),
        )

        onNodeWithTag(readerChromeGlobalOptionTag("tapZoneInvert", "false"))
            .performScrollTo()
            .performClick()
        assertEquals(
            ReaderChromeAction.SetInvertTapZone(true),
            actions.filterIsInstance<ReaderChromeAction.SetInvertTapZone>().single(),
        )
        assertTrue(actions.isNotEmpty())
    }
}
