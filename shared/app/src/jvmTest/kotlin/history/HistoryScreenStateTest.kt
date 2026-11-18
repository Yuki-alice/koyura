package app.koyura.app.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.app.AppScaffold
import app.koyura.app.NavigationBarTestTag
import app.koyura.app.NavigationRailTestTag
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * History tab states beyond the single-work happy path: two sources owning the same work id,
 * search with no match, and search + scroll retention across the bottom bar / rail switch.
 */
class HistoryScreenStateTest {
    private fun sameDayEntry(
        source: String,
        work: String,
        pageIndex: Int,
        readAt: Long,
        title: String = work,
        chapterTitle: String = "第二章",
    ) = historyEntry(
        sourceKey = source,
        workId = work,
        pageKey = "https://example.invalid/${pageIndex + 1}.png",
        pageIndex = pageIndex,
        workTitle = title,
        chapterTitle = chapterTitle,
        readAtMillis = readAt,
    )

    @Test
    fun sameWorkIdInTwoSourcesRendersTwoIndependentRows() = runComposeUiTest {
        val entries = listOf(
            sameDayEntry("source_a", "demo", pageIndex = 2, readAt = 1_794_916_800_100, title = "星图 A"),
            sameDayEntry("source_b", "demo", pageIndex = 5, readAt = 1_794_916_800_000, title = "星图 B"),
        )
        val session = HistorySession(FakeHistoryService(entries))
        setContent {
            ReaderTheme {
                HistoryScreen(
                    session = session,
                    onOpenWork = { _, _, _ -> },
                    onContinue = { _, _, _ -> },
                )
            }
        }

        onNodeWithTag(historyRowTag("source_a", "demo")).assertIsDisplayed()
        onNodeWithTag(historyRowTag("source_b", "demo")).assertIsDisplayed()
        // Same work id in two sources keeps two independent rows under one date group.
        val groupDate = java.time.Instant.ofEpochMilli(1_794_916_800_100)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        onNodeWithTag(historyGroupTag(groupDate)).assertIsDisplayed()
        onNodeWithTag(historyContinueTag("source_a", "demo")).assertIsDisplayed()
        onNodeWithTag(historyContinueTag("source_b", "demo")).assertIsDisplayed()
        // Same work id, independent progress and titles.
        assertEquals("星图 A", textOf(historyRowTitleTag("source_a", "demo")))
        assertEquals("星图 B", textOf(historyRowTitleTag("source_b", "demo")))
        val first = textOf(historyRowDetailTag("source_a", "demo"))
        val second = textOf(historyRowDetailTag("source_b", "demo"))
        assertTrue("第二章" in first && "3" in first, first)
        assertTrue("第二章" in second && "6" in second, second)
    }

    @Test
    fun emptyHistoryShowsEmptyState() = runComposeUiTest {
        val session = HistorySession(FakeHistoryService(emptyList()))
        setContent {
            ReaderTheme {
                HistoryScreen(session = session, onOpenWork = { _, _, _ -> }, onContinue = { _, _, _ -> })
            }
        }

        onNodeWithTag(HistoryEmptyTag).assertIsDisplayed()
        onNodeWithTag(HistoryListTag).assertDoesNotExist()
    }

    @Test
    fun searchWithoutMatchesShowsNoResultsState() = runComposeUiTest {
        val session = HistorySession(FakeHistoryService(listOf(sameDayEntry("s", "demo", 0, 1_794_916_800_000, title = "星图"))))
        setContent {
            ReaderTheme {
                HistoryScreen(session = session, onOpenWork = { _, _, _ -> }, onContinue = { _, _, _ -> })
            }
        }
        onNodeWithTag(HistoryListTag).assertIsDisplayed()

        onNodeWithTag(HistorySearchToggleTag).performClick()
        onNodeWithTag(HistorySearchTag).performTextInput("does-not-exist")

        onNodeWithTag(HistoryNoResultsTag).assertIsDisplayed()
    }

    @Test
    fun widthSwitchKeepsSearchAndScrollPosition() = runComposeUiTest {
        val entries = (0 until 40).map { index ->
            sameDayEntry(
                source = "source_a",
                work = "work-$index",
                pageIndex = index,
                readAt = 1_794_916_800_000 + index,
                chapterTitle = "第二章",
            )
        }
        val session = HistorySession(FakeHistoryService(entries))
        val navigation = createAppNavigationState(initialTab = NavTab.History)
        var useRail by mutableStateOf(false)
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = if (useRail) 1100.dp else 419.dp, height = 800.dp)) {
                    AppScaffold(
                        navigation = navigation,
                        useRail = useRail,
                        browseService = null,
                        browseSession = null,
                        readerService = null,
                        historyService = null,
                        historySession = session,
                    )
                }
            }
        }
        onNodeWithTag(HistoryListTag).assertIsDisplayed()
        onNodeWithTag(HistorySearchToggleTag).performClick()
        onNodeWithTag(HistorySearchTag).performTextInput("第二章")
        onNodeWithTag(HistoryListTag).performSemanticsAction(SemanticsActions.ScrollToIndex) { it(9) }
        waitForIdle()
        val offset = scrollOffset()
        assertTrue(offset > 0f, "History list must scroll before resizing (offset = $offset)")

        useRail = true
        waitForIdle()

        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
        onNodeWithTag(HistorySearchTag).assertTextContains("第二章")
        assertTrue(session.entries.size == 40)
        assertEquals(offset, scrollOffset(), "Resizing must keep the history scroll position")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textOf(tag: String): String =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollOffset(): Float =
        onNodeWithTag(HistoryListTag).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun tabSwitchKeepsSearchQuery() = runComposeUiTest {
        val session = HistorySession(
            FakeHistoryService(listOf(sameDayEntry("source_a", "demo", 0, 1_794_916_800_000, title = "星图"))),
        )
        val navigation = createAppNavigationState(initialTab = NavTab.History)
        setContent {
            ReaderTheme {
                AppScaffold(
                    navigation = navigation,
                    useRail = false,
                    browseService = null,
                    browseSession = null,
                    readerService = null,
                    historyService = null,
                    historySession = session,
                )
            }
        }
        onNodeWithTag(HistorySearchToggleTag).performClick()
        onNodeWithTag(HistorySearchTag).performTextInput("星")
        onNodeWithTag(historyRowTag("source_a", "demo")).assertIsDisplayed()

        navigation.select(NavTab.Library)
        waitForIdle()
        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
        navigation.select(NavTab.History)
        waitForIdle()

        onNodeWithTag(HistorySearchTag).assertTextContains("星")
        onNodeWithTag(historyRowTag("source_a", "demo")).assertIsDisplayed()
    }

    @Test
    fun tappingRowOpensWorkAndContinueResolvesChapters() = runComposeUiTest {
        val detail = app.koyura.app.browse.WorkDetail(
            id = "demo",
            title = "星图",
            chapters = listOf(
                app.koyura.app.browse.WorkChapter("ep_2", "第二章"),
                app.koyura.app.browse.WorkChapter("ep_1", "第一章"),
            ),
        )
        val browse = object : app.koyura.app.browse.BrowseService by unusedBrowseService() {
            override suspend fun loadWorkDetail(sourceKey: String, workId: String) = detail
        }
        val entry = sameDayEntry("source_a", "demo", 2, 1_794_916_800_000, title = "星图")
        val session = HistorySession(FakeHistoryService(listOf(entry)), browse)
        var opened: Triple<String, String, String>? = null
        var continued: Pair<String?, List<String>>? = null
        setContent {
            ReaderTheme {
                HistoryScreen(
                    session = session,
                    onOpenWork = { sourceKey, workId, title -> opened = Triple(sourceKey, workId, title) },
                    onContinue = { _, ids, _ -> continued = entry.chapterId to ids },
                )
            }
        }

        onNodeWithTag(historyRowTag("source_a", "demo")).performClick()
        assertEquals(Triple("source_a", "demo", "星图"), opened)

        onNodeWithTag(historyContinueTag("source_a", "demo")).performClick()
        waitForIdle()
        assertEquals("ep_2" to listOf("ep_2", "ep_1"), continued)
    }
}

private fun unusedBrowseService(): app.koyura.app.browse.BrowseService =
    object : app.koyura.app.browse.BrowseService {
        override suspend fun loadSnapshot() = throw UnsupportedOperationException()
        override suspend fun addStore(name: String, indexUrl: String) = throw UnsupportedOperationException()
        override suspend fun saveStore(storeId: String, name: String, indexUrl: String) = throw UnsupportedOperationException()
        override suspend fun removeStore(storeId: String) = throw UnsupportedOperationException()
        override suspend fun setStoreEnabled(storeId: String, enabled: Boolean) = throw UnsupportedOperationException()
        override suspend fun previewStore(storeId: String) = throw UnsupportedOperationException()
        override suspend fun installExtension(extensionKey: String) = throw UnsupportedOperationException()
        override suspend fun installCatalogVariant(variant: app.koyura.app.browse.CatalogVariantId) = throw UnsupportedOperationException()
        override suspend fun updateExtension(extensionKey: String) = throw UnsupportedOperationException()
        override suspend fun uninstallExtension(extensionKey: String) = throw UnsupportedOperationException()
        override suspend fun updateAllExtensions() = throw UnsupportedOperationException()
        override suspend fun searchWorks(sourceKey: String, query: String, page: Int) = throw UnsupportedOperationException()
        override suspend fun loadWorkDetail(sourceKey: String, workId: String) = throw UnsupportedOperationException()
    }
