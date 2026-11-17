package app.koyura.app.browse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 来源列表视图：语言分组保持不变，最近使用置顶且置顶态独立上报。
 */
class BrowseSourcesViewTest {
    private val sources = listOf(
        SourceInfo(id = "b", name = "Beta", language = "en", versionName = "1.0"),
        SourceInfo(id = "a", name = "Alpha", language = "en", versionName = "1.0"),
        SourceInfo(id = "c", name = "西语", language = "zh", versionName = "1.0"),
    )

    @Test
    fun languageGroupsStaySorted() {
        val sections = sources.groupedSources("")
        assertEquals(listOf("en", "zh"), sections.map { it.language })
        assertEquals(listOf("a", "b"), sections.first { it.language == "en" }.sources.map { it.id })
    }

    @Test
    fun recentListsNewestFirstAndSkipsUnknown() {
        val session = BrowseSession(service = FakeBrowseService())
        session.markSourceOpened("b")
        session.markSourceOpened("missing")
        session.markSourceOpened("a")

        val view = sources.sourcesView("", session.state.recentSourceIds, session.state.pinnedSourceIds)
        // missing 已卸载/不存在，渲染时过滤。
        assertEquals(listOf("a", "b"), view.recent.map { it.id })
    }

    @Test
    fun recentIsCappedAndDeduped() {
        val session = BrowseSession(service = FakeBrowseService())
        repeat(MAX_RECENT_SOURCES + 2) { session.markSourceOpened("s$it") }
        assertEquals(MAX_RECENT_SOURCES, session.state.recentSourceIds.size)
        assertEquals("s${MAX_RECENT_SOURCES + 1}", session.state.recentSourceIds.first())

        session.markSourceOpened("s0")
        assertEquals("s0", session.state.recentSourceIds.first())
        assertEquals(MAX_RECENT_SOURCES, session.state.recentSourceIds.size)
    }

    @Test
    fun queryFiltersRecentAndSections() {
        val session = BrowseSession(service = FakeBrowseService())
        session.markSourceOpened("c")
        val view = sources.sourcesView("beta", session.state.recentSourceIds, emptySet())
        assertTrue(view.recent.isEmpty())
        assertEquals(1, view.sections.single().sources.size)
    }

    @Test
    fun pinToggleIsReportedWithoutReordering() {
        val session = BrowseSession(service = FakeBrowseService())
        session.toggleSourcePinned("b")
        assertEquals(setOf("b"), session.state.pinnedSourceIds)
        val view = sources.sourcesView("", emptyList(), session.state.pinnedSourceIds)
        assertEquals(setOf("b"), view.pinnedIds)
        assertEquals(listOf("a", "b"), view.sections.first { it.language == "en" }.sources.map { it.id })

        session.toggleSourcePinned("b")
        assertTrue(session.state.pinnedSourceIds.isEmpty())
    }
}
