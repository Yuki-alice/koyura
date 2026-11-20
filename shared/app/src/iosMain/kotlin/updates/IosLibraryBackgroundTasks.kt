package app.koyura.app.updates

import app.koyura.data.session.openExtensionSession
import app.koyura.domain.updates.LibraryUpdateScheduleState
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.Foundation.NSLog
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Called by the app delegate before launch completes. */
fun registerLibraryBackgroundTasks() {
    IosLibraryBackgroundTasks.register()
}

@OptIn(ExperimentalForeignApi::class)
internal object IosLibraryBackgroundTasks {
    private const val taskId = "app.koyura.library.refresh"
    private val retryDelay = 15.minutes.inWholeMilliseconds
    private val scheduler get() = BGTaskScheduler.sharedScheduler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
        NSLog("Library background check failed: %@", error::class.simpleName.orEmpty())
    })
    val sessions = BackgroundLibraryUpdates()

    fun register() {
        scheduler.registerForTaskWithIdentifier(taskId, usingQueue = null) { task ->
            if (task != null) handle(task)
        }
    }

    fun schedule(state: LibraryUpdateScheduleState) {
        submit(state.nextUpdateMillis)
    }

    private fun submit(deadline: Long?) {
        if (deadline == null) {
            scheduler.cancelTaskRequestWithIdentifier(taskId)
            return
        }
        val request = BGProcessingTaskRequest(taskId).apply {
            requiresNetworkConnectivity = true
            earliestBeginDate = NSDate.dateWithTimeIntervalSince1970(deadline / 1_000.0)
        }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!scheduler.submitTaskRequest(request, error.ptr)) {
                NSLog("Library background request failed: %@", error.value?.localizedDescription.orEmpty())
            }
        }
    }

    private fun handle(task: BGTask) {
        // Keep a successor queued if iOS expires this attempt or the process is interrupted.
        submit(now() + retryDelay)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            IosUpdateNetwork.awaitInitialPath()
            val result = sessions.runDue {
                val locale = NSLocale.preferredLanguages.firstOrNull()?.toString()?.replace('-', '_') ?: "en"
                openExtensionSession(
                    environment = VeneraEnvironment("1.16.0", locale, "ios"),
                    isOnline = IosUpdateNetwork::isOnline,
                    isUnmetered = IosUpdateNetwork::isUnmetered,
                    updateNotifier = IosLibraryUpdateNotifier,
                )
            }
            val next = result.state.nextUpdateMillis
            submit(if (result.waitingForNetwork) next?.coerceAtLeast(now() + retryDelay) else next)
        }
        task.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { failure ->
            task.expirationHandler = null
            task.setTaskCompletedWithSuccess(failure == null)
        }
        job.start()
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()
}
