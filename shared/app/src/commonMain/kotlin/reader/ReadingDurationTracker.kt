package app.koyura.app.reader

import app.koyura.domain.reader.ReadingDurationRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class ReadingDurationTracker(
    private val recorder: ReadingDurationRecorder,
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    data class Chapter(val sourceKey: String, val workId: String, val chapterId: String?)

    private val lock = Mutex()
    private var visible = false
    private var resumed = false
    private var chapter: Chapter? = null
    private var start: TimeMark? = null

    suspend fun setVisibility(visible: Boolean, resumed: Boolean) = lock.withLock {
        if (this.visible == visible && this.resumed == resumed) return@withLock
        checkpoint()
        this.visible = visible
        this.resumed = resumed
        if (!visible) chapter = null
        restart()
    }

    suspend fun setChapter(value: Chapter?) = lock.withLock {
        checkpoint()
        chapter = value
        restart()
    }

    suspend fun positionSaved(value: Chapter) = lock.withLock {
        if (value == chapter) checkpoint()
    }

    private fun restart() {
        start = if (visible && resumed && chapter != null) clock.markNow() else null
    }

    private suspend fun checkpoint() {
        val target = chapter ?: return
        val elapsedMillis = start?.elapsedNow()?.inWholeMilliseconds ?: return
        if (elapsedMillis <= 0) return
        start = clock.markNow()
        try {
            // Keep the persisted increment and the consumed interval together on cancellation.
            withContext(NonCancellable) {
                recorder.recordReadingDuration(target.sourceKey, target.workId, target.chapterId, elapsedMillis)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Timing failures do not prevent saving a position or closing the reader.
        }
    }
}
