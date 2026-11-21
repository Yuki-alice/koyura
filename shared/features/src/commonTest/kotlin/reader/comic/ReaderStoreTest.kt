package app.koyura.features.reader.comic

import app.koyura.domain.reader.ReaderProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers

/**
 * 阅读器 Store 单测（阶段 1）：不断言实现细节只断言状态快照，
 * 与 [ReaderSessionTest] 同夹具、同期望（迁移前后行为一致）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderStoreTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createStore(service: FakeReaderService = demoReaderService()): ReaderStore =
        ReaderStoreFactory().create(service)

    private fun openEp1(store: ReaderStore) {
        store.accept(
            ReaderIntent.Open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2")),
        )
    }

    @Test
    fun openReturnsSavedStartPageForMatchingChapter() = runTest(testDispatcher) {
        val service = demoReaderService()
        service.savedProgress["example_source" to "demo"] = ReaderProgress("ep_1", 2)
        val store = createStore(service)

        openEp1(store)
        advanceUntilIdle()

        val content = assertIs<ReaderContentState.Ready>(store.state.content)
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), content.content.pageKeys)
        assertEquals(2, content.content.startPageIndex)
        assertEquals(2, store.state.currentPage)
        store.dispose()
    }

    @Test
    fun openFallsBackToZeroForOtherChapters() = runTest(testDispatcher) {
        val service = demoReaderService()
        service.savedProgress["example_source" to "demo"] = ReaderProgress("ep_1", 3)
        val store = createStore(service)

        store.accept(ReaderIntent.Open("example_source", "demo", "ep_2", listOf("ep_1", "ep_2")))
        advanceUntilIdle()

        val content = assertIs<ReaderContentState.Ready>(store.state.content)
        assertEquals(0, content.content.startPageIndex)
        store.dispose()
    }

    @Test
    fun openFailureSurfacesAndLeavesPageEmpty() = runTest(testDispatcher) {
        val store = createStore(FakeReaderService())

        store.accept(ReaderIntent.Open("example_source", "demo", "missing", emptyList()))
        advanceUntilIdle()

        val failed = assertIs<ReaderContentState.Failed>(store.state.content)
        assertTrue(failed.message.isNotBlank())
        store.dispose()
    }

    @Test
    fun initialOpenNeverWritesProgress() = runTest(testDispatcher) {
        val service = demoReaderService()
        service.savedProgress["example_source" to "demo"] = ReaderProgress("ep_1", 2)
        val store = createStore(service)

        openEp1(store)
        advanceUntilIdle()
        store.accept(ReaderIntent.SaveCurrent)
        advanceUntilIdle()

        assertTrue(service.saveCalls.isEmpty(), "got ${service.saveCalls}")
        store.dispose()
    }

    @Test
    fun saveSkipsAlreadySavedIndex() = runTest(testDispatcher) {
        val service = demoReaderService()
        val store = createStore(service)
        openEp1(store)
        advanceUntilIdle()

        store.accept(ReaderIntent.SavePage(3))
        advanceUntilIdle()
        store.accept(ReaderIntent.SavePage(3))
        advanceUntilIdle()

        assertEquals(
            listOf(FakeReaderService.SaveCall("example_source", "demo", "ep_1", 3)),
            service.saveCalls,
        )
        store.dispose()
    }

    @Test
    fun saveCurrentPersistsDisplayedPage() = runTest(testDispatcher) {
        val service = demoReaderService()
        val store = createStore(service)
        openEp1(store)
        advanceUntilIdle()

        store.accept(ReaderIntent.MarkDisplayed(4))
        advanceUntilIdle()
        store.accept(ReaderIntent.SaveCurrent)
        advanceUntilIdle()

        assertEquals(
            listOf(FakeReaderService.SaveCall("example_source", "demo", "ep_1", 4, isLastPage = true)),
            service.saveCalls,
        )
        store.dispose()
    }

    @Test
    fun ensurePageLoadsOnceAndCaches() = runTest(testDispatcher) {
        val service = demoReaderService()
        val store = createStore(service)
        openEp1(store)
        advanceUntilIdle()

        store.accept(ReaderIntent.EnsurePage("p1"))
        advanceUntilIdle()
        store.accept(ReaderIntent.EnsurePage("p1"))
        advanceUntilIdle()

        assertIs<ReaderPageState.Ready>(store.state.pages["p1"])
        assertEquals(1, service.loadCounts["p1"])
        store.dispose()
    }

    @Test
    fun duplicateRequestDoesNotInvalidatePendingPage() = runTest(testDispatcher) {
        val service = demoReaderService()
        val gate = FakeReaderService.Gate()
        service.gate = gate
        service.gateKey = "page:p1"
        val store = createStore(service)
        openEp1(store)
        advanceUntilIdle()

        store.accept(ReaderIntent.EnsurePage("p1"))
        testScheduler.advanceUntilIdle()
        // 第二次提交在同一帧内到达：inflight 守卫直接丢弃不产生第二次请求。
        store.accept(ReaderIntent.EnsurePage("p1"))
        gate.open()
        advanceUntilIdle()

        assertIs<ReaderPageState.Ready>(store.state.pages["p1"])
        assertEquals(1, service.loadCounts["p1"])
        store.dispose()
    }

    @Test
    fun retryReloadsAfterFailure() = runTest(testDispatcher) {
        val service = demoReaderService()
        service.pageFailures["p2"] = 1
        val store = createStore(service)
        openEp1(store)
        advanceUntilIdle()

        store.accept(ReaderIntent.EnsurePage("p2"))
        advanceUntilIdle()
        assertIs<ReaderPageState.Failed>(store.state.pages["p2"])
        store.accept(ReaderIntent.RetryPage("p2"))
        advanceUntilIdle()
        assertIs<ReaderPageState.Ready>(store.state.pages["p2"])
        assertEquals(2, service.loadCounts["p2"])
        store.dispose()
    }

    @Test
    fun lateOpenResultIsDropped() = runTest(testDispatcher) {
        val service = demoReaderService()
        val gate = FakeReaderService.Gate()
        service.gate = gate
        service.gateKey = "open"
        val store = createStore(service)

        store.accept(ReaderIntent.Open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2")))
        testScheduler.advanceUntilIdle()
        service.gate = null
        store.accept(ReaderIntent.Open("example_source", "demo", "ep_2", listOf("ep_1", "ep_2")))
        gate.open()
        advanceUntilIdle()

        val content = assertIs<ReaderContentState.Ready>(store.state.content)
        assertEquals("ep_2", content.content.chapterId)
        assertEquals(listOf("q0", "q1", "q2"), content.content.pageKeys)
        store.dispose()
    }

    @Test
    fun latePageResultIsDroppedAfterChapterSwitch() = runTest(testDispatcher) {
        val service = demoReaderService()
        val gate = FakeReaderService.Gate()
        service.gate = gate
        service.gateKey = "page:p0"
        val store = createStore(service)
        openEp1(store)
        advanceUntilIdle()

        store.accept(ReaderIntent.EnsurePage("p0"))
        testScheduler.advanceUntilIdle()
        service.gate = null
        store.accept(ReaderIntent.Open("example_source", "demo", "ep_2", listOf("ep_1", "ep_2")))
        gate.open()
        advanceUntilIdle()

        // 迟到的 p0 结果不得漏进新章节的页表。
        assertNull(store.state.pages["p0"])
        store.accept(ReaderIntent.EnsurePage("q0"))
        advanceUntilIdle()
        assertIs<ReaderPageState.Ready>(store.state.pages["q0"])
        val content = assertIs<ReaderContentState.Ready>(store.state.content)
        assertEquals("ep_2", content.content.chapterId)
        store.dispose()
    }

    @Test
    fun chapterNavigationUsesStoredOrder() = runTest(testDispatcher) {
        val store = createStore()
        openEp1(store)
        advanceUntilIdle()

        assertEquals(null, store.state.previousChapterId("ep_1"))
        assertEquals("ep_2", store.state.nextChapterId("ep_1"))
        assertEquals("ep_1", store.state.previousChapterId("ep_2"))
        assertEquals(null, store.state.nextChapterId("ep_2"))
        assertEquals(null, store.state.previousChapterId(null))
        assertEquals(null, store.state.nextChapterId(null))
        store.dispose()
    }
}
