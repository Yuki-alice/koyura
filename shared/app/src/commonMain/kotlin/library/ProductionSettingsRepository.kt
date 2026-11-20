package app.koyura.app.library

import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.data.downloads.DownloadTriggers
import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.data.updates.LibraryUpdateScheduler
import app.koyura.domain.appearance.AppearancePreferences
import app.koyura.domain.downloads.DownloadTriggerPreferences
import app.koyura.domain.library.LibraryRepository
import app.koyura.domain.reader.preferences.ReaderPreferences
import app.koyura.domain.updates.LibraryUpdateSchedulePreferences
import app.koyura.domain.updates.LibraryUpdateInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal val LocalProductionSettings = staticCompositionLocalOf<ProductionSettingsRepository?> { null }

/** Binds settings pages to the same preferences consumed by the reader and background services. */
class ProductionSettingsRepository(
    private val library: LibraryRepository,
    private val reader: ReaderPreferencesStore,
    private val downloads: DownloadTriggers,
    private val schedule: LibraryUpdateScheduler,
    private val requestUpdateNotifications: () -> Unit = {},
) : ReaderPreferences by reader {
    private val appearanceLock = Mutex()
    private val currentAppearance = MutableStateFlow(AppearancePreferences())
    val appearanceState = currentAppearance.asStateFlow()

    suspend fun appearance(): AppearancePreferences = appearanceLock.withLock {
        withContext(Dispatchers.IO) {
            val stored = library.getPref(APPEARANCE)
            val value = if (stored == null) AppearancePreferences() else try {
                json.decodeFromString<AppearancePreferences>(stored)
            } catch (_: SerializationException) {
                AppearancePreferences()
            }
            currentAppearance.value = value
            value
        }
    }

    suspend fun setAppearance(value: AppearancePreferences) = appearanceLock.withLock {
        require(value.themeId.isNotBlank())
        withContext(Dispatchers.IO) {
            library.setPref(APPEARANCE, json.encodeToString(value))
            currentAppearance.value = value
        }
    }

    suspend fun readerSettings() = reader.readGlobal()

    suspend fun downloadPreferences() = downloads.preferences()
    suspend fun setDownloadPreferences(value: DownloadTriggerPreferences) = downloads.setPreferences(value)

    val updateScheduleState get() = schedule.state
    suspend fun updateSchedule() = schedule.preferences()
    suspend fun setUpdateSchedule(value: LibraryUpdateSchedulePreferences) {
        val previouslyDisabled = schedule.preferences().interval == LibraryUpdateInterval.DISABLED
        schedule.setPreferences(value)
        if (previouslyDisabled && value.interval != LibraryUpdateInterval.DISABLED) requestUpdateNotifications()
    }
    fun onForeground() = schedule.onForeground()

    private companion object {
        const val APPEARANCE = "appearance.preferences"
        val json = Json { ignoreUnknownKeys = true }
    }
}
