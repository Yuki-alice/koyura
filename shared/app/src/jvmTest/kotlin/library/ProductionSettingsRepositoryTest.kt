package app.koyura.app.library

import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.appearance.AppearancePreferences
import app.koyura.domain.appearance.ThemeMode
import app.koyura.domain.downloads.DownloadTriggerPreferences
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.updates.LibraryUpdateInterval
import app.koyura.domain.updates.LibraryUpdateSchedulePreferences
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class ProductionSettingsRepositoryTest {
    @Test
    fun writesToTheExistingStoresAndReadsThemAfterReopen() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "settings-${Uuid.random()}"
        val environment = VeneraEnvironment("1.16.0", "en", "test")
        val appearance = AppearancePreferences(ThemeMode.DARK, "default", amoled = true, useCoverColors = true)
        val reader = ReaderSettings(layout = ReaderLayout.SINGLE_PAGE)
        val downloads = DownloadTriggerPreferences(true, 4)
        val schedule = LibraryUpdateSchedulePreferences(LibraryUpdateInterval.HOURS_12, wifiOnly = false)
        try {
            val session = openExtensionSession(root, environment)
            try {
                val settings = session.settings()
                assertEquals(AppearancePreferences(), settings.appearance())
                session.library.setPref("appearance.preferences", "{")
                assertEquals(AppearancePreferences(), settings.appearance())
                settings.setAppearance(appearance)
                assertFailsWith<IllegalArgumentException> { settings.setAppearance(appearance.copy(themeId = "")) }
                settings.writeGlobal(reader)
                settings.setDownloadPreferences(downloads)
                settings.setUpdateSchedule(schedule)
            } finally {
                session.close()
            }
            val reopened = openExtensionSession(root, environment)
            try {
                val settings = reopened.settings()
                assertEquals(appearance, settings.appearance())
                assertEquals(reader, settings.readerSettings())
                assertEquals(reader, settings.load("source", "work").global)
                assertEquals(downloads, reopened.downloadTriggers.preferences())
                assertEquals(schedule, reopened.updateScheduler.preferences())
            } finally {
                reopened.close()
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    private fun ExtensionSession.settings() =
        ProductionSettingsRepository(library, readerPreferences, downloadTriggers, updateScheduler)
}
