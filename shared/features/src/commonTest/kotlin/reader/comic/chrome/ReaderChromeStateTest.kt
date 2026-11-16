package app.koyura.features.reader.comic.chrome

import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.WorkReaderOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun demoState() = ReaderChromeState(
    workTitle = "星图",
    chapterId = "ep_2",
    chapters = listOf(ChapterEntry("ep_1", "第一章"), ChapterEntry("ep_2", "第二章")),
    pageIndex = 2,
    pageCount = 5,
    menuVisible = true,
    hasPrevChapter = true,
    hasNextChapter = false,
    global = ReaderSettings(),
    override = null,
)

class ReaderChromeStateTest {
    @Test
    fun currentChapterIndexMatchesChapterId() {
        assertEquals(1, demoState().currentChapterIndex)
        assertEquals(-1, demoState().copy(chapterId = "missing").currentChapterIndex)
    }

    @Test
    fun prevNextChapterIdsFollowListOrder() {
        val state = demoState()
        assertEquals("ep_1", prevChapterId(state))
        assertNull(nextChapterId(state))
        assertNull(prevChapterId(state.copy(chapterId = "ep_1")))
        assertEquals("ep_2", nextChapterId(state.copy(chapterId = "ep_1")))
    }

    @Test
    fun clampedPageStaysInRange() {
        val state = demoState()
        assertEquals(0, state.clampedPage(-3))
        assertEquals(4, state.clampedPage(99))
        assertEquals(2, state.clampedPage(2))
        assertEquals(0, state.copy(pageCount = 0).clampedPage(5))
    }

    @Test
    fun effectiveSettingsCombineGlobalAndOverride() {
        val state = demoState().copy(
            global = ReaderSettings(mode = ReadingMode.LEFT_TO_RIGHT),
            override = WorkReaderOverride(mode = ReadingMode.WEBTOON),
        )
        assertEquals(ReadingMode.WEBTOON, state.effective.mode)
        assertEquals(ReaderSettings().layout, state.effective.layout)
    }
}
