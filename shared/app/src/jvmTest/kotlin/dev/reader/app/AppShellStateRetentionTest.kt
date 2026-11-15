package dev.reader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards screen-level state retention across the bottom bar / navigation rail switch.
 *
 * The shell builds different layout shapes on each side of 720 dp. These tests assert that the
 * selected tab's screen keeps its scroll position while crossing that boundary, and that each tab
 * keeps its own position instead of sharing one.
 */
class AppShellStateRetentionTest {
    private fun ComposeUiTest.scrollOffset(): Float =
        onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun ComposeUiTest.scrollRange(): Float =
        onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    @Test
    fun scrollSurvivesNarrowToWideAndBack() = runComposeUiTest {
        val width = mutableStateOf<Dp>(719.dp)
        setContent {
            ReaderTheme {
                Box(Modifier.size(width.value, 138.dp)) { AppShell() }
            }
        }

        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 15f) }
        waitForIdle()
        val narrowOffset = scrollOffset()
        assertTrue(narrowOffset > 0f, "narrow screen must scroll first (offset = $narrowOffset)")

        runOnIdle { width.value = 720.dp }
        waitForIdle()
        onNodeWithTag(NavigationRailTestTag).assertIsDisplayed()
        assertTrue(scrollRange() >= narrowOffset, "rail layout must keep enough scroll range")
        assertEquals(narrowOffset, scrollOffset(), "rail switch must keep scroll position")

        runOnIdle { width.value = 719.dp }
        waitForIdle()
        onNodeWithTag(NavigationBarTestTag).assertIsDisplayed()
        assertEquals(narrowOffset, scrollOffset(), "bar switch must keep scroll position")
    }

    @Test
    fun eachTabKeepsItsOwnScrollPosition() = runComposeUiTest {
        val navigation = createAppNavigationState()
        val width = mutableStateOf<Dp>(719.dp)
        setContent {
            ReaderTheme {
                Box(Modifier.size(width.value, 138.dp)) {
                    AppScaffold(navigation = navigation, useRail = usesNavigationRail(width.value))
                }
            }
        }

        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 15f) }
        waitForIdle()
        val libraryOffset = scrollOffset()
        assertTrue(libraryOffset > 0f, "library must scroll first (offset = $libraryOffset)")

        runOnIdle { navigation.select(NavTab.Updates) }
        waitForIdle()
        assertEquals(0f, scrollOffset(), "updates starts at its own scroll origin")

        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 5f) }
        waitForIdle()
        val updatesOffset = scrollOffset()
        assertTrue(updatesOffset > 0f, "updates must scroll first (offset = $updatesOffset)")
        assertTrue(updatesOffset != libraryOffset, "tabs must not share one scroll position")

        runOnIdle { width.value = 720.dp }
        waitForIdle()

        runOnIdle { navigation.select(NavTab.Library) }
        waitForIdle()
        assertEquals(libraryOffset, scrollOffset(), "library keeps its own offset after resizing")

        runOnIdle { navigation.select(NavTab.Updates) }
        waitForIdle()
        assertEquals(updatesOffset, scrollOffset(), "updates keeps its own offset after resizing")
    }
}
