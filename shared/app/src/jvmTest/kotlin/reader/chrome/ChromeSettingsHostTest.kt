package app.koyura.app.reader.chrome

import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.features.reader.comic.chrome.ChapterEntry
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeState
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
                WorkReaderOverride(mode = ReadingMode.WEBTOON),
            ),
        )
        host.onAction(
            ReaderChromeAction.ChangeWorkOverride(
                WorkReaderOverride(cropBorders = true),
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
}
