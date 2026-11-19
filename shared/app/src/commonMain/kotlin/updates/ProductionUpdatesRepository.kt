package app.koyura.app.updates

import app.koyura.domain.updates.DetectedChapter
import app.koyura.domain.updates.LibraryUpdates
import app.koyura.domain.updates.WorkUpdate
import app.koyura.domain.updates.UpdateFailure as DomainUpdateFailure
import app.koyura.features.updates.UpdateChapter
import app.koyura.features.updates.UpdateWork
import app.koyura.features.updates.UpdatesRepository
import app.koyura.features.updates.UpdateFailure
import kotlin.time.Clock

/**
 * 生产更新仓储：数据层 [LibraryUpdates]（扫描引擎）到界面层 [UpdatesRepository] 的唯一桥。
 *
 * - `loadUpdates` 读上次扫描的持久 feed；`checkNow`/`retryFailures` 调扫描器并返回新 feed。
 * - 扫描失败列表（`UpdateScan.failures`）暂不进界面契约，更新错误列表 UI 随下一轮接入；
 *   整次扫描抛错时经会话的 `Failed` 态呈现。
 * - `detectedAtMillis` 映射为相对日期串（Today/Yesterday/N days ago），与 Fake 文案同形。
 */
class ProductionUpdatesRepository(
    private val updates: LibraryUpdates,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : UpdatesRepository {

    override suspend fun loadUpdates(): List<UpdateWork> =
        updates.feed().map { it.toFeature(nowMillis()) }

    override suspend fun checkNow(): List<UpdateWork> =
        updates.checkNow().updates.map { it.toFeature(nowMillis()) }

    override suspend fun retryFailures(): List<UpdateWork> =
        updates.retryFailures().updates.map { it.toFeature(nowMillis()) }

    override suspend fun failures(): List<UpdateFailure> =
        updates.failures().map { failure ->
            UpdateFailure(
                sourceKey = failure.work.sourceKey,
                workId = failure.work.workId,
                title = failure.work.title,
                message = failure.message,
            )
        }

    private fun WorkUpdate.toFeature(now: Long): UpdateWork = UpdateWork(
        sourceKey = work.sourceKey,
        workId = work.workId,
        title = work.title,
        coverUrl = work.coverUrl,
        chapters = newChapters.map { it.toFeature(now) },
    )

    private fun DetectedChapter.toFeature(now: Long): UpdateChapter = UpdateChapter(
        id = id,
        title = title,
        dateText = relativeDate(now, detectedAtMillis),
    )
}

/** 与 Fake 文案同形的相对日期：检出当天 → Today，前一天 → Yesterday，更早按天数。 */
internal fun relativeDate(nowMillis: Long, atMillis: Long): String {
    val days = ((nowMillis - atMillis) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
    return when (days) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> "$days days ago"
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
