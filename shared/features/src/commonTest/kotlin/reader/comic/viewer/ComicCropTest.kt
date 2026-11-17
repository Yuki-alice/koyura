package app.koyura.features.reader.comic.viewer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pixel-level contract for [detectBorderCrop] and [contentFitTransform].
 *
 * The task requires proving that borderless pages keep their corner content
 * while real solid margins are removed, for bordered / plain / wide / long
 * pages alike. All fixtures are hand-built ARGB arrays, no platform code.
 */
class ComicCropTest {
    private fun solid(w: Int, h: Int, color: Int): IntArray = IntArray(w * h) { color }

    /** Bordered page: [border]-px solid frame around [inner], plus corner marks. */
    private fun bordered(
        w: Int,
        h: Int,
        border: Int,
        borderColor: Int,
        innerColor: Int,
    ): IntArray {
        val pixels = solid(w, h, borderColor)
        for (y in border until h - border) {
            for (x in border until w - border) {
                pixels[y * w + x] = innerColor
            }
        }
        // Corner marks inside the content area (must survive the crop).
        val mark = 0xFFFF0000.toInt()
        val m = 6
        for (dy in 0 until m) {
            for (dx in 0 until m) {
                pixels[(border + dy) * w + border + dx] = mark
                pixels[(border + dy) * w + w - 1 - border - dx] = mark
                pixels[(h - 1 - border - dy) * w + border + dx] = mark
                pixels[(h - 1 - border - dy) * w + w - 1 - border - dx] = mark
            }
        }
        return pixels
    }

    private fun assertInsideContent(
        pixels: IntArray,
        w: Int,
        crop: ComicCrop,
        probe: Int,
    ) {
        // The four inner corners (just inside the detected crop) still hold
        // content, i.e. nothing readable was cut.
        val l = crop.left
        val t = crop.top
        val r = w - 1 - crop.right
        val b = (pixels.size / w) - 1 - crop.bottom
        assertEquals(probe, pixels[t * w + l], "top-left content")
        assertEquals(probe, pixels[t * w + r], "top-right content")
        assertEquals(probe, pixels[b * w + l], "bottom-left content")
        assertEquals(probe, pixels[b * w + r], "bottom-right content")
    }

    @Test
    fun borderedPageDetectsExactFrame() {
        val white = 0xFFFFFFFF.toInt()
        val teal = 0xFF117A65.toInt()
        val pixels = bordered(w = 400, h = 520, border = 40, borderColor = white, innerColor = teal)

        val crop = detectBorderCrop(pixels, 400, 520)

        assertEquals(ComicCrop(40, 40, 40, 40), crop)
        assertInsideContent(pixels, 400, crop, 0xFFFF0000.toInt())
    }

    @Test
    fun borderlessPageWithCornerContentKeepsEverything() {
        val blue = 0xFF2E86C1.toInt()
        val yellow = 0xFFFFFF00.toInt()
        val pixels = solid(400, 600, blue)
        // Corner marks directly at the bitmap corners.
        pixels[0] = yellow
        pixels[399] = yellow
        pixels[599 * 400] = yellow
        pixels[599 * 400 + 399] = yellow

        val crop = detectBorderCrop(pixels, 400, 600)

        assertEquals(ComicCrop.NONE, crop)
    }

    @Test
    fun noisyBorderWithinToleranceStillCrops() {
        val base = 0xFFF5F5F5.toInt()
        val w = 200
        val h = 300
        val pixels = solid(w, h, 0xFF123456.toInt())
        // Deterministic ±5 noise on a 24px frame (spread 11 < tolerance 12).
        fun noise(x: Int, y: Int): Int = (((x * 31 + y * 17 + 3) % 11) + 11) % 11 - 5
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x < 24 || y < 24 || x >= w - 24 || y >= h - 24) {
                    val n = noise(x, y)
                    val r = (0xF5 + n).coerceIn(0, 255)
                    pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (r shl 8) or r
                }
            }
        }

        val crop = detectBorderCrop(pixels, w, h, tolerance = 12)

        assertEquals(ComicCrop(24, 24, 24, 24), crop)
    }

    @Test
    fun wideSpreadBorderCropsSymmetrically() {
        val black = 0xFF000000.toInt()
        val orange = 0xFFCA6F1E.toInt()
        val pixels = bordered(w = 800, h = 400, border = 30, borderColor = black, innerColor = orange)

        val crop = detectBorderCrop(pixels, 800, 400)

        assertEquals(ComicCrop(30, 30, 30, 30), crop)
    }

    @Test
    fun longStripBorderCropsSymmetrically() {
        val white = 0xFFFFFFFF.toInt()
        val green = 0xFF117A65.toInt()
        val pixels = bordered(w = 400, h = 1200, border = 20, borderColor = white, innerColor = green)

        val crop = detectBorderCrop(pixels, 400, 1200)

        assertEquals(ComicCrop(20, 20, 20, 20), crop)
    }

    @Test
    fun nearlySolidPageIsCapped() {
        // 90% uniform frame with a tiny center mark: the cap stops the crop
        // from eating the page.
        val white = 0xFFFFFFFF.toInt()
        val pixels = solid(400, 400, white)
        pixels[200 * 400 + 200] = 0xFF000000.toInt()

        val crop = detectBorderCrop(pixels, 400, 400, maxFraction = 0.25f)

        assertTrue(crop.top <= 100 && crop.bottom <= 100 && crop.left <= 100 && crop.right <= 100)
        // The center mark must remain inside the kept rect.
        assertTrue(200 >= crop.top && 200 < 400 - crop.bottom)
        assertTrue(200 >= crop.left && 200 < 400 - crop.right)
    }

    @Test
    fun fitTransformCentersCroppedContent() {
        // 400x520 bitmap, 40px frame -> 320x440 content, viewport 420x780.
        val (s, tx, ty) = contentFitTransform(
            viewportWidth = 420f,
            viewportHeight = 780f,
            bitmapWidth = 400f,
            bitmapHeight = 520f,
            crop = ComicCrop(40, 40, 40, 40),
            fit = ComicScaleFit.FIT,
        )
        // scale = min(420/320, 780/440) = 1.3125; content centered.
        assertTrue(abs(s - 1.3125f) < 1e-3f, "scale=$s")
        assertTrue(abs(tx - (-52.5f)) < 1e-2f, "tx=$tx")
        assertTrue(abs(ty - 48.75f) < 1e-2f, "ty=$ty")
        // Content rect maps centered into the viewport: full width, letterboxed
        // vertically (content 320x440 @1.3125 = 420x577.5 in 420x780).
        assertTrue(abs((tx + s * 40f) - 0f) < 1e-2f)
        assertTrue(abs((tx + s * (40f + 320f)) - 420f) < 1e-2f)
        assertTrue(abs((ty + s * 40f) - 101.25f) < 1e-2f)
        assertTrue(abs((ty + s * (40f + 440f)) - 678.75f) < 1e-2f)
    }

    @Test
    fun fitWidthTransformKeepsFullWidth() {
        val (s, tx, ty) = contentFitTransform(
            viewportWidth = 420f,
            viewportHeight = 780f,
            bitmapWidth = 400f,
            bitmapHeight = 1200f,
            crop = ComicCrop(0, 0, 0, 0),
            fit = ComicScaleFit.FIT_WIDTH,
        )
        assertTrue(abs(s - 1.05f) < 1e-3f, "scale=$s")
        assertTrue(abs(tx) < 1e-3f, "tx=$tx")
        // Height overflows (1260 > 780): centered, user pans the rest.
        assertTrue(ty < 0f, "ty=$ty")
    }
}
