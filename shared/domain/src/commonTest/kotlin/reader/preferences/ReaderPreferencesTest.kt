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
    fun patchKeepsSetsAndClears() {
        val base = WorkReaderOverride(mode = ReadingMode.VERTICAL, cropBorders = true)
        val patched = base.patchedWith(
            WorkReaderOverridePatch(
                mode = OverrideField.Clear,
                fit = OverrideField.Set(ReaderFit.FIT_WIDTH),
            ),
        )
        assertEquals(null, patched.mode)
        assertEquals(ReaderFit.FIT_WIDTH, patched.fit)
        assertEquals(true, patched.cropBorders)
        assertTrue(WorkReaderOverridePatch().isEmpty())
        assertTrue(WorkReaderOverride().isEmpty())
    }

    @Test
    fun explicitOffDiffersFromInherit() {
        val global = ReaderSettings(cropBorders = true)
        // Explicit off wins over a true global.
        val off = global.withOverride(
            WorkReaderOverride().patchedWith(
                WorkReaderOverridePatch(cropBorders = OverrideField.Set(false)),
            ),
        )
        assertEquals(false, off.cropBorders)
        // Clearing the field inherits the global value again.
        val cleared = WorkReaderOverride(cropBorders = false).patchedWith(
            WorkReaderOverridePatch(cropBorders = OverrideField.Clear),
        )
        assertEquals(null, cleared.cropBorders)
        assertEquals(true, global.withOverride(cleared).cropBorders)
    }

    @Test
    fun clearingOverrideRestoresGlobal() {
        val global = ReaderSettings(fit = ReaderFit.FIT_WIDTH)
        val withOverride = global.withOverride(WorkReaderOverride(fit = ReaderFit.ORIGINAL))
        assertEquals(ReaderFit.ORIGINAL, withOverride.fit)
        assertEquals(global, global.withOverride(null))
    }
}
