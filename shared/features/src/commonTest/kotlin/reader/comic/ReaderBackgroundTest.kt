package app.koyura.reader.comic

import androidx.compose.ui.graphics.Color
import app.koyura.domain.reader.preferences.ReaderBackground
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阅读器四档背景色值：Automatic 跟随主题，Black/Gray 恒暗，White 恒亮。
 */
class ReaderBackgroundTest {
    private val darkGray = Color(0xFF202125)

    @Test
    fun automaticFollowsTheme() {
        assertEquals(darkGray, readerBackgroundColor(ReaderBackground.AUTOMATIC, dark = true))
        assertEquals(Color.White, readerBackgroundColor(ReaderBackground.AUTOMATIC, dark = false))
    }

    @Test
    fun blackAndGrayIgnoreTheme() {
        assertEquals(Color.Black, readerBackgroundColor(ReaderBackground.BLACK, dark = false))
        assertEquals(Color.Black, readerBackgroundColor(ReaderBackground.BLACK, dark = true))
        assertEquals(darkGray, readerBackgroundColor(ReaderBackground.GRAY, dark = false))
        assertEquals(darkGray, readerBackgroundColor(ReaderBackground.GRAY, dark = true))
    }

    @Test
    fun whiteIgnoresTheme() {
        assertEquals(Color.White, readerBackgroundColor(ReaderBackground.WHITE, dark = true))
        assertEquals(Color.White, readerBackgroundColor(ReaderBackground.WHITE, dark = false))
    }
}
