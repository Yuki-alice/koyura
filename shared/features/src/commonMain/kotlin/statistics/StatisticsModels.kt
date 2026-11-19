package app.koyura.features.statistics

/**
 * 统计页展示模型（白皮书 07 §7.10，四张 SectionCard）。
 *
 * 字段按白皮书截图 `assets/phone/30_statistics.jpg` 的标签逐项对齐：
 * Overview（在书架 / 阅读时长 / 已读完）、Entries（全局更新中 / 已开始 / 本地）、
 * Chapters（总数 / 已读 / 已下载）、Trackers（追踪条目 / 平均分 / 使用数）。
 * 参考仓库钉死版本没有统计页实现（`reference/komikku` 于 `290267d` 只有 ExH 的
 * `StatisticsDto`），因此形状以白皮书标签为准，未自造额外维度。
 */
data class StatisticsOverview(
    val inLibrary: Int = 0,
    /** 累计阅读时长（秒）。 */
    val readDurationSeconds: Long = 0,
    val completedEntries: Int = 0,
)

data class StatisticsEntries(
    val inGlobalUpdate: Int = 0,
    val started: Int = 0,
    val local: Int = 0,
)

data class StatisticsChapters(
    val total: Int = 0,
    val read: Int = 0,
    val downloaded: Int = 0,
)

data class StatisticsTrackers(
    val trackedEntries: Int = 0,
    /** 追踪服务给出的平均分；未接追踪服务时为 null（界面显示 N/A）。 */
    val meanScore: Double? = null,
    val used: Int = 0,
)

data class StatisticsSnapshot(
    val overview: StatisticsOverview = StatisticsOverview(),
    val entries: StatisticsEntries = StatisticsEntries(),
    val chapters: StatisticsChapters = StatisticsChapters(),
    val trackers: StatisticsTrackers = StatisticsTrackers(),
)

/** 统计数据源；生产实现由数据层聚合后注入，缺省为 Fake（与更新/下载同一纪律）。 */
interface StatisticsRepository {
    suspend fun load(): StatisticsSnapshot
}

/** 统计加载态（对齐 Updates/Library 的三态）。 */
sealed interface StatisticsLoadState {
    data object Loading : StatisticsLoadState
    data object Ready : StatisticsLoadState
    data class Failed(val message: String) : StatisticsLoadState
}

/**
 * 阅读时长展示串（白皮书截图 `4m 13s`）：不足 1 分钟显示秒，满 1 小时显示时分。
 * 纯函数，便于单测直接断言格式。
 */
fun formatReadDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${remainder}s"
        else -> "${remainder}s"
    }
}

/** 平均分展示串：null → `N/A`，否则保留一位小数。 */
fun formatMeanScore(score: Double?): String =
    if (score == null) "N/A" else ((score * 10).toInt() / 10.0).toString()
