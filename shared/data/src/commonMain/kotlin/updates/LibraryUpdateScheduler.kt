package app.koyura.data.updates

import app.koyura.domain.updates.LibraryUpdateNotifier
import app.koyura.domain.updates.LibraryUpdateInterval
import app.koyura.domain.updates.LibraryUpdateSchedulePreferences
import app.koyura.domain.updates.LibraryUpdateScheduleState
import app.koyura.domain.updates.ScheduledUpdateCheck
import app.koyura.domain.updates.ScheduledUpdateFailure
import app.koyura.domain.updates.UpdateScan
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class LibraryUpdateScheduler internal constructor(
    private val readPreference: suspend () -> String?,
    private val writePreference: suspend (String) -> Unit,
    private val checkNow: suspend () -> UpdateScan,
    private val nextReleaseWindow: suspend () -> Long? = { null },
    private val releaseChanges: Flow<Unit> = emptyFlow(),
    private val clock: Clock = Clock.System,
    private val networkAllowsRun: (LibraryUpdateSchedulePreferences) -> Boolean = { true },
    private val notifier: LibraryUpdateNotifier? = null,
) {
    private val lock = Mutex()
    private val runLock = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val status = MutableStateFlow(LibraryUpdateScheduleState())
    val state = status.asStateFlow()
    private var job: Job? = null
    private var closed = false
    private var cached: Record? = null

    /** Attach once to a process/session scope. An overdue check is handled immediately. */
    suspend fun start(scope: CoroutineScope) = lock.withLock {
        check(!closed) { "Library update scheduler is closed" }
        if (job?.isActive == true) return@withLock
        job = scope.launch {
            val watch = launch { releaseChanges.collect { onForeground() } }
            try {
                while (isActive) {
                    val deadline = lock.withLock { reconcile() }
                    val remaining = deadline?.minus(now())
                    when {
                        remaining == null -> wake.receive()
                        remaining > 0 -> withTimeoutOrNull(remaining) { wake.receive() }
                        else -> {
                            if (!runDue().ran) withTimeoutOrNull(15.minutes) { wake.receive() }
                        }
                    }
                }
            } finally {
                watch.cancel()
                status.update { it.copy(checking = false) }
            }
        }
    }

    fun onForeground() {
        wake.trySend(Unit)
    }

    suspend fun preferences(): LibraryUpdateSchedulePreferences = lock.withLock { record().preferences }

    suspend fun setPreferences(value: LibraryUpdateSchedulePreferences) = lock.withLock {
        check(!closed) { "Library update scheduler is closed" }
        val record = record()
        if (record.preferences != value) {
            val anchor = when {
                value.interval == LibraryUpdateInterval.DISABLED -> null
                record.preferences.interval == LibraryUpdateInterval.DISABLED -> now()
                else -> record.anchorMillis ?: now()
            }
            val release = record.releaseMillis.takeUnless { anchor == null }
            save(record.copy(preferences = value, anchorMillis = anchor, releaseMillis = release))
            publish(record())
            onForeground()
        }
    }

    internal suspend fun close() {
        val active = lock.withLock {
            closed = true
            job.also { job = null }
        }
        active?.cancelAndJoin()
        wake.close()
    }

    private suspend fun reconcile(): Long? {
        val current = record()
        val release = when {
            current.preferences.interval == LibraryUpdateInterval.DISABLED -> null
            current.releaseMillis?.let { it <= now() } == true -> current.releaseMillis
            else -> nextReleaseWindow()
        }
        val anchor = when (current.preferences.interval) {
            LibraryUpdateInterval.DISABLED -> null
            else -> current.anchorMillis ?: current.lastCheck?.checkedAtMillis ?: now()
        }
        val updated = current.copy(anchorMillis = anchor, releaseMillis = release)
        if (current != updated) save(updated)
        publish(updated)
        return deadline(updated)
    }

    /** Shares the execution lock with the process timer, including during a cold background launch. */
    suspend fun runDue(): ScheduledUpdateRun = runLock.withLock {
        val blocked = lock.withLock checkDeadline@{
            check(!closed) { "Library update scheduler is closed" }
            val deadline = reconcile()
            if (deadline == null || deadline > now()) return@checkDeadline ScheduledUpdateRun(state.value)
            if (!networkAllowsRun(record().preferences)) {
                return@checkDeadline ScheduledUpdateRun(state.value, waitingForNetwork = true)
            }
            status.update { it.copy(checking = true) }
            null
        }
        if (blocked != null) return@withLock blocked
        try {
            recordScheduledCheck()
            ScheduledUpdateRun(state.value, ran = true)
        } finally {
            status.update { it.copy(checking = false) }
        }
    }

    suspend fun refreshSchedule(): LibraryUpdateScheduleState = lock.withLock {
        check(!closed) { "Library update scheduler is closed" }
        reconcile()
        state.value
    }

    private suspend fun recordScheduledCheck() {
        val result = try {
            val scan = checkNow()
            ScheduledUpdateCheck(
                now(), scan.updates.size + scan.failures.size, scan.updates.sumOf { it.newChapterCount },
                scan.failures.size, scan.skipped.size,
                downloadTriggerFailures = scan.downloadTriggerFailures.size,
                failureKinds = buildSet {
                    if (scan.failures.isNotEmpty()) add(ScheduledUpdateFailure.SOURCE_WORKS)
                    if (scan.downloadTriggerFailures.isNotEmpty()) add(ScheduledUpdateFailure.DOWNLOAD_TRIGGER)
                },
            )
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            ScheduledUpdateCheck(
                now(), 0, 0, 0, 0, failedToRun = true, failureKinds = setOf(ScheduledUpdateFailure.SCAN),
            )
        }
        lock.withLock {
            val current = record()
            val anchor = result.checkedAtMillis.takeUnless { current.preferences.interval == LibraryUpdateInterval.DISABLED }
            val updated = current.copy(anchorMillis = anchor, releaseMillis = null, lastCheck = result)
            save(updated)
            publish(updated, checking = false)
        }
        if (result.newChapters > 0) {
            try {
                notifier?.notify(result)
            } catch (_: Exception) {
                // The scan and its next deadline have already been committed.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private fun deadline(record: Record): Long? {
        val anchor = record.anchorMillis ?: return null
        val regular = anchor + record.preferences.interval.hours.hours.inWholeMilliseconds
        return minOf(regular, record.releaseMillis ?: regular)
    }

    private suspend fun record(): Record = cached ?: (
        readPreference()?.let { runCatching { json.decodeFromString<Record>(it) }.getOrNull() } ?: Record()
    ).also { cached = it }

    private suspend fun save(value: Record) {
        writePreference(json.encodeToString(value))
        cached = value
    }

    private fun publish(value: Record, checking: Boolean = state.value.checking) {
        status.value = LibraryUpdateScheduleState(value.preferences, deadline(value), value.lastCheck, checking)
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    @Serializable
    private data class Record(
        val preferences: LibraryUpdateSchedulePreferences = LibraryUpdateSchedulePreferences(),
        val anchorMillis: Long? = null,
        val releaseMillis: Long? = null,
        val lastCheck: ScheduledUpdateCheck? = null,
    )

    internal companion object {
        const val PREFERENCE = "updates.schedule"
        val json = Json { ignoreUnknownKeys = true }
    }
}

data class ScheduledUpdateRun(
    val state: LibraryUpdateScheduleState,
    val ran: Boolean = false,
    val waitingForNetwork: Boolean = false,
)
