package app.koyura.categories

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.library.FakeLibraryRepository
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.library.defaultLibraryCategories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 分类管理页 UI 回归：空态、列表、新建、重命名、删除确认、上下移、隐藏。
 *
 * 数据侧注入内存书架仓储：页面读 `LocalLibraryRepository.current`，与生产同一条真实契约，
 * 断言以仓储提交结果为准（不另建页面级 Fake）。
 */
class CategoriesUiTest {
    private fun repository(categories: Boolean = true): FakeLibraryRepository =
        FakeLibraryRepository(
            categories = if (categories) defaultLibraryCategories() else emptyList(),
        )

    @Test
    fun emptyStateShowsForRepositoryWithoutCategories() = runComposeUiTest {
        val repo = repository(categories = false)
        setContent {
            ReaderTheme {
                CompositionLocalProvider(LocalLibraryRepository provides repo) {
                    CategoriesScreen(onBack = {})
                }
            }
        }
        waitForIdle()

        onNodeWithTag(CategoriesRootTag).assertIsDisplayed()
        onNodeWithTag(CategoriesEmptyTag).assertIsDisplayed()
        onNodeWithTag(CategoriesAddTag).assertIsDisplayed()
        // 空态下没有列表行。
        onNodeWithTag(CategoriesListTag).assertDoesNotExist()
    }

    @Test
    fun listRowsExposeNamesAndMarkers() = runComposeUiTest {
        val repo = repository()
        setContent {
            ReaderTheme {
                CompositionLocalProvider(LocalLibraryRepository provides repo) {
                    CategoriesScreen(onBack = {})
                }
            }
        }
        waitForIdle()

        onNodeWithTag(CategoriesListTag).assertIsDisplayed()
        onNodeWithTag(categoriesRowTag(1L)).assertIsDisplayed()
        onNodeWithTag(categoriesRowTag(2L)).assertIsDisplayed()
        onNodeWithTag(categoriesRowTag(3L)).assertIsDisplayed()
    }

    @Test
    fun createRenameAndDeleteGoThroughRepository() = runComposeUiTest {
        val repo = repository(categories = false)
        setContent {
            ReaderTheme {
                CompositionLocalProvider(LocalLibraryRepository provides repo) {
                    CategoriesScreen(onBack = {})
                }
            }
        }
        waitForIdle()

        // 新建：FAB → 对话框输入 → 保存。
        onNodeWithTag(CategoriesAddTag).performClick()
        waitForIdle()
        onNodeWithTag(CategoriesNameFieldTag).performTextInput("新分类")
        onNodeWithTag(CategoriesDialogConfirmTag).performClick()
        waitForIdle()
        val created = repo.categories.single()
        assertEquals("新分类", created.name)
        onNodeWithTag(categoriesRowTag(created.id)).assertIsDisplayed()

        // 重命名：点行体 → 清空输入 → 保存。
        onNodeWithTag(categoriesRowTag(created.id)).performClick()
        waitForIdle()
        onNodeWithTag(CategoriesNameFieldTag).performTextClearance()
        onNodeWithTag(CategoriesNameFieldTag).performTextInput("改名后")
        onNodeWithTag(CategoriesDialogConfirmTag).performClick()
        waitForIdle()
        assertEquals("改名后", repo.categories.single().name)

        // 删除：行尾删除 → 对话框确认（取消此前不落库）。
        onNodeWithTag(categoriesDeleteTag(created.id)).performClick()
        waitForIdle()
        assertTrue(repo.categories.isNotEmpty())
        onNodeWithTag(CategoriesDeleteConfirmTag).performClick()
        waitForIdle()
        assertTrue(repo.categories.isEmpty())
        onNodeWithTag(CategoriesEmptyTag).assertIsDisplayed()
    }

    @Test
    fun orderButtonsAndHiddenToggleWriteThrough() = runComposeUiTest {
        val repo = repository()
        setContent {
            ReaderTheme {
                CompositionLocalProvider(LocalLibraryRepository provides repo) {
                    CategoriesScreen(onBack = {})
                }
            }
        }
        waitForIdle()

        // 第一行不能上移，最后一行不能下移（按钮禁用），中间行可双向调整。
        onNodeWithTag(categoriesUpTag(1L)).performClick()
        waitForIdle()
        assertNull(repo.calls["setCategoryOrder"])

        onNodeWithTag(categoriesDownTag(1L)).performClick()
        waitForIdle()
        assertEquals(1, repo.calls["setCategoryOrder"])
        // 第一、二行互换后：分类 2 排在分类 1 前。
        val orderedIds = repo.categories.sortedBy { it.order }.map { it.id }
        assertEquals(listOf(2L, 1L, 3L), orderedIds)

        onNodeWithTag(categoriesHideTag(1L)).performClick()
        waitForIdle()
        assertEquals(true, repo.categories.first { it.id == 1L }.isHidden)
        onNodeWithTag(categoriesHideTag(1L)).performClick()
        waitForIdle()
        assertEquals(false, repo.categories.first { it.id == 1L }.isHidden)
    }
}
