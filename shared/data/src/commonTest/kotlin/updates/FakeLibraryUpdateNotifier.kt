package app.koyura.data.updates

import app.koyura.domain.updates.LibraryUpdateNotifier
import app.koyura.domain.updates.ScheduledUpdateCheck

internal class FakeLibraryUpdateNotifier : LibraryUpdateNotifier {
    private val delivered = mutableListOf<ScheduledUpdateCheck>()
    val checks: List<ScheduledUpdateCheck> get() = delivered.toList()

    override suspend fun notify(check: ScheduledUpdateCheck) {
        delivered += check
    }
}
