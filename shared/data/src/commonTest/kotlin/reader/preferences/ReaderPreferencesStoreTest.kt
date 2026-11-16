package app.koyura.data.reader.preferences

import app.koyura.domain.reader.preferences.ReaderFit
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.WorkReaderOverride
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPreferencesStoreTest {
    private fun store(dir: String) = ReaderPreferencesStore(
        directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(dir),
        fileSystem = FileSystem.SYSTEM,
    )

    @Test
    fun freshStoreReturnsDefaults() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        assertEquals(ReaderSettings(), store.readGlobal())
        assertNull(store.readOverride("example_source", "demo"))
        assertEquals(ReaderSettings(), store.resolve("example_source", "demo"))
    }

    @Test
    fun newInstanceOnSameDirectorySeesWrites() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        store(dir).writeGlobal(ReaderSettings(mode = ReadingMode.RIGHT_TO_LEFT))
        val reopened = store(dir)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, reopened.readGlobal().mode)
        assertEquals(ReadingMode.RIGHT_TO_LEFT, reopened.resolve("example_source", "demo").mode)
    }

    @Test
    fun singleWorkOverrideMergesOverGlobal() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeOverride("example_source", "demo", WorkReaderOverride(mode = ReadingMode.WEBTOON))
        store.writeOverride("example_source", "demo", WorkReaderOverride(cropBorders = true))
        val effective = store.resolve("example_source", "demo")
        assertEquals(ReadingMode.WEBTOON, effective.mode)
        assertEquals(true, effective.cropBorders)
        assertEquals(ReaderLayout.AUTOMATIC, effective.layout)
        // Untouched work inherits the global value.
        assertEquals(ReadingMode.LEFT_TO_RIGHT, store.resolve("example_source", "other").mode)
    }

    @Test
    fun sameTitleWorksUnderTwoSourcesAreIsolated() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeOverride("source_a", "demo", WorkReaderOverride(fit = ReaderFit.FIT_WIDTH))
        store.writeOverride("source_b", "demo", WorkReaderOverride(fit = ReaderFit.ORIGINAL))
        assertEquals(ReaderFit.FIT_WIDTH, store.resolve("source_a", "demo").fit)
        assertEquals(ReaderFit.ORIGINAL, store.resolve("source_b", "demo").fit)
    }

    @Test
    fun clearingOverrideRestoresGlobal() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val store = store(dir)
        store.writeOverride("example_source", "demo", WorkReaderOverride(mode = ReadingMode.VERTICAL))
        assertEquals(ReadingMode.VERTICAL, store.resolve("example_source", "demo").mode)
        store.clearOverride("example_source", "demo")
        assertNull(store.readOverride("example_source", "demo"))
        assertEquals(ReaderSettings(), store.resolve("example_source", "demo"))
    }

    @Test
    fun legacyConfigWithMissingFieldsFallsBackToDefaults() = runTest {
        val dir = "chrome-prefs-${randomSuffix()}"
        val fs = FileSystem.SYSTEM
        val base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(dir)
        fs.createDirectories(base)
        // Old config predating fit/cropBorders/zoomOnDoubleTap, with an unknown key.
        fs.write(base / "reader_global.json") {
            writeUtf8("""{"mode":"WEBTOON","layout":"SINGLE_PAGE","unknownKey":1}""")
        }
        val store = ReaderPreferencesStore(directory = base, fileSystem = fs)
        val global = store.readGlobal()
        assertEquals(ReadingMode.WEBTOON, global.mode)
        assertEquals(ReaderLayout.SINGLE_PAGE, global.layout)
        assertEquals(ReaderFit.FIT, global.fit)
        assertEquals(false, global.cropBorders)
        assertEquals(true, global.zoomOnDoubleTap)
    }

    private fun randomSuffix(): String = (0..Int.MAX_VALUE).random().toString(36)
}
