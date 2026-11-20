package app.koyura.data.history

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.domain.history.HistoryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private fun chapterKey(chapterId: String?): String = chapterId ?: ""

/**
 * Reads the latest saved progress per work for the history tab and writes the
 * reading position together with its display metadata.
 *
 * The reading_progress table stays the single source of truth for chapter and
 * page. [save] updates the position and display metadata and restores history visibility
 * in one transaction.
 */
class HistoryStore(driver: SqlDriver) {
    private val db = ExtensionDb(driver)

    /**
     * The newest row per (sourceKey, workId), newest save first. Rows without
     * a recorded time sort last; the UI shows them in a separate group instead
     * of inventing a reading date.
     */
    suspend fun latestEntries(): List<HistoryRecord> = withContext(Dispatchers.IO) {
        db.extensionDbQueries.selectHistoryLatest().executeAsList().map { row ->
            HistoryRecord(
                sourceKey = row.source_key,
                workId = row.work_id,
                chapterId = row.chapter_id.ifEmpty { null },
                pageKey = row.page_key,
                pageIndex = row.page_index.toInt(),
                workTitle = row.work_title,
                chapterTitle = row.chapter_title,
                readAtMillis = row.read_at,
            )
        }
    }

    /**
     * Saves the position and metadata and clears the hidden marker atomically.
     * A failed save leaves the previous position, metadata and visibility intact.
     */
    suspend fun save(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        pageKey: String,
        pageIndex: Int,
        readAtMillis: Long,
        workTitle: String?,
        chapterTitle: String?,
    ): Unit = withContext(Dispatchers.IO) {
        db.transaction {
            db.extensionDbQueries.upsertProgressWithMeta(
                source_key = sourceKey,
                work_id = workId,
                chapter_id = chapterKey(chapterId),
                page_key = pageKey,
                page_index = pageIndex.toLong(),
                read_at = readAtMillis,
                work_title = workTitle,
                chapter_title = chapterTitle,
            )
            db.extensionDbQueries.unhideHistoryEntry(source_key = sourceKey, work_id = workId)
        }
    }

    /** Adds time measured by the foreground reader after its first position has been persisted. */
    suspend fun addReadDuration(sourceKey: String, workId: String, chapterId: String?, elapsedMillis: Long) =
        withContext(Dispatchers.IO) {
            require(elapsedMillis >= 0)
            db.extensionDbQueries.addReadDuration(elapsedMillis, sourceKey, workId, chapterKey(chapterId))
        }

    /**
     * 全量最新进度（忽略隐藏标记）：书架继续阅读、详情继续按钮的派生依据。
     * 历史列表展示用 [latestEntries]（尊重隐藏），位置恢复永远读这里，
     * 删除/清空历史不影响任何一处的继续位置。
     */
    suspend fun latestProgress(): Map<Pair<String, String>, HistoryRecord> = withContext(Dispatchers.IO) {
        db.extensionDbQueries.selectHistoryLatestAll().executeAsList().associate { row ->
            (row.source_key to row.work_id) to HistoryRecord(
                sourceKey = row.source_key,
                workId = row.work_id,
                chapterId = row.chapter_id.ifEmpty { null },
                pageKey = row.page_key,
                pageIndex = row.page_index.toInt(),
                workTitle = row.work_title,
                chapterTitle = row.chapter_title,
                readAtMillis = row.read_at,
            )
        }
    }

    /**
     * 删除一条历史：只插隐藏标记，`reading_progress` 进度行原样保留。
     * 调用方刷新后列表即不再出现；书架收藏行独立存放，不受影响；
     * 书架续读与历史恢复读的都是进度行，位置不丢失。
     */
    suspend fun deleteEntry(sourceKey: String, workId: String) = withContext(Dispatchers.IO) {
        db.extensionDbQueries.hideHistoryEntry(source_key = sourceKey, work_id = workId)
    }

    /**
     * 清空全部历史：为当前全部作品插隐藏标记，`reading_progress` 全表保留。
     * 书架 `library_manga` 与分类归属保留，清空后仍可从书架续读，
     * 重新阅读后条目再次出现。
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.extensionDbQueries.hideAllHistory()
    }
}
