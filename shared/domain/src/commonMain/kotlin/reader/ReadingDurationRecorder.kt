package app.koyura.domain.reader

/** Receives elapsed foreground reading time for a chapter with a saved position. */
fun interface ReadingDurationRecorder {
    suspend fun recordReadingDuration(sourceKey: String, workId: String, chapterId: String?, elapsedMillis: Long)
}
