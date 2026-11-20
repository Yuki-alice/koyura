package app.koyura.data.updates

import app.koyura.domain.library.LibraryManga
import app.koyura.domain.library.WorkKind
import app.koyura.domain.updates.DetectedChapter
import app.koyura.domain.updates.LibraryUpdateInterval
import app.koyura.domain.updates.WorkUpdate
import app.koyura.domain.updates.LibraryUpdateSchedulePreferences
import app.koyura.domain.updates.UpdateScan
import app.koyura.domain.updates.ScheduledUpdateFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
        assertEquals(setOf(ScheduledUpdateFailure.SCAN), again.state.value.lastCheck?.failureKinds)
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

    @Test
    fun toleratesLegacyScheduleWithoutAnchorAndDefersAnOverdueRunUntilNetworkAllowsIt() = runTest {
        var raw = """{"preferences":{"interval":"HOURS_6"}}"""
        var runAllowed = false
        var runs = 0
        val time = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        val updates = LibraryUpdateScheduler(
            { raw }, { raw = it }, { runs++; UpdateScan(emptyList(), emptyList()) },
            clock = time, networkAllowsRun = { !it.wifiOnly || runAllowed },
        )
        updates.start(backgroundScope)
        runCurrent()
        assertEquals(6.hours.inWholeMilliseconds, updates.state.value.nextUpdateMillis)
        advanceTimeBy(7.hours)
        runCurrent()
        assertEquals(0, runs)
        runAllowed = true
        updateForeground(updates)
        runCurrent()
        assertEquals(1, runs)
        assertEquals(13.hours.inWholeMilliseconds, updates.state.value.nextUpdateMillis)
        updates.close()
    }

    @Test
    fun coldBackgroundRunDeduplicatesConcurrentWakeupsAndNotifiesAfterCommit() = runTest {
        var raw: String? = null
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val notifier = FakeLibraryUpdateNotifier()
        var runs = 0
        val time = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        fun open() = LibraryUpdateScheduler(
            readPreference = { raw },
            writePreference = { raw = it },
            checkNow = {
                runs++
                entered.complete(Unit)
                finish.await()
                UpdateScan(
                    listOf(WorkUpdate(
                        LibraryManga("source", WorkKind.COMIC, "work", "Title"), testScheduler.currentTime,
                        listOf(DetectedChapter("chapter", "Chapter", testScheduler.currentTime, false)),
                    )),
                    emptyList(),
                )
            },
            clock = time,
            notifier = notifier,
        )
        val first = open()
        first.setPreferences(LibraryUpdateSchedulePreferences(LibraryUpdateInterval.HOURS_6))
        first.close()
        advanceTimeBy(7.hours)

        val reopened = open()
        val background = async { reopened.runDue() }
        entered.await()
        val foreground = async { reopened.runDue() }
        assertTrue(notifier.checks.isEmpty())
        finish.complete(Unit)

        assertTrue(background.await().ran)
        assertFalse(foreground.await().ran)
        assertEquals(1, runs)
        assertEquals(1, notifier.checks.single().newChapters)
        assertEquals(13.hours.inWholeMilliseconds, reopened.state.value.nextUpdateMillis)
        reopened.close()

        val restored = open()
        assertFalse(restored.runDue().ran)
        assertEquals(1, notifier.checks.size)
        restored.close()
    }

    private fun updateForeground(updates: LibraryUpdateScheduler) {
        repeat(3) { updates.onForeground() }
    }
}
