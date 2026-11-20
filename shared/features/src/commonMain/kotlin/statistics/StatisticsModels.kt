package app.koyura.features.statistics

/**
 * 统计页展示模型（白皮书 07 §7.10四张 SectionCard），字段与口径对照原版定稿
 *（`StatsScreenContent.kt` 四个 Section + `StatsData.kt`）：
 *
 * | 本模型 | 原版 `StatsData` | 口径（`StatsScreenModel.kt`） |
 * | --- | --- | --- |
 * | `overview.inLibrary` | `Overview.libraryMangaCount` | 去重书架作品数 |
 * | `overview.readDurationMillis` | `Overview.totalReadDuration`（毫秒） | 累计阅读毫秒 |
 * | `overview.completedEntries` | `Overview.completedMangaCount` | 状态 COMPLETED 计数 |
 * | `entries.inGlobalUpdate` | `Titles.globalUpdateItemCount` | 纳入书架全局更新的作品数 |
 * | `entries.started` | `Titles.startedMangaCount` | `hasStarted` 计数 |
 * | `entries.local` | `Titles.localMangaCount` | 来源为本地的作品数 |
 * | `chapters.total` | `Chapters.totalChapterCount` | 章节总数 |
 * | `chapters.read` | `Chapters.readChapterCount` | 已读章节数 |
 * | `chapters.downloaded` | `Chapters.downloadCount` | 下载管理器计数 |
 * | `trackers.trackedEntries` | `Trackers.trackedTitleCount` | 有追踪记录的作品数 |
 * | `trackers.meanScore` | `Trackers.meanScore`（Double，NaN 表无分） | 仅 `trackedTitleCount > 0 && !isNaN` 时展示 |
 * | `trackers.used` | `Trackers.trackerCount` | 已登录追踪服务数 |
 */
data class StatisticsOverview(
    val inLibrary: Int = 0,
    /** 累计阅读时长（毫秒，对齐原版 `totalReadDuration` 单位）。 */
    val readDurationMillis: Long = 0,
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
    /** 追踪服务给出的平均分；NaN 表无分（原版 `meanScore` 语义，界面显示 N/A）。 */
    val meanScore: Double = Double.NaN,
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
 * 阅读时长展示串（原版 `TimeUtils.toDurationString` 的 DateUtils 短单位语义，
 * 不依赖 Android：`%dd/%dh/%dm/%ds`，非零单位按「天/时/分/秒」规则取舍，
 * 全零回 fallback）。
 *
 * 纯函数便于单测直接断言格式。
 */
fun formatReadDuration(totalMillis: Long, fallback: String = "None"): String {
    val totalSeconds = (totalMillis.coerceAtLeast(0) / 1000).toInt()
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildList {
        if (days != 0) add("${days}d")
        if (hours != 0) add("${hours}h")
        if (minutes != 0 && (days == 0 || hours == 0)) add("${minutes}m")
        if (seconds != 0 && days == 0 && hours == 0) add("${seconds}s")
    }.joinToString(" ").ifBlank { fallback }
}

/** 平均分展示串（原版 `"%.2f ★"` 英文小数两位；无追踪或 NaN 时回 N/A）。 */
fun formatMeanScore(score: Double, trackedCount: Int, notApplicable: String = "N/A"): String {
    if (trackedCount <= 0 || score.isNaN()) return notApplicable
    val rounded = kotlin.math.round(score * 100).toLong()
    val whole = rounded / 100
    val fraction = (rounded % 100).toString().padStart(2, '0')
    return "$whole.$fraction ★"
}
