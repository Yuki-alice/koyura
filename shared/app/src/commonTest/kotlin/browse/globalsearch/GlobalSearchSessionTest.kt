package app.koyura.app.browse.globalsearch

import app.koyura.app.browse.FakeBrowseService
import app.koyura.app.browse.WorkSearchPage
import app.koyura.app.browse.WorkSummary
import app.koyura.app.browse.defaultSources
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 全局搜索扇出：并发分组、部分失败、空结果与单源重试。
 */
class GlobalSearchSessionTest {
    private fun page(title: String) = WorkSearchPage(
        works = listOf(WorkSummary(id = "w-$title", title = title)),
        maxPage = 1,
    )

    @Test
    fun submitFansOutToEverySource() = runTest {
        val browse = FakeBrowseService().apply {
            searchPages = mapOf("星" to page("星图"))
        }
        val session = GlobalSearchSession(browse)
        session.updateQuery(" 星 ")

        session.submit()

        assertTrue(session.hasSearched)
        assertFalse(session.searching)
        assertNull(session.error)
        assertEquals(listOf("src:en:manga", "src:ja:manga"), session.groups.map { it.sourceKey })
        // 每个来源各得一份结果：组间不合并、不覆盖。
        assertEquals(2, session.resultCount)
        session.groups.forEach { group ->
            assertIs<GlobalSearchSourceState.Ready>(group.state)
            assertEquals("星图", group.works.single().title)
        }
        assertEquals(2, browse.calls.filterKeys { it.startsWith("search:") }.values.sum())
    }

    @Test
    fun partialFailureOnlyMarksThatSource() = runTest {
        val browse = FakeBrowseService().apply {
            searchPages = mapOf("星" to page("星图"))
            // 第二来源（ja）搜索失败：键与契约的 calls 键一致。
            failures["search:src:ja:manga:星:1"] = "source offline"
        }
        val session = GlobalSearchSession(browse)
        session.updateQuery("星")

        session.submit()

        val en = session.groups.first { it.sourceKey == "src:en:manga" }
        val ja = session.groups.first { it.sourceKey == "src:ja:manga" }
        assertIs<GlobalSearchSourceState.Ready>(en.state)
        val failed = assertIs<GlobalSearchSourceState.Failed>(ja.state)
        assertEquals("source offline", failed.message)
        assertEquals(1, session.resultCount)
        assertNull(session.error)

        // 重试该源：失败已消费，重试后转为 Ready，其它分组不变。
        session.retry("src:ja:manga")
        assertIs<GlobalSearchSourceState.Ready>(session.groups.first { it.sourceKey == "src:ja:manga" }.state)
        assertEquals(2, session.resultCount)
    }

    @Test
    fun emptyResultsKeepGroupsWithZeroCounts() = runTest {
        val session = GlobalSearchSession(FakeBrowseService())
        session.updateQuery("没有这本")

        session.submit()

        assertEquals(0, session.resultCount)
        assertEquals(defaultSources().size, session.groups.size)
        session.groups.forEach { assertIs<GlobalSearchSourceState.Ready>(it.state) }
    }

    @Test
    fun blankQueryClearsResultsAndSnapshotFailureIsReported() = runTest {
        val browse = FakeBrowseService()
        val session = GlobalSearchSession(browse)
        session.updateQuery("星")
        session.submit()
        assertEquals(2, session.groups.size)

        session.updateQuery("   ")
        session.submit()
        assertFalse(session.hasSearched)
        assertTrue(session.groups.isEmpty())

        // 列源失败：整体错误，不留半截分组。
        browse.loadFailure = "no runtime"
        session.updateQuery("星")
        session.submit()
        assertEquals("no runtime", session.error)
        assertTrue(session.groups.isEmpty())
    }

    @Test
    fun missingServiceReportsNotConnected() = runTest {
        val session = GlobalSearchSession(browse = null)
        session.updateQuery("星")

        session.submit()

        assertEquals("Browse is not connected", session.error)
        assertTrue(session.hasSearched)
    }
}
