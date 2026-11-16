package app.koyura.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewScrollRetentionTest {
    @Test
    fun preservesActualScreenScrollAcrossBreakpoint() = runComposeUiTest {
        val width = mutableStateOf(719.dp)
        setContent {
            ReaderTheme {
                Box(Modifier.size(width.value, 138.dp)) { AppShell() }
            }
        }
        fun scrollOffset(): Float = onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 15f) }
        waitForIdle()
        val before = scrollOffset()
        assertTrue(before > 0f, "Screen must scroll before resizing (offset = $before)")
        runOnIdle { width.value = 720.dp }
        waitForIdle()
        val wideRange = onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(wideRange.maxValue() >= before, "Wide screen can hold previous offset")
        assertEquals(before, scrollOffset(), "Window resizing should retain scroll on the same route")
    }
}
