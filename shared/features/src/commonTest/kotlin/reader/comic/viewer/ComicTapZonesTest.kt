package app.koyura.features.reader.comic.viewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComicTapZonesTest {
    @Test
    fun fromIdFallsBackToDefault() {
        assertEquals(ComicTapZoneMode.DEFAULT, ComicTapZoneMode.fromId("DEFAULT"))
        assertEquals(ComicTapZoneMode.KINDLE, ComicTapZoneMode.fromId("KINDLE"))
        assertEquals(ComicTapZoneMode.DEFAULT, ComicTapZoneMode.fromId("unknown"))
    }

    @Test
    fun defaultTilesThirdsAlongReadingAxis() {
        val rects = tapZoneRects(ComicTapZoneMode.DEFAULT)
        assertEquals(
            listOf(ComicTapAction.PREVIOUS, ComicTapAction.MENU, ComicTapAction.NEXT),
            rects.map { it.action },
        )
        assertEquals(0f, rects.first().alongFrom)
        assertEquals(1f, rects.last().alongTo)
    }

    @Test
    fun disabledHasNoRectsAndResolvesNone() {
        assertTrue(tapZoneRects(ComicTapZoneMode.DISABLED).isEmpty())
        assertEquals(
            ComicTapAction.NONE,
            resolveComicTap(ComicTapZoneMode.DISABLED, 0.5f, 0.5f, invert = false),
        )
    }

    @Test
    fun resolveFollowsDocumentedGeometry() {
        // DEFAULT thirds.
        assertEquals(
            ComicTapAction.PREVIOUS,
            resolveComicTap(ComicTapZoneMode.DEFAULT, 0.1f, 0.5f, invert = false),
        )
        assertEquals(
            ComicTapAction.MENU,
            resolveComicTap(ComicTapZoneMode.DEFAULT, 0.5f, 0.5f, invert = false),
        )
        assertEquals(
            ComicTapAction.NEXT,
            resolveComicTap(ComicTapZoneMode.DEFAULT, 0.9f, 0.5f, invert = false),
        )
        // LEFT_RIGHT halves, no menu.
        assertEquals(
            ComicTapAction.PREVIOUS,
            resolveComicTap(ComicTapZoneMode.LEFT_RIGHT, 0.49f, 0.5f, invert = false),
        )
        assertEquals(
            ComicTapAction.NEXT,
            resolveComicTap(ComicTapZoneMode.LEFT_RIGHT, 0.5f, 0.5f, invert = false),
        )
        // KINDLE: narrow prev strip, menu on the leading across band, next elsewhere.
        assertEquals(
            ComicTapAction.PREVIOUS,
            resolveComicTap(ComicTapZoneMode.KINDLE, 0.1f, 0.9f, invert = false),
        )
        assertEquals(
            ComicTapAction.MENU,
            resolveComicTap(ComicTapZoneMode.KINDLE, 0.5f, 0.05f, invert = false),
        )
        assertEquals(
            ComicTapAction.NEXT,
            resolveComicTap(ComicTapZoneMode.KINDLE, 0.5f, 0.5f, invert = false),
        )
        // EDGE: only the outer sixths turn.
        assertEquals(
            ComicTapAction.MENU,
            resolveComicTap(ComicTapZoneMode.EDGE, 0.5f, 0.5f, invert = false),
        )
        assertEquals(
            ComicTapAction.PREVIOUS,
            resolveComicTap(ComicTapZoneMode.EDGE, 0.05f, 0.5f, invert = false),
        )
        assertEquals(
            ComicTapAction.NEXT,
            resolveComicTap(ComicTapZoneMode.EDGE, 0.95f, 0.5f, invert = false),
        )
        // L_SHAPE: prev strip plus a next arm on the far across band.
        assertEquals(
            ComicTapAction.PREVIOUS,
            resolveComicTap(ComicTapZoneMode.L_SHAPE, 0.1f, 0.9f, invert = false),
        )
        assertEquals(
            ComicTapAction.NEXT,
            resolveComicTap(ComicTapZoneMode.L_SHAPE, 0.6f, 0.9f, invert = false),
        )
        assertEquals(
            ComicTapAction.MENU,
            resolveComicTap(ComicTapZoneMode.L_SHAPE, 0.6f, 0.3f, invert = false),
        )
    }

    @Test
    fun invertSwapsPrevNextOnly() {
        assertEquals(
            ComicTapAction.NEXT,
            resolveComicTap(ComicTapZoneMode.DEFAULT, 0.1f, 0.5f, invert = true),
        )
        assertEquals(
            ComicTapAction.PREVIOUS,
            resolveComicTap(ComicTapZoneMode.DEFAULT, 0.9f, 0.5f, invert = true),
        )
        assertEquals(
            ComicTapAction.MENU,
            resolveComicTap(ComicTapZoneMode.DEFAULT, 0.5f, 0.5f, invert = true),
        )
        assertEquals(
            ComicTapAction.NONE,
            resolveComicTap(ComicTapZoneMode.DISABLED, 0.1f, 0.5f, invert = true),
        )
    }

    @Test
    fun outOfRangeFractionsClampInside() {
        assertEquals(
            ComicTapAction.PREVIOUS,
            resolveComicTap(ComicTapZoneMode.DEFAULT, -0.5f, 99f, invert = false),
        )
        assertEquals(
            ComicTapAction.NEXT,
            resolveComicTap(ComicTapZoneMode.DEFAULT, 1.5f, -1f, invert = false),
        )
    }
}
