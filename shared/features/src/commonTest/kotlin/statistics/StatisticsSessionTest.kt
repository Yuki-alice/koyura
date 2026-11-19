package app.koyura.statistics

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 统计会话三态与展示串：Ready 读回完整快照、Failed 保留上次结果、取消不算失败。
 */
class StatisticsSessionTest {
    @Test
    fun readyStateCommitsSnapshot() = runTest {
        val repository = FakeStatisticsRepository()
        val session = StatisticsSession(repository)

        assertEquals(StatisticsLoadState.Loading, session.loadState)
        session.refresh()

        assertEquals(StatisticsLoadState.Ready, session.loadState)
        assertEquals(3, session.snapshot.overview.inLibrary)
        assertEquals(9, session.snapshot.chapters.total)
        assertEquals(null, session.snapshot.trackers.meanScore)
        assertEquals(1, repository.loadCount)
    }

    @Test
    fun failedStateKeepsLastCommittedSnapshot() = runTest {
        val repository = FakeStatisticsRepository()
        val session = StatisticsSession(repository)
        session.refresh()

        repository.failure = "boom"
        session.refresh()

        val failed = session.loadState as StatisticsLoadState.Failed
        assertEquals("boom", failed.message)
        // 失败不清空已提交数据，重试即可恢复。
        assertEquals(3, session.snapshot.overview.inLibrary)

        session.refresh()
        assertEquals(StatisticsLoadState.Ready, session.loadState)
    }

    @Test
    fun readDurationAndMeanScoreFormatting() {
        assertEquals("0s", formatReadDuration(0))
        assertEquals("13s", formatReadDuration(13))
        assertEquals("4m 13s", formatReadDuration(4 * 60 + 13))
        assertEquals("1h 2m", formatReadDuration(3600 + 2 * 60 + 5))
        assertEquals("N/A", formatMeanScore(null))
        assertEquals("7.8", formatMeanScore(7.84))
        assertTrue(formatReadDuration(-5) == "0s")
    }
}
