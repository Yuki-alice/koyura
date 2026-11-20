package app.koyura.features.reader.comic.chrome

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.domain.reader.preferences.ReaderBackground
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.ReaderTapZone
import app.koyura.domain.reader.preferences.OverrideField
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import app.koyura.features.reader.comic.chrome.ReaderSettingsDialog
import app.koyura.features.reader.comic.chrome.readerChromeGlobalOptionTag
import app.koyura.features.reader.comic.chrome.readerChromeOverrideOptionTag
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
    fun tapZoneWritesGlobalAndInvertToggle() = runComposeUiTest {
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

        // 全局段：点击区分区改走整份替换写偏好链。
        onNodeWithTag(readerChromeGlobalOptionTag("tapZone", "KINDLE"))
            .performScrollTo()
            .performClick()
        val global = actions.filterIsInstance<ReaderChromeAction.ChangeGlobalSettings>().single()
        assertEquals(ReaderTapZone.KINDLE, global.settings.tapZone)
        assertEquals(ReaderSettings().mode, global.settings.mode)

        onNodeWithTag(readerChromeGlobalOptionTag("tapZoneInvert", "false"))
            .performScrollTo()
            .performClick()
        val toggled = actions.filterIsInstance<ReaderChromeAction.ChangeGlobalSettings>().last()
        assertTrue(toggled.settings.invertTapZone)
        assertTrue(actions.isNotEmpty())
    }

    @Test
    fun tapZoneOverridePatchAndClear() = runComposeUiTest {
        val actions = mutableListOf<ReaderChromeAction>()
        setContent {
            ReaderTheme {
                ReaderSettingsDialog(
                    state = state(
                        global = ReaderSettings(tapZone = ReaderTapZone.DEFAULT),
                        override = null,
                    ),
                    onAction = { actions += it },
                    onDismiss = {},
                )
            }
        }
        waitForIdle()

        // 覆盖段：设分区写字段补丁，「跟随全局」清回继承。
        // 注：跟随全局 chip 与 DEFAULT 选项 chip 同 tag（沿用 InheritEnumRow 惯例），
        // 用文本区分。
        onNodeWithTag(readerChromeOverrideOptionTag("tapZone", "LEFT_RIGHT"))
            .performScrollTo()
            .performClick()
        val set = actions.filterIsInstance<ReaderChromeAction.ChangeWorkOverride>().last()
        assertEquals(OverrideField.Set(ReaderTapZone.LEFT_RIGHT), set.patch.tapZone)

        // 覆盖段 tapZone 行：「跟随全局」chip 与 DEFAULT 选项 chip 同 tag
        //（InheritEnumRow 惯例），按组合顺序取首个（跟随全局在前）。
        onAllNodesWithTag(readerChromeOverrideOptionTag("tapZone", "DEFAULT"))[0]
            .performScrollTo()
            .performClick()
        val clear = actions.filterIsInstance<ReaderChromeAction.ChangeWorkOverride>().last()
        assertEquals(OverrideField.Clear, clear.patch.tapZone)

        onNodeWithTag(readerChromeOverrideOptionTag("tapZoneInvert", "false"))
            .performScrollTo()
            .performClick()
        val invert = actions.filterIsInstance<ReaderChromeAction.ChangeWorkOverride>().last()
        assertEquals(OverrideField.Set(false), invert.patch.invertTapZone)
    }
}
