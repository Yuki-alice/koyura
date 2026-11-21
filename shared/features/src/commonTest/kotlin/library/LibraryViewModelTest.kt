package app.koyura.features.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 阶段 0 样板单测：ViewModel 只是 Session 的 UDF 门面，不复制业务逻辑。
 *
 * 只断言同步 Intent（query/selection）直写 uiState；
 * 异步 refresh 与快照流由既有 LibrarySessionTest 覆盖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoadingWithEmptySelection() {
        val viewModel = LibraryViewModel(FakeLibraryRepository())
        val state = viewModel.uiState.value
        assertIs<LibraryLoadState.Loading>(state.loadState)
        assertTrue(state.selection.isEmpty())
        assertEquals("", state.query)
    }

    @Test
    fun queryAndSelectionAreReflectedInUiState() {
        val fake = FakeLibraryRepository(
            entries = libraryFixtureSnapshot().entries.toMutableList(),
            categories = libraryFixtureSnapshot().categories.toMutableList(),
            links = libraryFixtureSnapshot().links.toMutableMap(),
            prefs = libraryFixtureSnapshot().prefs,
        )
        val viewModel = LibraryViewModel(fake)

        viewModel.updateQuery("同名")
        assertEquals("同名", viewModel.uiState.value.query)

        val key = libraryFixtureSnapshot().entries.first().key
        viewModel.toggleSelect(key)
        assertEquals(setOf(key), viewModel.uiState.value.selection)
        assertTrue(viewModel.uiState.value.wantsBack)

        viewModel.clearSelection()
        assertTrue(viewModel.uiState.value.selection.isEmpty())

        viewModel.selectCategory(1L)
        assertEquals(1L, viewModel.uiState.value.activeCategoryId)
    }

    @Test
    fun nullRepositoryStaysUnavailable() {
        val viewModel = LibraryViewModel(repository = null)
        assertIs<LibraryLoadState.Loading>(viewModel.uiState.value.loadState)
        viewModel.noteError("Library is not connected")
        assertEquals("Library is not connected", viewModel.uiState.value.actionError)
    }
}
