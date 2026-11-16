package dev.reader.data.history

import app.cash.sqldelight.db.SqlDriver
import dev.reader.data.extension.ExtensionDb
import dev.reader.domain.history.HistoryRecord

private fun chapterKey(chapterId: String?): String = chapterId ?: ""

/**
 * Reads the latest saved progress per work for the history tab and stamps
 * display metadata after each successful save.
 *
 * The reading_progress table stays the single source of truth for chapter and
 * page; this store only adds presentation data (titles, timestamps) around it.
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
     * Records when a save happened plus best-effort display titles. Called by
     * the app layer right after a successful progress write so already
     * committed rows stay untouched and old rows keep their NULL timestamp.
     */
    suspend fun stamp(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        readAtMillis: Long,
        workTitle: String?,
        chapterTitle: String?,
    ) {
        db.extensionDbQueries.stampProgressMeta(
            read_at = readAtMillis,
            work_title = workTitle,
            chapter_title = chapterTitle,
            source_key = sourceKey,
            work_id = workId,
            chapter_id = chapterKey(chapterId),
        )
    }
}
