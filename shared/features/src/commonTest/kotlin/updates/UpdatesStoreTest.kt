package app.koyura.features.updates

import com.arkivanov.mvikotlin.extensions.coroutines.labels as labelFlow
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 更新 Store 单测（阶段 1）：不断言实现细节只断言状态/Label 序列，
 * 与 [UpdatesSessionTest] 同夹具、同期望（迁移前后行为一致）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesStoreTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createStore(
        repository: UpdatesRepository = FakeUpdatesRepository(FakeUpdatesRepository.sampleEntries()),
    ): UpdatesStore = UpdatesStoreFactory().create(repository)

    @Test
    fun refreshLoadsFakeEntriesAndCountsUnread() = runTest(testDispatcher) {
        val store = createStore()
        val states = mutableListOf<UpdatesState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(UpdatesIntent.Refresh)
        advanceUntilIdle()

        val last = states.last()
        assertIs<UpdatesLoadState.Ready>(last.loadState)
        assertEquals(2, last.entries.size)
        assertEquals(3, last.unreadCount)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun defaultFakeIsEmptyLikeWhitepaperScreenshot() = runTest(testDispatcher) {
        val store = createStore(FakeUpdatesRepository())
        val states = mutableListOf<UpdatesState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(UpdatesIntent.Refresh)
        advanceUntilIdle()

        val last = states.last()
        assertIs<UpdatesLoadState.Ready>(last.loadState)
        assertTrue(last.entries.isEmpty())
        assertEquals(0, last.unreadCount)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun failureKeepsSnapshotAndReportsActionError() = runTest(testDispatcher) {
        val store = createStore(FakeUpdatesRepository(failure = "boom"))
        val states = mutableListOf<UpdatesState>()
        val labels = mutableListOf<UpdatesLabel>()
        val collectStates = launch { store.stateFlow.collect(states::add) }
        val collectLabels = launch { store.labelFlow.collect(labels::add) }
        // 非重播的 Label 流必须先确认订阅就绪再发 intent否则 publish 会丢。
        runCurrent()

        store.accept(UpdatesIntent.Refresh)
        advanceUntilIdle()

        val last = states.last()
        assertIs<UpdatesLoadState.Failed>(last.loadState)
        assertTrue(last.entries.isEmpty())
        assertEquals("boom", last.actionError)
        assertEquals(listOf<UpdatesLabel>(UpdatesLabel.Message("boom")), labels)

        store.accept(UpdatesIntent.ClearError)
        advanceUntilIdle()
        assertNull(states.last().actionError)
        collectStates.cancel()
        collectLabels.cancel()
        store.dispose()
    }

    @Test
    fun checkNowUsesRepositoryScanResult() = runTest(testDispatcher) {
        val store = createStore(
            object : UpdatesRepository {
                override suspend fun loadUpdates() = emptyList<UpdateWork>()
                override suspend fun checkNow() = FakeUpdatesRepository.sampleEntries()
            },
        )
        val states = mutableListOf<UpdatesState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(UpdatesIntent.Refresh)
        advanceUntilIdle()
        assertEquals(0, states.last().unreadCount)

        store.accept(UpdatesIntent.CheckNow)
        advanceUntilIdle()
        assertIs<UpdatesLoadState.Ready>(states.last().loadState)
        assertEquals(3, states.last().unreadCount)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun defaultContractDelegatesScanToFeedForFakes() = runTest(testDispatcher) {
        val store = createStore()
        val states = mutableListOf<UpdatesState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(UpdatesIntent.CheckNow)
        advanceUntilIdle()
        store.accept(UpdatesIntent.RetryFailures)
        advanceUntilIdle()

        assertIs<UpdatesLoadState.Ready>(states.last().loadState)
        assertEquals(3, states.last().unreadCount)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun failuresLoadWithSnapshotAndRetryClearsThem() = runTest(testDispatcher) {
        val store = createStore(
            FakeUpdatesRepository(
                FakeUpdatesRepository.sampleEntries(),
                failed = FakeUpdatesRepository.sampleFailures(),
            ),
        )
        val states = mutableListOf<UpdatesState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(UpdatesIntent.Refresh)
        advanceUntilIdle()
        assertIs<UpdatesLoadState.Ready>(states.last().loadState)
        assertEquals(2, states.last().failures.size)

        store.accept(UpdatesIntent.RetryFailures)
        advanceUntilIdle()
        assertIs<UpdatesLoadState.Ready>(states.last().loadState)
        assertTrue(states.last().failures.isEmpty())
        assertEquals(2, states.last().entries.size)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun defaultContractHasNoFailures() = runTest(testDispatcher) {
        val store = createStore()
        val states = mutableListOf<UpdatesState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(UpdatesIntent.Refresh)
        advanceUntilIdle()

        assertTrue(states.last().failures.isEmpty())
        collect.cancel()
        store.dispose()
    }

    @Test
    fun queryFiltersVisibleEntriesByTitle() = runTest(testDispatcher) {
        val store = createStore()
        val states = mutableListOf<UpdatesState>()
        val collect = launch { store.stateFlow.collect(states::add) }
        store.accept(UpdatesIntent.Refresh)
        advanceUntilIdle()
        assertEquals(2, states.last().visibleEntries.size)

        store.accept(UpdatesIntent.UpdateQuery("kaze"))
        advanceUntilIdle()
        assertEquals(listOf("Kaze no Kioku"), states.last().visibleEntries.map { it.title })

        store.accept(UpdatesIntent.UpdateQuery("  HOSHI  "))
        advanceUntilIdle()
        assertEquals(listOf("Hoshizora no Utahime"), states.last().visibleEntries.map { it.title })

        store.accept(UpdatesIntent.UpdateQuery("zzz-no-match"))
        advanceUntilIdle()
        assertTrue(states.last().visibleEntries.isEmpty())
        // unreadCount 仍按全量快照统计不随筛选变。
        assertEquals(3, states.last().unreadCount)

        store.accept(UpdatesIntent.UpdateQuery(""))
        advanceUntilIdle()
        assertEquals(2, states.last().visibleEntries.size)
        collect.cancel()
        store.dispose()
    }
}
