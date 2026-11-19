package app.koyura.app.browse.globalsearch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.browse.FakeBrowseService
import app.koyura.app.browse.WorkSearchPage
import app.koyura.app.browse.WorkSummary
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 全局搜索页结构：搜索框提交、按源分组、部分失败行与重试、结果行点击。
 */
class GlobalSearchUiTest {
    private fun service() = FakeBrowseService().apply {
        searchPages = mapOf(
            "星" to WorkSearchPage(
                works = listOf(WorkSummary(id = "w1", title = "星图", subtitle = "Ch.1")),
                maxPage = 1,
            ),
        )
        failures["search:src:ja:manga:星:1"] = "source offline"
    }

    @Test
    fun groupsResultsBySourceAndOpensWork() = runComposeUiTest {
        val browse = service()
        val session = GlobalSearchSession(browse)
        var opened: Triple<String, String, String>? = null
        setContent {
            ReaderTheme {
                GlobalSearchScreen(
                    session = session,
                    onBack = {},
                    onOpenWork = { sourceKey, workId, title -> opened = Triple(sourceKey, workId, title) },
                )
            }
        }
        waitForIdle()

        onNodeWithTag(GlobalSearchPromptTag).assertIsDisplayed()
        onNodeWithTag(GlobalSearchQueryTag).performTextInput("星")
        onNodeWithTag(GlobalSearchSubmitTag).performClick()
        waitForIdle()

        onNodeWithTag(GlobalSearchListTag).assertIsDisplayed()
        onNodeWithTag(globalSearchGroupTag("src:en:manga")).assertIsDisplayed()
        onNodeWithTag(globalSearchResultTag("src:en:manga", "w1")).assertIsDisplayed()

        // 失败源显示错误行与重试入口。
        onNodeWithTag(globalSearchRetryTag("src:ja:manga")).assertIsDisplayed()

        onNodeWithTag(globalSearchResultTag("src:en:manga", "w1")).performClick()
        assertEquals(Triple("src:en:manga", "w1", "星图"), opened)
    }

    @Test
    fun retryOnlyReRunsFailedSource() = runComposeUiTest {
        val browse = service()
        val session = GlobalSearchSession(browse)
        setContent {
            ReaderTheme {
                GlobalSearchScreen(session = session, onBack = {}, onOpenWork = { _, _, _ -> })
            }
        }
        waitForIdle()

        onNodeWithTag(GlobalSearchQueryTag).performTextInput("星")
        onNodeWithTag(GlobalSearchSubmitTag).performClick()
        waitForIdle()
        onNodeWithTag(globalSearchRetryTag("src:ja:manga")).performClick()
        waitForIdle()

        // 重试成功后该源补上结果，两组都有行。
        onNodeWithTag(globalSearchResultTag("src:ja:manga", "w1")).assertIsDisplayed()
        onNodeWithTag(globalSearchResultTag("src:en:manga", "w1")).assertIsDisplayed()
    }
}
