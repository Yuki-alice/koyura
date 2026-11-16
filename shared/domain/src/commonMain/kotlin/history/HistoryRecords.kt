package app.koyura.domain.history

/**
 * One history row: the latest saved progress for a (sourceKey, workId) pair.
 *
 * Display metadata and [readAtMillis] are best-effort: rows written before
 * metadata tracking carry nulls and are shown with ID fallbacks in a separate
 * "unknown time" group instead of inventing a reading time.
 */
data class HistoryRecord(
    val sourceKey: String,
    val workId: String,
    val chapterId: String?,
    val pageKey: String,
    val pageIndex: Int,
    val workTitle: String?,
    val chapterTitle: String?,
    /** Epoch millis of the last successful save, null when unknown. */
    val readAtMillis: Long?,
)
