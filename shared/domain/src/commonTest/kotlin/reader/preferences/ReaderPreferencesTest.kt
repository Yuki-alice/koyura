package app.koyura.domain.reader.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPreferencesTest {
    @Test
    fun defaultsAreLeftToRightSingleFlow() {
        val defaults = ReaderSettings()
        assertEquals(ReadingMode.LEFT_TO_RIGHT, defaults.mode)
        assertEquals(ReaderLayout.AUTOMATIC, defaults.layout)
        assertEquals(ReaderFit.FIT, defaults.fit)
    }

    @Test
    fun nullOverrideInheritsGlobal() {
        val global = ReaderSettings(mode = ReadingMode.RIGHT_TO_LEFT, cropBorders = true)
        assertEquals(global, global.withOverride(null))
        assertEquals(global, global.withOverride(WorkReaderOverride()))
    }

    @Test
    fun overrideMergesFieldByField() {
        val global = ReaderSettings()
        val effective = global.withOverride(
            WorkReaderOverride(mode = ReadingMode.WEBTOON, zoomOnDoubleTap = false),
        )
        assertEquals(ReadingMode.WEBTOON, effective.mode)
        assertFalse(effective.zoomOnDoubleTap)
        // Untouched fields inherit the global value.
        assertEquals(global.layout, effective.layout)
        assertEquals(global.fit, effective.fit)
        assertEquals(global.firstPageSingle, effective.firstPageSingle)
    }

    @Test
    fun patchKeepsUnsetFields() {
        val base = WorkReaderOverride(mode = ReadingMode.VERTICAL)
        val patched = base.patchedWith(WorkReaderOverride(cropBorders = true))
        assertEquals(ReadingMode.VERTICAL, patched.mode)
        assertEquals(true, patched.cropBorders)
        assertEquals(null, patched.fit)
        assertFalse(patched.isEmpty())
        assertTrue(WorkReaderOverride().isEmpty())
    }

    @Test
    fun clearingOverrideRestoresGlobal() {
        val global = ReaderSettings(fit = ReaderFit.FIT_WIDTH)
        val withOverride = global.withOverride(WorkReaderOverride(fit = ReaderFit.ORIGINAL))
        assertEquals(ReaderFit.ORIGINAL, withOverride.fit)
        assertEquals(global, global.withOverride(null))
    }
}
