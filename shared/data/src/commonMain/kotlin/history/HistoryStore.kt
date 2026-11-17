package app.koyura.data.history

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.domain.history.HistoryRecord

private fun chapterKey(chapterId: String?): String = chapterId ?: ""

/**
 * Reads the latest saved progress per work for the history tab and writes the
 * reading position together with its display metadata.
 *
 * The reading_progress table stays the single source of truth for chapter and
 * page. [save] covers page key, page index, read time and both titles in one
 * SQL statement, so a failed write leaves the previously committed row — and
 * therefore both the detail continue button and the history entry — untouched.
 */
class HistoryStore(driver: SqlDriver) {
    private val db = ExtensionDb(driver)

    /**
     * The newest row per (sourceKey, workId), newest save first. Rows without
     * a recorded time sort last; the UI shows them in a separate group instead
     * of inventing a reading date.
     */
    suspend fun latestEntries(): List<HistoryRecord> =
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

    /**
     * Commits the new position and its metadata atomically.
     *
     * One `INSERT OR REPLACE` carries every column, so either all values become
     * visible or the previous row keeps its page, title and timestamp. Old
     * writers that only know the position keep using `upsertProgress`; their
     * rows gain NULL metadata and show up as "unknown time".
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
    ) {
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
    }

    /**
     * 删除一条历史（整部作品的全部进度行）。调用方刷新后列表即不再出现；
     * 书架收藏行独立存放，不受影响。
     */
    suspend fun deleteEntry(sourceKey: String, workId: String) {
        db.extensionDbQueries.deleteHistoryEntry(source_key = sourceKey, work_id = workId)
    }

    /**
     * 清空全部历史（`reading_progress` 全表）。书架 `library_manga` 与分类归属保留，
     * 清空后仍可从书架续读，重新阅读后条目再次出现。
     */
    suspend fun clearAll() {
        db.extensionDbQueries.clearHistory()
    }
}
