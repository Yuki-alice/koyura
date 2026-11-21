package app.koyura.app.browse

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test

/**
 * 源内搜索分页：首屏只打 `search.load`，列表尾「更多」按 `maxPage` 追加后续页。
 */
class BrowseSearchPagingTest {
    @Test
    fun searchLoadMoreAppendsNextPage() = runComposeUiTest {
        val service = FakeBrowseService()
        service.searchPaged = mapOf(
            "star" to listOf(
                WorkSearchPage(
                    listOf(WorkSummary(id = "w1", title = "星图一")),
                    maxPage = 2,
                ),
                WorkSearchPage(
                    listOf(WorkSummary(id = "w2", title = "星图二")),
                    maxPage = 2,
                ),
            ),
        )
        val session = BrowseSession(service)
        setContent {
            ReaderTheme {
                SourceSearchScreen(
                    session = session,
                    sourceKey = "src",
                    sourceName = "源",
                    onOpenWork = { _, _ -> },
                )
            }
        }

        onNodeWithTag(SourceSearchQueryTag).performTextReplacement("star")
        onNodeWithTag(SourceSearchSubmitTag).performClick()
        waitUntil(timeoutMillis = 5_000) {
            try {
                onNodeWithTag(sourceResultTag("w1")).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }

        // 还有第 2 页：点「更多」后 w1 保留、w2 追加，按钮消失。
        onNodeWithTag(SourceSearchMoreTag).assertExists()
        onNodeWithTag(SourceSearchMoreTag).performClick()
        waitUntil(timeoutMillis = 5_000) {
            try {
                onNodeWithTag(sourceResultTag("w2")).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        onNodeWithTag(sourceResultTag("w1")).assertExists()
        onNodeWithTag(SourceSearchMoreTag).assertDoesNotExist()
    }

    @Test
    fun searchWithoutPagingHidesMoreButton() = runComposeUiTest {
        val service = FakeBrowseService()
        service.searchPages = mapOf(
            "solo" to WorkSearchPage(listOf(WorkSummary(id = "w1", title = "唯一")), maxPage = 1),
        )
        val session = BrowseSession(service)
        setContent {
            ReaderTheme {
                SourceSearchScreen(
                    session = session,
                    sourceKey = "src",
                    sourceName = "源",
                    onOpenWork = { _, _ -> },
                )
            }
        }

        onNodeWithTag(SourceSearchQueryTag).performTextReplacement("solo")
        onNodeWithTag(SourceSearchSubmitTag).performClick()
        waitUntil(timeoutMillis = 5_000) {
            try {
                onNodeWithTag(sourceResultTag("w1")).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        onNodeWithTag(SourceSearchMoreTag).assertDoesNotExist()
    }
}
