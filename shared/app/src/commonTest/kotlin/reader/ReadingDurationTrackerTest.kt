package app.koyura.app.reader

import app.koyura.domain.reader.ReadingDurationRecorder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class ReadingDurationTrackerTest {
    @Test
    fun countsOnlyResumedReaderTimeAndSplitsChaptersWithoutRepeatingCheckpoints() = runTest {
        val time = TestTimeSource()
        val recorded = mutableListOf<Pair<String?, Long>>()
        val tracker = ReadingDurationTracker(
            ReadingDurationRecorder { _, _, chapter, millis -> recorded += chapter to millis },
            time,
        )
        val first = ReadingDurationTracker.Chapter("source", "work", "first")
        val second = first.copy(chapterId = "second")
        tracker.setChapter(first)
        time += 200.milliseconds
        tracker.setVisibility(visible = true, resumed = true)
        time += 500.milliseconds
        tracker.positionSaved(first)
        tracker.positionSaved(first)
        time += 300.milliseconds
        tracker.setVisibility(visible = true, resumed = false)
        time += 3_000.milliseconds
        tracker.setVisibility(visible = true, resumed = true)
        time += 400.milliseconds
        tracker.setChapter(second)
        time += 700.milliseconds
        tracker.setVisibility(visible = false, resumed = true)
        time += 2_000.milliseconds
        tracker.setVisibility(visible = false, resumed = false)
        assertEquals<List<Pair<String?, Long>>>(listOf("first" to 500L, "first" to 300L, "first" to 400L, "second" to 700L), recorded)
    }
}
