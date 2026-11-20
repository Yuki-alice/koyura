package app.koyura.app.updates

import app.koyura.data.session.ExtensionSession
import app.koyura.data.updates.ScheduledUpdateRun
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keeps foreground and background users of the production database and script directory in one session. */
internal class BackgroundLibraryUpdates {
    private val lock = Mutex()
    private var foreground: ExtensionSession? = null
    private var released = CompletableDeferred<Unit>()

    suspend fun openForeground(open: suspend () -> ExtensionSession): ExtensionSession {
        while (true) {
            val waitForRelease = lock.withLock {
                if (foreground == null) return open().also { foreground = it }
                released
            }
            waitForRelease.await()
        }
    }

    suspend fun closeForeground(session: ExtensionSession) = lock.withLock {
        check(foreground === session)
        try {
            session.close()
        } finally {
            foreground = null
            released.complete(Unit)
            released = CompletableDeferred()
        }
    }

    suspend fun runDue(openHeadless: suspend () -> ExtensionSession): ScheduledUpdateRun = withContext(Dispatchers.IO) {
        lock.withLock {
            val active = foreground
            if (active != null) return@withLock active.updateScheduler.runDue()
            var temporary: ExtensionSession? = null
            try {
                temporary = openHeadless()
                temporary.updateScheduler.runDue()
            } finally {
                withContext(NonCancellable) { temporary?.close() }
            }
        }
    }
}
