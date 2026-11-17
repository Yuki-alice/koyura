package app.koyura.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.library.FakeLibraryRepository
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.library.libraryFixtureSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewScrollRetentionTest {
    /**
     * Vertical screen scroll only: the shelf category row scrolls horizontally and
     * must not match a screen-scroll lookup.
     */
    private fun hasVerticalScroll() = SemanticsMatcher("vertical scroll") {
        it.config.contains(SemanticsProperties.VerticalScrollAxisRange)
    }
    @Test
    fun preservesActualScreenScrollAcrossBreakpoint() = runComposeUiTest {
        val width = mutableStateOf(719.dp)
        // 书架页签渲染真实书架屏：注入有数据的仓储，保证网格可滚动。
        val snapshot = libraryFixtureSnapshot()
        val library = FakeLibraryRepository(
            entries = snapshot.entries,
            categories = snapshot.categories,
            links = snapshot.links,
        )
        setContent {
            ReaderTheme {
                CompositionLocalProvider(LocalLibraryRepository provides library) {
                    Box(Modifier.size(width.value, 138.dp)) { AppShell() }
                }
            }
        }
        fun scrollOffset(): Float = onNode(hasVerticalScroll()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        onNode(hasVerticalScroll()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 15f) }
        waitForIdle()
        val before = scrollOffset()
        assertTrue(before > 0f, "Screen must scroll before resizing (offset = $before)")
        runOnIdle { width.value = 720.dp }
        waitForIdle()
        val wideRange = onNode(hasVerticalScroll()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(wideRange.maxValue() >= before, "Wide screen can hold previous offset")
        assertEquals(before, scrollOffset(), "Window resizing should retain scroll on the same route")
    }
}
