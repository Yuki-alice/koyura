package dev.reader.app

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellWidthTest {
    @Test
    fun belowSevenHundredTwentyUsesBottomBar() {
        assertFalse(usesNavigationRail(719.dp))
        assertFalse(usesNavigationRail(360.dp))
        assertFalse(usesNavigationRail(0.dp))
    }

    @Test
    fun atSevenHundredTwentyUsesRail() {
        assertTrue(usesNavigationRail(720.dp))
        assertTrue(usesNavigationRail(721.dp))
        assertTrue(usesNavigationRail(1280.dp))
    }

    @Test
    fun thresholdMatchesKomikkuTabletBaseline() {
        assertFalse(usesNavigationRail(RailWidthThreshold - 1.dp))
        assertTrue(usesNavigationRail(RailWidthThreshold))
    }
}
