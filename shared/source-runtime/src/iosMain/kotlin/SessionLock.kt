package app.koyura.source.runtime

import platform.Foundation.NSLock

internal actual class SessionLock {
    private val lock = NSLock()

    actual fun <T> locked(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
