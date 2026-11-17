package app.koyura.features.reader.comic.viewer

/**
 * Solid-border detection and content-rect fitting for `cropBorders`.
 *
 * Original behaviour (Komikku `ReaderPageImageView` + `SubsamplingScaleImageView`):
 * uniform outer strips are removed first, and the remaining content is rendered
 * with the user's scale type. A plain `ContentScale.Crop` is NOT equivalent: it
 * would cut into borderless pages. These helpers reproduce the crop step on the
 * decoded pixels; the viewer then fits the *content* rect instead of the full
 * bitmap, so corner content on borderless pages is never touched.
 */

/** Pixel strips to remove from each side. All values are bitmap pixels. */
data class ComicCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val NONE = ComicCrop(0, 0, 0, 0)
    }

    val isEmpty: Boolean get() = left == 0 && top == 0 && right == 0 && bottom == 0
}

/**
 * Finds solid-color border strips around the image.
 *
 * Each side uses its outermost line's most common color as the reference and
 * walks inward while every pixel of the line stays within [tolerance] per
 * channel. Walking stops at the first line containing page content, so corner
 * marks on borderless pages yield zero margins. Each side is capped at
 * [maxFraction] of its dimension so a nearly-solid page can never be eaten.
 *
 * @param pixels row-major ARGB pixels, [width] x [height].
 */
fun detectBorderCrop(
    pixels: IntArray,
    width: Int,
    height: Int,
    tolerance: Int = 12,
    maxFraction: Float = 0.25f,
): ComicCrop {
    require(pixels.size == width * height) { "pixels ${pixels.size} != ${width}x$height" }
    if (width <= 0 || height <= 0) return ComicCrop.NONE

    fun close(a: Int, b: Int): Boolean {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        val da = (a ushr 24) - (b ushr 24)
        return dr in -tolerance..tolerance &&
            dg in -tolerance..tolerance &&
            db in -tolerance..tolerance &&
            da in -tolerance..tolerance
    }

    fun mostCommon(get: (Int) -> Int, size: Int): Int {
        val counts = LinkedHashMap<Int, Int>()
        var best = get(0)
        var bestCount = 0
        for (i in 0 until size) {
            val color = get(i)
            val count = (counts[color] ?: 0) + 1
            counts[color] = count
            if (count > bestCount) {
                bestCount = count
                best = color
            }
        }
        return best
    }

    val maxHorizontal = (width * maxFraction).toInt().coerceAtLeast(0)
    val maxVertical = (height * maxFraction).toInt().coerceAtLeast(0)

    fun rowUniform(y: Int, ref: Int): Boolean {
        if (y !in 0 until height) return false
        val base = y * width
        for (x in 0 until width) {
            if (!close(pixels[base + x], ref)) return false
        }
        return true
    }

    fun columnUniform(x: Int, ref: Int): Boolean {
        if (x !in 0 until width) return false
        for (y in 0 until height) {
            if (!close(pixels[y * width + x], ref)) return false
        }
        return true
    }

    val topRef = mostCommon({ x -> pixels[x] }, width)
    var top = 0
    val topLimit = minOf(maxVertical, height - 1)
    while (top < topLimit && rowUniform(top, topRef)) top++

    val bottomRef = mostCommon({ x -> pixels[(height - 1) * width + x] }, width)
    var bottom = 0
    val bottomLimit = minOf(maxVertical, height - 1 - top)
    while (bottom < bottomLimit && rowUniform(height - 1 - bottom, bottomRef)) bottom++

    val leftRef = mostCommon({ y -> pixels[y * width] }, height)
    var left = 0
    val leftLimit = minOf(maxHorizontal, width - 1)
    while (left < leftLimit && columnUniform(left, leftRef)) left++

    val rightRef = mostCommon({ y -> pixels[y * width + width - 1] }, height)
    var right = 0
    val rightLimit = minOf(maxHorizontal, width - 1 - left)
    while (right < rightLimit && columnUniform(width - 1 - right, rightRef)) right++

    return ComicCrop(left, top, right, bottom)
}

/**
 * Base transform that fits the *content* rect (bitmap minus [crop]) into a
 * viewport, following [fit]. Returns Triple(scale, translateX, translateY) in
 * pixels, expressed for a top-left transform origin: screen = scale * bitmap + t.
 */
fun contentFitTransform(
    viewportWidth: Float,
    viewportHeight: Float,
    bitmapWidth: Float,
    bitmapHeight: Float,
    crop: ComicCrop,
    fit: ComicScaleFit,
): Triple<Float, Float, Float> {
    if (viewportWidth <= 0f || viewportHeight <= 0f || bitmapWidth <= 0f || bitmapHeight <= 0f) {
        return Triple(1f, 0f, 0f)
    }
    val l = crop.left.toFloat()
    val t = crop.top.toFloat()
    val contentW = (bitmapWidth - crop.left - crop.right).coerceAtLeast(1f)
    val contentH = (bitmapHeight - crop.top - crop.bottom).coerceAtLeast(1f)
    return when (fit) {
        ComicScaleFit.FIT -> {
            val s = minOf(viewportWidth / contentW, viewportHeight / contentH)
            Triple(s, (viewportWidth - s * contentW) / 2f - s * l, (viewportHeight - s * contentH) / 2f - s * t)
        }
        ComicScaleFit.FIT_WIDTH -> {
            val s = viewportWidth / contentW
            Triple(s, -s * l, (viewportHeight - s * contentH) / 2f - s * t)
        }
        ComicScaleFit.FIT_HEIGHT -> {
            val s = viewportHeight / contentH
            Triple(s, (viewportWidth - s * contentW) / 2f - s * l, -s * t)
        }
        ComicScaleFit.ORIGINAL -> {
            Triple(1f, (viewportWidth - contentW) / 2f - l, (viewportHeight - contentH) / 2f - t)
        }
    }
}
