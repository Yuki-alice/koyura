package app.koyura.app.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.reader.comic.readerAutomaticBackground
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阅读器 Automatic 背景映射：暗 #FF202125 / 亮白（Code: ReaderActivity.kt:1432）。
 */
class ReaderBackgroundTest {
    @Test
    fun automaticBackgroundMapsBothThemes() = runComposeUiTest {
        var light: Color? = null
        var dark: Color? = null
        setContent {
            ReaderTheme {
                light = readerAutomaticBackground(dark = false)
                dark = readerAutomaticBackground(dark = true)
            }
        }
        waitForIdle()

        assertEquals(Color.White, light)
        assertEquals(Color(0xFF202125), dark)
    }
}
