package app.koyura.features.statistics

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
        assertTrue(session.snapshot.trackers.meanScore.isNaN())
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
        // 原版 DateUtils 短单位语义（毫秒入参；零值回 fallback）。
        assertEquals("None", formatReadDuration(0))
        assertEquals("13s", formatReadDuration(13_000))
        assertEquals("4m 13s", formatReadDuration((4 * 60 + 13) * 1000L))
        assertEquals("1h 2m", formatReadDuration((3600 + 2 * 60 + 5) * 1000L))
        assertEquals("1h", formatReadDuration(3600 * 1000L))
        assertEquals("1d 1h", formatReadDuration((86400 + 3600) * 1000L))
        assertEquals("无", formatReadDuration(-5, fallback = "无"))
        // 原版 "%.2f ★" 英式两位小数；无追踪或 NaN 回 N/A。
        assertEquals("N/A", formatMeanScore(Double.NaN, 0))
        assertEquals("N/A", formatMeanScore(7.84, 0))
        assertEquals("7.84 ★", formatMeanScore(7.84, 3))
        assertEquals("8.50 ★", formatMeanScore(8.5, 1))
    }
}
