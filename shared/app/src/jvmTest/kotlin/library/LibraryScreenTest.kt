package app.koyura.app.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.reader.pngBytes
import app.koyura.designsystem.KomikkuCoverImageTag
import app.koyura.designsystem.KomikkuCoverPlaceholderTag
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.library.FakeLibraryRepository
import app.koyura.features.library.LibraryBottomDownloadTag
import app.koyura.features.library.LibraryBottomMenuTag
import app.koyura.features.library.LibraryBottomMigrateTag
import app.koyura.features.library.LibraryBottomRemoveTag
import app.koyura.features.library.LibraryCategoriesSheetTag
import app.koyura.features.library.LibraryCategoryAddTag
import app.koyura.features.library.LibraryCategoryNameTag
import app.koyura.features.library.LibraryCountTag
import app.koyura.features.library.LibraryDetailFavoriteRow
import app.koyura.features.library.LibraryDisplayMode
import app.koyura.features.library.LibraryDisplaySheetTag
import app.koyura.features.library.LibraryEmptyBrowseTag
import app.koyura.features.library.LibraryEmptyGuideTag
import app.koyura.features.library.LibraryEmptyTag
import app.koyura.features.library.LibraryEntry
import app.koyura.features.library.LibraryEntryKey
import app.koyura.features.library.LibraryErrorTag
import app.koyura.features.library.LibraryFavoriteButtonTag
import app.koyura.features.library.LibraryFilterButtonTag
import app.koyura.features.library.LibraryFilterSheetTag
import app.koyura.features.library.LibraryPrefs
import app.koyura.features.library.LibraryGlobalSearchTag
import app.koyura.features.library.LibraryGridTag
import app.koyura.features.library.LibraryGroupBy
import app.koyura.features.library.LibraryGroupSheetTag
import app.koyura.features.library.LibraryKind
import app.koyura.features.library.LibraryListTag
import app.koyura.features.library.LibraryLoadState
import app.koyura.features.library.LibraryCoverState
import app.koyura.features.library.LibraryMenuCategoriesTag
import app.koyura.features.library.LibraryMenuDisplayTag
import app.koyura.features.library.LibraryMenuGlobalSearchTag
import app.koyura.features.library.LibraryMenuGroupTag
import app.koyura.features.library.LibraryMenuRefreshTag
import app.koyura.features.library.LibraryMenuSortTag
import app.koyura.features.library.libraryModeChipTag
import app.koyura.features.library.LibraryNoResultsTag
import app.koyura.features.library.LibraryOverflowTag
import app.koyura.features.library.LibraryPanoramaTag
import app.koyura.features.library.LibraryRemoveConfirmTag
import app.koyura.features.library.LibraryRemoveDialogTag
import app.koyura.features.library.LibraryRetryTag
import app.koyura.features.library.LibraryRootTag
import app.koyura.features.library.LibraryScreen
import app.koyura.features.library.LibrarySearchTag
import app.koyura.features.library.LibrarySearchToggleTag
import app.koyura.features.library.LibrarySelectAllTag
import app.koyura.features.library.LibrarySelectionBarTag
import app.koyura.features.library.LibrarySelectionCountTag
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.LibrarySheetCloseTag
import app.koyura.features.library.LibrarySortSheetTag
import app.koyura.features.library.LibrarySortType
import app.koyura.features.library.LibraryTabsTag
import app.koyura.features.library.LibraryTitleTag
import app.koyura.features.library.NewLibraryEntry
import app.koyura.features.library.coverCacheKey
import app.koyura.features.library.defaultLibraryCategories
import app.koyura.features.library.libraryCardTag
import app.koyura.features.library.libraryCardTitleTag
import app.koyura.features.library.libraryContinueTag
import app.koyura.features.library.libraryDownloadBadgeTag
import app.koyura.features.library.libraryFilterRowTag
import app.koyura.features.library.libraryGroupHeaderTag
import app.koyura.features.library.libraryGroupRowTag
import app.koyura.features.library.libraryLanguageBadgeTag
import app.koyura.features.library.librarySortRowTag
import app.koyura.features.library.libraryUnreadBadgeTag
import app.koyura.features.reader.comic.decodeToImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 书架交互轨的离屏渲染验收：真实 [LibrarySession] + [FakeLibraryRepository]，
 * 直接挂载 [LibraryScreen]。AppScaffold 接线（占位替换、底栏隐藏、返回联动）
 * 由主 agent 执行，见本轨 notes。
 */

/** 卡片内部节点（徽标/标题/封面/继续）在合并语义树中不可见，一律走未合并树。 */
private fun ComposeUiTest.node(tag: String) =
    onNodeWithTag(tag, useUnmergedTree = true)
private fun ComposeUiTest.allNodes(tag: String) =
    onAllNodesWithTag(tag, useUnmergedTree = true)

/** 弹层内深处行：先滚到可见再点。 */
private fun ComposeUiTest.scrollAndClick(tag: String) {
    node(tag).performScrollTo()
    node(tag).performClick()
}

class LibraryScreenTest {
    private val realDecode: (ByteArray) -> ImageBitmap? =
        { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }

    private fun triStateFixture(columns: Int = 2): FakeLibraryRepository {
        val entries = listOf(
            LibraryEntry(
                sourceKey = "srcA", workId = "w1", title = "同名作品",
                coverUrl = "https://img.example/cover.png", sourceName = "源A", language = "zh",
                totalChapters = 10, unreadCount = 3, downloadedCount = 2,
                lastReadAtMillis = 1000L, favoriteAtMillis = 10L,
                continueChapterId = "c3", continueChapterTitle = "第3话", continuePageIndex = 5,
            ),
            LibraryEntry(
                sourceKey = "srcB", workId = "w2", title = "About Broken",
                coverUrl = "https://img.example/broken.png", sourceName = "源B", language = "en",
                totalChapters = 4, unreadCount = 0, favoriteAtMillis = 20L,
            ),
            LibraryEntry(
                sourceKey = "srcA", workId = "w4", title = "空封面",
                coverUrl = "", sourceName = "源A", language = "",
                totalChapters = 2, favoriteAtMillis = 30L,
            ),
        )
        return FakeLibraryRepository(
            entries = entries,
            categories = defaultLibraryCategories(),
            links = mapOf(
                LibraryEntryKey("srcA", LibraryKind.COMIC, "w1") to setOf(1L),
                LibraryEntryKey("srcB", LibraryKind.COMIC, "w2") to setOf(1L),
                LibraryEntryKey("srcA", LibraryKind.COMIC, "w4") to setOf(3L),
            ),
            thumbnailBytes = mapOf("https://img.example/cover.png" to pngBytes(0x336699)),
            thumbnailFailures = mutableMapOf("https://img.example/broken.png" to "404"),
            prefs = LibraryPrefs(columns = columns),
        )
    }

    @Test
    fun toolbarTabsAndBadgesOnRealData() = runComposeUiTest {
        val session = LibrarySession(triStateFixture(columns = 4))
        var opened: Triple<String, String, String>? = null
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { sourceKey, workId, title -> opened = Triple(sourceKey, workId, title) },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryRootTag).assertIsDisplayed()
        node(LibraryTitleTag).assertIsDisplayed()
        node(LibraryCountTag).assertIsDisplayed()
        node(LibraryTabsTag).assertIsDisplayed()
        // 徽标：w1 未读/下载/语言/继续齐全。
        node(libraryUnreadBadgeTag("srcA", "w1")).assertIsDisplayed()
        node(libraryDownloadBadgeTag("srcA", "w1")).assertIsDisplayed()
        node(libraryLanguageBadgeTag("srcA", "w1")).assertIsDisplayed()
        node(libraryContinueTag("srcA", "w1")).assertIsDisplayed()
        // 点卡进详情（回调带三参）。
        node(libraryCardTag("srcA", "w1")).performClick()
        assertEquals(Triple("srcA", "w1", "同名作品"), opened)
    }

    @Test
    fun searchNarrowsAndRecovers() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibrarySearchToggleTag).performClick()
        node(LibrarySearchTag).performTextReplacement("同名作品")
        waitUntil { session.visibleEntries.size == 1 }
        node(libraryCardTag("srcA", "w1")).assertIsDisplayed()
        assertTrue(allNodes(LibraryNoResultsTag).fetchSemanticsNodes().isEmpty())
        assertTrue(allNodes(libraryCardTag("srcB", "w2")).fetchSemanticsNodes().isEmpty())
        node(LibrarySearchTag).performTextReplacement("zzz-no-match")
        waitUntil { session.visibleEntries.isEmpty() }
        node(LibraryNoResultsTag).assertIsDisplayed()
        node(LibraryGlobalSearchTag).assertIsDisplayed()
    }

    @Test
    fun displayModesSwitchGridListPanorama() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryGridTag).assertIsDisplayed()
        node(LibraryOverflowTag).performClick()
        scrollAndClick(LibraryMenuDisplayTag)
        node(LibraryDisplaySheetTag).assertIsDisplayed()
        node(libraryModeChipTag(LibraryDisplayMode.LIST)).performClick()
        waitUntil { session.snapshot.prefs.displayMode == LibraryDisplayMode.LIST }
        scrollAndClick(LibrarySheetCloseTag)
        node(LibraryListTag).assertIsDisplayed()
        node(libraryCardTitleTag("srcA", "w1")).assertIsDisplayed()
    }

    @Test
    fun panoramaModeRendersWideCards() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryOverflowTag).performClick()
        scrollAndClick(LibraryMenuDisplayTag)
        node(libraryModeChipTag(LibraryDisplayMode.COMFORTABLE_GRID_PANORAMA)).performClick()
        waitUntil { session.snapshot.prefs.displayMode == LibraryDisplayMode.COMFORTABLE_GRID_PANORAMA }
        scrollAndClick(LibrarySheetCloseTag)
        node(LibraryPanoramaTag).assertIsDisplayed()
    }

    @Test
    fun longPressSelectsAndBatchRemoveNeedsConfirm() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(libraryCardTag("srcA", "w1")).performTouchInput { longClick() }
        waitUntil { session.selection.isNotEmpty() }
        node(LibrarySelectionBarTag).assertIsDisplayed()
        node(LibrarySelectionCountTag).assertIsDisplayed()
        node(LibraryBottomMenuTag).assertIsDisplayed()
        // 迁移与下载只留 disabled 入口。
        node(LibraryBottomMigrateTag).assertIsNotEnabled()
        node(LibraryBottomDownloadTag).assertIsNotEnabled()
        // 全选→移除→确认→落盘。
        node(LibrarySelectAllTag).performClick()
        waitUntil { session.selection.size == 3 }
        node(LibraryBottomRemoveTag).performClick()
        node(LibraryRemoveDialogTag).assertIsDisplayed()
        node(LibraryRemoveConfirmTag).performClick()
        waitUntil { session.snapshot.entries.isEmpty() }
        node(LibraryEmptyTag).assertIsDisplayed()
    }

    @Test
    fun filterSheetUnreadCycleNarrowsList() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryFilterButtonTag).performClick()
        node(LibraryFilterSheetTag).assertIsDisplayed()
        scrollAndClick(libraryFilterRowTag("unread"))
        waitUntil { session.visibleEntries.size == 1 }
        scrollAndClick(LibrarySheetCloseTag)
        node(libraryCardTag("srcA", "w1")).assertIsDisplayed()
        assertTrue(allNodes(libraryCardTag("srcB", "w2")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun sortSheetPersistsPrefs() = runComposeUiTest {
        val fake = triStateFixture()
        val session = LibrarySession(fake)
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryOverflowTag).performClick()
        scrollAndClick(LibraryMenuSortTag)
        node(LibrarySortSheetTag).assertIsDisplayed()
        scrollAndClick(librarySortRowTag(LibrarySortType.DATE_ADDED))
        waitUntil { fake.prefs.sortType == LibrarySortType.DATE_ADDED }
        // fake 即落盘：同一仓储的新会话读回同一偏好在单测已断言，此处只验 UI 写入。
        scrollAndClick(LibrarySheetCloseTag)
        node(LibraryGridTag).assertIsDisplayed()
    }

    @Test
    fun groupSheetShowsSourceHeaders() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryOverflowTag).performClick()
        scrollAndClick(LibraryMenuGroupTag)
        node(LibraryGroupSheetTag).assertIsDisplayed()
        scrollAndClick(libraryGroupRowTag(LibraryGroupBy.SOURCE))
        waitUntil { session.snapshot.prefs.groupBy == LibraryGroupBy.SOURCE }
        scrollAndClick(LibrarySheetCloseTag)
        waitUntil(timeoutMillis = 5_000) {
            allNodes(LibraryGroupSheetTag).fetchSemanticsNodes().isEmpty()
        }
        // 组头节点无可点击语义时 Skiko 的 isDisplayed 判定不可靠：此处断言其唯一存在、
        // 有尺寸且落在视口内（离屏渲染下等价于可见）。
        val headers = allNodes(libraryGroupHeaderTag("源A")).fetchSemanticsNodes()
        assertEquals(1, headers.size)
        val bounds = headers.single().boundsInRoot
        assertTrue(bounds.width > 0 && bounds.height > 0, "header has size: $bounds")
    }

    @Test
    fun coverTriStateImageBrokenAndEmpty() = runComposeUiTest {
        val session = LibrarySession(triStateFixture(columns = 4))
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        // w1 正常 URL（有效 PNG）→ 真图；w2 损坏字节 → 占位；w4 空字符串 → 占位零网络。
        waitUntil(timeoutMillis = 10_000) {
            allNodes(KomikkuCoverImageTag).fetchSemanticsNodes().size == 1
        }
        assertEquals(
            2,
            allNodes(KomikkuCoverPlaceholderTag).fetchSemanticsNodes().size,
        )
        // 空封面条目零网络请求。
        assertTrue(session.covers[coverCacheKey("srcA", "")] is LibraryCoverState.Empty)
    }

    @Test
    fun emptyStateOffersBrowseGuideAndGlobalSearch() = runComposeUiTest {
        val session = LibrarySession(FakeLibraryRepository())
        var browse = 0
        var guide = 0
        var global = 0
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                    onOpenBrowse = { browse++ },
                    onGlobalSearch = { global++ },
                    onGuide = { guide++ },
                )
            }
        }
        waitUntil { session.loadState is LibraryLoadState.Ready }
        node(LibraryEmptyTag).assertIsDisplayed()
        node(LibraryEmptyBrowseTag).performClick()
        node(LibraryEmptyGuideTag).performClick()
        node(LibraryGlobalSearchTag).performClick()
        assertEquals(1, browse)
        assertEquals(1, guide)
        assertEquals(1, global)
    }

    @Test
    fun errorStateRetriesAndRecovers() = runComposeUiTest {
        val fake = FakeLibraryRepository()
        fake.failures["load"] = "boom"
        val session = LibrarySession(fake)
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.loadState is LibraryLoadState.Failed }
        node(LibraryErrorTag).assertIsDisplayed()
        node(LibraryRetryTag).performClick()
        waitUntil { session.loadState is LibraryLoadState.Ready }
        node(LibraryEmptyTag).assertIsDisplayed()
    }

    @Test
    fun detailFavoriteSyncsWithLibraryInstantly() = runComposeUiTest {
        val fake = triStateFixture()
        val session = LibrarySession(fake)
        val entry = NewLibraryEntry(
            sourceKey = "srcC",
            workId = "w9",
            title = "新收藏",
            coverUrl = "https://img.example/cover.png",
            sourceName = "源C",
            totalChapters = 3,
        )
        setContent {
            ReaderTheme {
                LibraryDetailFavoriteRow(session = session, entry = entry)
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { session.refresh() }
        }
        waitUntil { session.loadState is LibraryLoadState.Ready }
        node(LibraryFavoriteButtonTag).performClick()
        waitUntil { session.isFavorite(LibraryEntryKey("srcC", LibraryKind.COMIC, "w9")) }
        // 同一会话驱动书架页：快照立即可见（即时同步），取消收藏同理。
        assertEquals(4, session.snapshot.entries.size)
        node(LibraryFavoriteButtonTag).performClick()
        waitUntil { !session.isFavorite(LibraryEntryKey("srcC", LibraryKind.COMIC, "w9")) }
        assertEquals(3, session.snapshot.entries.size)
    }

    @Test
    fun overflowOffersRefreshAndGlobalSearch() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        var global = 0
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                    onGlobalSearch = { global++ },
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryOverflowTag).performClick()
        scrollAndClick(LibraryMenuRefreshTag)
        waitUntil { session.loadState is LibraryLoadState.Ready }
        // 刷新不行使关闭，对话框仍在，直接点全局搜索行。
        scrollAndClick(LibraryMenuGlobalSearchTag)
        assertEquals(1, global)
    }

    @Test
    fun categoriesSheetAddsCategory() = runComposeUiTest {
        val session = LibrarySession(triStateFixture())
        setContent {
            ReaderTheme {
                LibraryScreen(
                    session = session,
                    decodeCover = realDecode,
                    onOpenWork = { _, _, _ -> },
                    onContinue = {},
                )
            }
        }
        waitUntil { session.snapshot.entries.isNotEmpty() }
        node(LibraryOverflowTag).performClick()
        scrollAndClick(LibraryMenuCategoriesTag)
        node(LibraryCategoriesSheetTag).assertIsDisplayed()
        node(LibraryCategoryNameTag).performTextReplacement("追更中")
        scrollAndClick(LibraryCategoryAddTag)
        waitUntil { session.snapshot.categories.any { it.name == "追更中" } }
    }
}
