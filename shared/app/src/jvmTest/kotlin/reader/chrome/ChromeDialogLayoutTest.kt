package app.koyura.app.reader.chrome

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import app.koyura.designsystem.ReaderTheme
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.features.reader.comic.chrome.ReaderChromeModeApplyTag
import app.koyura.features.reader.comic.chrome.ReaderChromeModeDialogTag
import app.koyura.features.reader.comic.chrome.ReaderChromeModeFrameTag
import app.koyura.features.reader.comic.chrome.ReaderChromeModePanelTag
import app.koyura.features.reader.comic.chrome.ReaderChromeModeScrimTag
import app.koyura.features.reader.comic.chrome.ReadingModeDialog
import app.koyura.features.reader.comic.chrome.isCompactDialog
import app.koyura.features.reader.comic.chrome.readerChromeModeOptionTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * Adaptive-sheet placement: bottom sheet on narrow windows, centered capped panel on wide
 * windows (Komikku breakpoint: smallest width < 720 dp is compact, max content width 460 dp).
 * Positions are asserted relative to the scrim/frame so they hold in any test window.
 */
class ChromeDialogLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun breakpointMatchesTabletRule() {
        assertTrue(isCompactDialog(419))
        assertTrue(isCompactDialog(719))
        assertTrue(!isCompactDialog(720))
        assertTrue(!isCompactDialog(1280))
    }

    private fun dialogContent(compact: Boolean, onApply: (ReadingMode?) -> Unit = {}) {
        rule.setContent {
            ReaderTheme(darkTheme = false) {
                ReadingModeDialog(
                    current = null,
                    allowDefault = true,
                    compact = compact,
                    onApply = onApply,
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun compactPanelHugsBottomAndCapsWidth() {
        dialogContent(compact = true)

        val scrim = rule.onNodeWithTag(ReaderChromeModeScrimTag).fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag(ReaderChromeModePanelTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Bottom-anchored: panel bottom meets the scrim bottom (above the system bars).
        assertEquals(scrim.bottom, panel.bottom, 8f)
        // Lower half only, capped width, dialog reachable.
        assertTrue(panel.top > scrim.height / 2, "compact panel must live in the lower half")
        assertTrue(panel.width <= 460.5f, "panel capped at 460 dp, was ${panel.width}")
        rule.onNodeWithTag(ReaderChromeModeDialogTag).assertIsDisplayed()
    }

    @Test
    fun widePanelIsCenteredAndCapped() {
        var applied: ReadingMode? = ReadingMode.VERTICAL
        dialogContent(compact = false, onApply = { applied = it })

        // No scrim in the centered variant: the dialog window handles outside dismissal.
        rule.onNodeWithTag(ReaderChromeModeScrimTag).assertDoesNotExist()
        val frame = rule.onNodeWithTag(ReaderChromeModeFrameTag).fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag(ReaderChromeModePanelTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Capped, strictly inside the frame, horizontally centered.
        assertTrue(panel.width <= 460.5f, "wide panel capped at 460 dp, was ${panel.width}")
        assertTrue(panel.width < frame.width, "centered panel must not stretch full width")
        val panelCenterX = (panel.left + panel.right) / 2
        val frameCenterX = (frame.left + frame.right) / 2
        assertEquals(frameCenterX, panelCenterX, 2f)
        assertTrue(panel.top > frame.top, "centered panel floats above the frame bottom")
        assertTrue(panel.bottom < frame.bottom, "centered panel floats below the frame top")

        // The centered variant stays fully operable.
        rule.onNodeWithTag(readerChromeModeOptionTag("RIGHT_TO_LEFT")).performClick()
        rule.onNodeWithTag(ReaderChromeModeApplyTag).performClick()
        assertEquals(ReadingMode.RIGHT_TO_LEFT, applied)
    }

    @Test
    fun selectionSurvivesWidthChange() {
        val compactState = androidx.compose.runtime.mutableStateOf(true)
        var applied: ReadingMode? = null
        rule.setContent {
            ReaderTheme(darkTheme = false) {
                ReadingModeDialog(
                    current = null,
                    allowDefault = true,
                    compact = compactState.value,
                    onApply = { applied = it },
                    onDismiss = {},
                )
            }
        }
        // Pick RTL in the bottom sheet, then widen the window: the temp pick must survive.
        rule.onNodeWithTag(readerChromeModeOptionTag("RIGHT_TO_LEFT")).performClick()
        rule.runOnIdle { compactState.value = false }
        rule.onNodeWithTag(ReaderChromeModeApplyTag).performClick()
        assertEquals(ReadingMode.RIGHT_TO_LEFT, applied)
    }

    @Test
    fun scrimTapDismissesCompactSheet() {
        var dismissed = false
        rule.setContent {
            ReaderTheme(darkTheme = false) {
                ReadingModeDialog(
                    current = null,
                    allowDefault = true,
                    compact = true,
                    onApply = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.onNodeWithTag(ReaderChromeModeScrimTag).performClick()
        rule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun keyboardSelectsModeCell() {
        rule.setContent {
            ReaderTheme(darkTheme = false) {
                ReadingModeDialog(
                    current = null,
                    allowDefault = true,
                    compact = true,
                    onApply = {},
                    onDismiss = {},
                )
            }
        }
        val cell = rule.onNodeWithTag(readerChromeModeOptionTag("WEBTOON"))
        cell.performSemanticsAction(SemanticsActions.RequestFocus)
        cell.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        // Selection is visible on the cell; apply would now emit WEBTOON.
        cell.assertIsSelected()
    }
}
