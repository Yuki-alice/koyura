package app.koyura.features.downloads

import com.arkivanov.mvikotlin.extensions.coroutines.labels as labelFlow
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 下载 Store 单测（阶段 1）：不断言实现细节，只断言状态/Label 序列，
 * 与 [DownloadsSessionTest] 同夹具、同期望（迁移前后行为一致）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsStoreTest {
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
        repository: DownloadQueueRepository = FakeDownloadRepository(FakeDownloadRepository.sampleEntries()),
    ): DownloadsStore = DownloadsStoreFactory().create(repository)

    @Test
    fun refreshLoadsFakeEntriesAndDerivesRunning() = runTest(testDispatcher) {
        val store = createStore()
        val states = mutableListOf<DownloadsState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(DownloadsIntent.Refresh)
        advanceUntilIdle()

        val last = states.last()
        assertIs<DownloadQueueLoadState.Ready>(last.loadState)
        assertEquals(3, last.entries.size)
        assertTrue(last.running)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun defaultFakeIsEmptyLikeWhitepaperScreenshot() = runTest(testDispatcher) {
        val store = createStore(FakeDownloadRepository())
        val states = mutableListOf<DownloadsState>()
        val collect = launch { store.stateFlow.collect(states::add) }

        store.accept(DownloadsIntent.Refresh)
        advanceUntilIdle()

        val last = states.last()
        assertIs<DownloadQueueLoadState.Ready>(last.loadState)
        assertTrue(last.entries.isEmpty())
        assertFalse(last.running)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun pauseResumeRetryCancelFollowStoreSemantics() = runTest(testDispatcher) {
        val store = createStore()
        val states = mutableListOf<DownloadsState>()
        val collect = launch { store.stateFlow.collect(states::add) }
        store.accept(DownloadsIntent.Refresh)
        advanceUntilIdle()

        store.accept(DownloadsIntent.Pause("job-1"))
        advanceUntilIdle()
        assertEquals(FakeDownloadStatus.PAUSED, states.last().entries.first { it.id == "job-1" }.status)

        store.accept(DownloadsIntent.Resume("job-1"))
        advanceUntilIdle()
        assertEquals(FakeDownloadStatus.QUEUED, states.last().entries.first { it.id == "job-1" }.status)

        store.accept(DownloadsIntent.Retry("job-3"))
        advanceUntilIdle()
        assertEquals(FakeDownloadStatus.QUEUED, states.last().entries.first { it.id == "job-3" }.status)

        store.accept(DownloadsIntent.Cancel("job-3"))
        advanceUntilIdle()
        assertTrue(states.last().entries.none { it.id == "job-3" })
        collect.cancel()
        store.dispose()
    }

    @Test
    fun pauseAllAndResumeAllToggleRunning() = runTest(testDispatcher) {
        val store = createStore()
        val states = mutableListOf<DownloadsState>()
        val collect = launch { store.stateFlow.collect(states::add) }
        store.accept(DownloadsIntent.Refresh)
        advanceUntilIdle()

        store.accept(DownloadsIntent.PauseAll)
        advanceUntilIdle()
        assertFalse(states.last().running)
        assertTrue(states.last().entries.none {
            it.status == FakeDownloadStatus.QUEUED || it.status == FakeDownloadStatus.DOWNLOADING
        })

        store.accept(DownloadsIntent.ResumeAll)
        advanceUntilIdle()
        assertTrue(states.last().running)
        collect.cancel()
        store.dispose()
    }

    @Test
    fun failureKeepsSnapshotAndReportsActionError() = runTest(testDispatcher) {
        val store = createStore(FakeDownloadRepository(failure = "boom"))
        val states = mutableListOf<DownloadsState>()
        val labels = mutableListOf<DownloadsLabel>()
        val collectStates = launch { store.stateFlow.collect(states::add) }
        val collectLabels = launch { store.labelFlow.collect(labels::add) }

        store.accept(DownloadsIntent.Refresh)
        advanceUntilIdle()

        val last = states.last()
        assertIs<DownloadQueueLoadState.Failed>(last.loadState)
        assertTrue(last.entries.isEmpty())
        assertEquals("boom", last.actionError)

        store.accept(DownloadsIntent.ClearError)
        advanceUntilIdle()
        assertEquals(null, states.last().actionError)
        collectStates.cancel()
        collectLabels.cancel()
        store.dispose()
    }

    @Test
    fun queueChangesTriggerSilentReload() = runTest(testDispatcher) {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val delegate = FakeDownloadRepository(FakeDownloadRepository.sampleEntries())
        val repository = object : DownloadQueueRepository by delegate {
            override val changes: kotlinx.coroutines.flow.Flow<Unit> = changes
        }
        val store = createStore(repository)
        val states = mutableListOf<DownloadsState>()
        val collect = launch { store.stateFlow.collect(states::add) }
        store.accept(DownloadsIntent.Refresh)
        advanceUntilIdle()
        assertEquals(3, states.last().entries.size)

        delegate.cancel("job-3")
        changes.emit(Unit)
        advanceUntilIdle()

        assertTrue(states.last().entries.none { it.id == "job-3" })
        // 静默刷新不闪骨架屏：loadState 保持 Ready。
        assertIs<DownloadQueueLoadState.Ready>(states.last().loadState)
        collect.cancel()
        store.dispose()
    }
}
