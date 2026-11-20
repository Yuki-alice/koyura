package app.koyura.app.reader.chrome

import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.ReaderTapZone
import app.koyura.domain.reader.preferences.OverrideField
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.WorkReaderOverridePatch
import app.koyura.features.reader.comic.chrome.ChapterEntry
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import app.koyura.features.reader.comic.viewer.ComicTapZoneMode
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Settings host checks with a real file store: global defaults, per-work overrides,
 * cross-source isolation, reset, and page stability while settings change (chapter ep_2,
 * 0-based page 2 = the 3rd page).
 */
class ChromeSettingsHostTest {
    private fun freshDir(): String = "chrome-host-${(0..Int.MAX_VALUE).random().toString(36)}"

    private fun newStore(dir: String) = ReaderPreferencesStore(
        directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(dir),
        fileSystem = FileSystem.SYSTEM,
    )

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

    private fun assertStillOnThirdPageOfChapterTwo(state: ReaderChromeState) {
        assertEquals("ep_2", state.chapterId, "settings changes must not move the chapter")
        assertEquals(2, state.pageIndex, "settings changes must not move the page")
    }

    @Test
    fun globalChangePersistsAndKeepsPage() = runTest {
        val dir = freshDir()
        val host = ChromeTestHost(demoState(), newStore(dir))

        host.onAction(
            ReaderChromeAction.ChangeGlobalSettings(
                ReaderSettings(mode = ReadingMode.RIGHT_TO_LEFT),
            ),
        )

        assertEquals(ReadingMode.RIGHT_TO_LEFT, host.state.effective.mode)
        assertStillOnThirdPageOfChapterTwo(host.state)

        // A new store instance on the same directory recovers the default (restart recovery).
        val reopened = newStore(dir)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, reopened.readGlobal().mode)
    }

    @Test
    fun workOverrideMergesIsolatesAndResets() = runTest {
        val dir = freshDir()
        val store = newStore(dir)
        val host = ChromeTestHost(demoState(), store)

        host.onAction(
            ReaderChromeAction.ChangeWorkOverride(
                WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.WEBTOON)),
            ),
        )
        host.onAction(
            ReaderChromeAction.ChangeWorkOverride(
                WorkReaderOverridePatch(cropBorders = OverrideField.Set(true)),
            ),
        )
        assertEquals(ReadingMode.WEBTOON, host.state.effective.mode)
        assertEquals(true, host.state.effective.cropBorders)
        assertStillOnThirdPageOfChapterTwo(host.state)

        // Same title under another source is isolated.
        val other = newStore(dir).resolve("other_source", "demo")
        assertEquals(ReadingMode.LEFT_TO_RIGHT, other.mode)

        // Restart recovery: the override survives a new instance.
        val reopened = newStore(dir).resolve("example_source", "demo")
        assertEquals(ReadingMode.WEBTOON, reopened.mode)
        assertEquals(true, reopened.cropBorders)

        host.onAction(ReaderChromeAction.ResetWorkOverride)
        assertNull(host.state.override)
        assertEquals(ReadingMode.LEFT_TO_RIGHT, host.state.effective.mode)
        assertStillOnThirdPageOfChapterTwo(host.state)
        assertNull(newStore(dir).readOverride("example_source", "demo"))
    }

    @Test
    fun tapZoneFollowsPreferenceChainAndOverrideClearsToGlobal() = runTest {
        val dir = freshDir()
        val store = newStore(dir)
        val host = ChromeTestHost(demoState(), store)

        // 改全局：effective 即切三分区外布局并落盘。
        host.onAction(
            ReaderChromeAction.ChangeGlobalSettings(
                ReaderSettings(
                    tapZone = ReaderTapZone.KINDLE,
                ),
            ),
        )
        assertEquals(
            ComicTapZoneMode.KINDLE,
            host.state.tapZone,
        )
        assertStillOnThirdPageOfChapterTwo(host.state)

        // 覆盖：分区 + 反转走合并；重进阅读器（新会话同仓）保持。
        host.onAction(
            ReaderChromeAction.ChangeWorkOverride(
                WorkReaderOverridePatch(
                    tapZone = OverrideField.Set(ReaderTapZone.LEFT_RIGHT),
                    invertTapZone = OverrideField.Set(true),
                ),
            ),
        )
        assertEquals(
            ComicTapZoneMode.LEFT_RIGHT,
            host.state.tapZone,
        )
        assertEquals(true, host.state.invertTapZone)

        // 清除覆盖回全局。
        host.onAction(
            ReaderChromeAction.ChangeWorkOverride(
                WorkReaderOverridePatch(
                    tapZone = OverrideField.Clear,
                    invertTapZone = OverrideField.Clear,
                ),
            ),
        )
        assertEquals(
            ComicTapZoneMode.KINDLE,
            host.state.tapZone,
        )
        assertEquals(false, host.state.invertTapZone)

        // 落盘可恢复：全局 KINDLE 留存。
        val reopened = newStore(dir)
        assertEquals(
            ReaderTapZone.KINDLE,
            reopened.readGlobal().tapZone,
        )
    }
}
