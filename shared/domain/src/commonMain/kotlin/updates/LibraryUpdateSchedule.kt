package app.koyura.domain.updates

import kotlinx.serialization.Serializable

@Serializable
enum class LibraryUpdateInterval(val hours: Int) {
    DISABLED(0), HOURS_6(6), HOURS_12(12), HOURS_24(24),
}

@Serializable
data class LibraryUpdateSchedulePreferences(
    val interval: LibraryUpdateInterval = LibraryUpdateInterval.DISABLED,
    val wifiOnly: Boolean = true,
)

@Serializable
data class ScheduledUpdateCheck(
    val checkedAtMillis: Long,
    val worksChecked: Int,
    val newChapters: Int,
    val failures: Int,
    val skipped: Int,
    val failedToRun: Boolean = false,
)

data class LibraryUpdateScheduleState(
    val preferences: LibraryUpdateSchedulePreferences = LibraryUpdateSchedulePreferences(),
    val nextUpdateMillis: Long? = null,
    val lastCheck: ScheduledUpdateCheck? = null,
    val checking: Boolean = false,
)
