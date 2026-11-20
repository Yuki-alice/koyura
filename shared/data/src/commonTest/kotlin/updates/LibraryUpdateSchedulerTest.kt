package app.koyura.data.updates

import app.koyura.domain.updates.LibraryUpdateInterval
import app.koyura.domain.updates.LibraryUpdateSchedulePreferences
import app.koyura.domain.updates.UpdateScan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryUpdateSchedulerTest {
    @Test
    fun firesAtIntervalRearmsAfterChangeAndDisarmsWhenDisabled() = runTest {
        var raw: String? = null
        var runs = 0
        val time = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        val updates = LibraryUpdateScheduler({ raw }, { raw = it }, { runs++; UpdateScan(emptyList(), emptyList()) }, clock = time)
        updates.start(backgroundScope)
        runCurrent()
        assertNull(updates.state.value.nextUpdateMillis)

        updates.setPreferences(LibraryUpdateSchedulePreferences(LibraryUpdateInterval.HOURS_12, wifiOnly = false))
        runCurrent()
        assertEquals(12.hours.inWholeMilliseconds, updates.state.value.nextUpdateMillis)
        advanceTimeBy(5.hours)
        updates.setPreferences(LibraryUpdateSchedulePreferences(LibraryUpdateInterval.HOURS_6))
        runCurrent()
        assertEquals(6.hours.inWholeMilliseconds, updates.state.value.nextUpdateMillis)
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(1, runs)
        assertEquals(6.hours.inWholeMilliseconds, updates.state.value.lastCheck?.checkedAtMillis)
        assertFalse(updates.state.value.lastCheck!!.failedToRun)
        assertEquals(12.hours.inWholeMilliseconds, updates.state.value.nextUpdateMillis)

        updates.setPreferences(LibraryUpdateSchedulePreferences())
        updateForeground(updates)
        advanceTimeBy(24.hours)
        runCurrent()
        assertEquals(1, runs)
        assertNull(updates.state.value.nextUpdateMillis)
        updates.close()
    }

    @Test
    fun restartAndForegroundCoalesceOneOverdueRunAndPersistFailureOutcome() = runTest {
        var raw: String? = null
        var runs = 0
        val time = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        fun open() = LibraryUpdateScheduler({ raw }, { raw = it }, { runs++; throw CancellationException("fixture source cancelled locally") }, clock = time)
        val first = open()
        first.setPreferences(LibraryUpdateSchedulePreferences(LibraryUpdateInterval.HOURS_6))
        first.start(backgroundScope)
        runCurrent()
        first.close()
        advanceTimeBy(20.hours)

        val reopened = open()
        reopened.start(backgroundScope)
        reopened.start(backgroundScope)
        updateForeground(reopened)
        runCurrent()
        assertEquals(1, runs)
        assertTrue(reopened.state.value.lastCheck!!.failedToRun)
        assertEquals(20.hours.inWholeMilliseconds, reopened.state.value.lastCheck?.checkedAtMillis)
        updateForeground(reopened)
        runCurrent()
        assertEquals(1, runs)
        reopened.close()

        val again = open()
        again.start(backgroundScope)
        runCurrent()
        assertEquals(1, runs)
        assertEquals(26.hours.inWholeMilliseconds, again.state.value.nextUpdateMillis)
        assertTrue(again.state.value.lastCheck!!.failedToRun)
        again.close()
    }

    @Test
    fun futureReleaseWindowRearmsWithoutRepeatingAfterItIsConsumed() = runTest {
        var raw: String? = null
        var release: Long? = null
        var runs = 0
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val time = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        val updates = LibraryUpdateScheduler(
            { raw }, { raw = it }, { runs++; UpdateScan(emptyList(), emptyList()) },
            nextReleaseWindow = { release?.takeIf { it > testScheduler.currentTime } },
            releaseChanges = changes,
            clock = time,
        )
        updates.setPreferences(LibraryUpdateSchedulePreferences(LibraryUpdateInterval.HOURS_24))
        updates.start(backgroundScope)
        runCurrent()
        assertEquals(24.hours.inWholeMilliseconds, updates.state.value.nextUpdateMillis)
        release = 2.hours.inWholeMilliseconds
        assertTrue(changes.tryEmit(Unit))
        runCurrent()
        assertEquals(release, updates.state.value.nextUpdateMillis)
        advanceTimeBy(2.hours)
        runCurrent()
        assertEquals(1, runs)
        updateForeground(updates)
        runCurrent()
        assertEquals(1, runs)
        assertEquals(26.hours.inWholeMilliseconds, updates.state.value.nextUpdateMillis)
        updates.close()
    }

    private fun updateForeground(updates: LibraryUpdateScheduler) {
        repeat(3) { updates.onForeground() }
    }
}
