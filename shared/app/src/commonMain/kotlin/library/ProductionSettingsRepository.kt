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

    /**
     * 更多页开关与引导标记：纯字符串偏好（"1"/缺失），随书架库持久化。
     *
     * 隐身模式由阅读器在每次落位时读取；下载仅看是书架筛选的快捷入口，
     * 由装配层同步进书架偏好；引导标记只写一次。
     */
    suspend fun isIncognito(): Boolean = withContext(Dispatchers.IO) {
        library.getPref(MORE_INCOGNITO) == "1"
    }

    suspend fun setIncognito(value: Boolean) = withContext(Dispatchers.IO) {
        if (value) library.setPref(MORE_INCOGNITO, "1") else library.deletePref(MORE_INCOGNITO)
    }

    suspend fun isDownloadedOnly(): Boolean = withContext(Dispatchers.IO) {
        library.getPref(MORE_DOWNLOADED_ONLY) == "1"
    }

    suspend fun setDownloadedOnly(value: Boolean) = withContext(Dispatchers.IO) {
        if (value) library.setPref(MORE_DOWNLOADED_ONLY, "1") else library.deletePref(MORE_DOWNLOADED_ONLY)
    }

    suspend fun isOnboardingCompleted(): Boolean = withContext(Dispatchers.IO) {
        library.getPref(ONBOARDING_COMPLETED) == "1"
    }

    suspend fun setOnboardingCompleted() = withContext(Dispatchers.IO) {
        library.setPref(ONBOARDING_COMPLETED, "1")
    }

    private companion object {
        const val APPEARANCE = "appearance.preferences"
        const val MORE_INCOGNITO = "more.incognito"
        const val MORE_DOWNLOADED_ONLY = "more.downloaded_only"
        const val ONBOARDING_COMPLETED = "onboarding.completed"
        val json = Json { ignoreUnknownKeys = true }
    }
}
