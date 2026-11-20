package app.koyura.features.statistics

/**
 * 统计页内存数据源（与 FakeUpdatesRepository 同一写法）：默认返回白皮书截图同形的样例，
 * 便于 UI 回归；`failure` 注入后下一次读取抛错，用于三态测试。
 */
class FakeStatisticsRepository(
    private var snapshot: StatisticsSnapshot = sampleSnapshot(),
) : StatisticsRepository {
    var loadCount: Int = 0
        private set

    /** 有值时下一次 load 抛错（用后即焚，便于断言「重试后恢复」）。 */
    var failure: String? = null

    override suspend fun load(): StatisticsSnapshot {
        loadCount += 1
        failure?.let { message ->
            failure = null
            throw IllegalStateException(message)
        }
        return snapshot
    }

    fun update(next: StatisticsSnapshot) {
        snapshot = next
    }

    companion object {
        /** 与 `assets/phone/30_statistics.jpg` 同形的样例数值。 */
        fun sampleSnapshot(): StatisticsSnapshot = StatisticsSnapshot(
            overview = StatisticsOverview(
                inLibrary = 3,
                readDurationSeconds = 4 * 60 + 13,
                completedEntries = 0,
            ),
            entries = StatisticsEntries(inGlobalUpdate = 0, started = 0, local = 3),
            chapters = StatisticsChapters(total = 9, read = 0, downloaded = 0),
            trackers = StatisticsTrackers(trackedEntries = 0, meanScore = null, used = 0),
        )

        fun emptySnapshot(): StatisticsSnapshot = StatisticsSnapshot()
    }
}
