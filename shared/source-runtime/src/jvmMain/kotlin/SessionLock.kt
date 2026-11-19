package app.koyura.source.runtime

internal actual class SessionLock {
    private val monitor = Any()

    actual fun <T> locked(action: () -> T): T = synchronized(monitor, action)
}
