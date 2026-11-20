package app.koyura.app.visual

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 60 张确定性截图的 golden-diff 断言。禁止「零断言截图」：每个截图用例都必须走
 * [assertGolden]，它一次覆盖尺寸、主题背景采样、非背景比例与关键导航区块。
 *
 * 阈值与容差说明（平台字体 / 抗锯齿差异）：
 * - JVM/Skia 与 Android 的字形 hinting、字重、行高取整不同，同一段文字的抗锯齿边缘像素
 *   会有差异；因此颜色比较一律用逐通道 ±[CHANNEL_TOLERANCE] 的容差，绝不做逐像素全等比较。
 * - 非背景比例阈值取 [MIN_NON_BACKGROUND_RATIO] = 1%：文字与图标在 360×800 上通常占
 *   2%~8%，1% 仍能识别「全空白 / 全透明」，又不会因字体渲染差异误报。
 * - 导航区块阈值取 [MIN_REGION_CONTENT_RATIO] = 1%：导航条 / rail 的容器色本身就与背景
 *   不同，轻松超过；全屏阅读器没有 shell chrome，但阅读器自身的顶/底控件与页面图像仍会在
 *   对应条带留下内容，1% 可兼容这种形态又不会把空白条带误判为通过。
 * - 不透明度阈值 [MIN_OPAQUE_RATIO] = 95%：捕获失败时 Skia 会给出全透明图，alpha 检查
 *   是识别它的最直接手段，95% 对正常截图（100% 不透明）留有余量。
 */
object GoldenDiffAssertions {

    private const val CHANNEL_TOLERANCE = 12

    /** 比例统计用更紧的容差：导航容器色（surfaceContainer）应算作「内容」而非背景。 */
    private const val RATIO_BACKGROUND_TOLERANCE = 6
    private const val MIN_NON_BACKGROUND_RATIO = 0.01f
    private const val MIN_REGION_CONTENT_RATIO = 0.01f
    private const val MIN_OPAQUE_RATIO = 0.95f

    /** Material 3 NavigationBar 默认高度 80 dp；Density(1f) 下 1 dp = 1 px。 */
    private const val BOTTOM_BAR_HEIGHT_PX = 80

    /** NavigationRail 最小宽度 80 dp，取 100 px 覆盖 inner padding 与选中指示条。 */
    private const val RAIL_WIDTH_PX = 100

    /**
     * 主题调色板，颜色用 ARGB Int 表示（与 [ImageBitmap.readPixels] 填充的 int 同格式）。
     * 取值来自 `ReaderTheme` 的 TachiyomiLightColors / TachiyomiDarkColors。
     */
    data class ThemePalette(
        val backgroundArgb: Int,
        val allowedArgb: Set<Int>,
    ) {
        companion object {
            fun light(): ThemePalette = ThemePalette(
                backgroundArgb = 0xFFFEFBFF.toInt(),
                allowedArgb = setOf(
                    0xFFFEFBFF.toInt(), // background / surface
                    0xFFF5F1F8.toInt(), // surfaceContainerLowest
                    0xFFF7F2FA.toInt(), // surfaceContainerLow
                    0xFFF3EDF7.toInt(), // surfaceContainer / surfaceVariant
                    0xFFFCF7FF.toInt(), // surfaceContainerHigh / Highest
                    0xFFF2F0F4.toInt(), // inverseOnSurface
                    0xFF0058CA.toInt(), // primary / secondary / surfaceTint
                    0xFF001945.toInt(), // onPrimaryContainer
                    0xFFD9E2FF.toInt(), // primaryContainer / inversePrimary
                    0xFF1B1B1F.toInt(), // onBackground / onSurface
                    0xFF44464F.toInt(), // onSurfaceVariant
                    0xFF757780.toInt(), // outline
                    0xFFC5C6D0.toInt(), // outlineVariant
                    0xFFBA1A1A.toInt(), // error
                    0xFFFFDAD6.toInt(), // errorContainer
                    0xFF410002.toInt(), // onErrorContainer
                ),
            )

            fun dark(): ThemePalette = ThemePalette(
                backgroundArgb = 0xFF1B1B1F.toInt(),
                allowedArgb = setOf(
                    0xFF1B1B1F.toInt(), // background / surface
                    0xFF1A181D.toInt(), // surfaceContainerLowest
                    0xFF1E1C22.toInt(), // surfaceContainerLow
                    0xFF211F26.toInt(), // surfaceContainer / surfaceVariant
                    0xFF292730.toInt(), // surfaceContainerHigh
                    0xFF302E38.toInt(), // surfaceContainerHighest
                    0xFF303034.toInt(), // inverseSurface
                    0xFFB0C6FF.toInt(), // primary / inversePrimary / surfaceTint
                    0xFF00429B.toInt(), // primaryContainer / secondaryContainer
                    0xFFD9E2FF.toInt(), // onPrimaryContainer
                    0xFFE3E2E6.toInt(), // onBackground / onSurface / inverseSurface text
                    0xFFC5C6D0.toInt(), // onSurfaceVariant
                    0xFF8F9099.toInt(), // outline
                    0xFF44464F.toInt(), // outlineVariant
                    0xFFFFB4AB.toInt(), // error
                    0xFF93000A.toInt(), // errorContainer
                    0xFFFFDAD6.toInt(), // onErrorContainer
                ),
            )
        }
    }

    /** 窄屏断言底部导航条，宽屏断言左侧 rail。 */
    enum class ChromeExpectation {
        BottomBar,
        Rail,
    }

    private data class Region(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    /**
     * 对一个截图执行全部 golden-diff 断言。必须在 ImageBitmap 仍有效时调用
     * （即 scene.close() 之前）。
     *
     * @param extraAllowedArgb 允许的额外确定色，例如固定 fixture 的封面色 / 阅读器页色；
     *   这些色由测试数据决定，不属于主题令牌，但也不应被判为背景或非法色。
     */
    fun assertGolden(
        image: ImageBitmap,
        expectedWidth: Int,
        expectedHeight: Int,
        palette: ThemePalette,
        chrome: ChromeExpectation,
        extraAllowedArgb: Set<Int> = emptySet(),
    ) {
        assertImageSize(image, expectedWidth, expectedHeight)

        val pixels = IntArray(expectedWidth * expectedHeight)
        image.readPixels(pixels)

        assertOpaque(pixels, expectedWidth, expectedHeight)
        assertSampledPixelsUseThemePalette(
            pixels, expectedWidth, expectedHeight, palette, extraAllowedArgb, chrome,
        )
        assertNonBackgroundRatio(pixels, expectedWidth, expectedHeight, palette.backgroundArgb)
        assertChromeRegionHasContent(pixels, expectedWidth, expectedHeight, palette.backgroundArgb, chrome)
    }

    /** 图像尺寸必须精确等于画布；尺寸漂移会让后续坐标采样失去意义。 */
    fun assertImageSize(image: ImageBitmap, expectedWidth: Int, expectedHeight: Int) {
        assertEquals(expectedWidth, image.width, "截图宽度与画布不一致")
        assertEquals(expectedHeight, image.height, "截图高度与画布不一致")
    }

    private fun assertOpaque(pixels: IntArray, width: Int, height: Int) {
        val opaque = pixels.count { ((it shr 24) and 0xFF) >= 0xF0 }
        val ratio = opaque.toFloat() / (width * height)
        assertTrue(
            ratio >= MIN_OPAQUE_RATIO,
            "截图几乎透明：不透明像素比例 $ratio < $MIN_OPAQUE_RATIO（${width}x${height}）",
        )
    }

    /**
     * 采样边角与上下边缘的中点：这些位置由主题背景 / 导航容器 / 顶部栏覆盖，
     * 不会落在正文文字的抗锯齿边缘上。任一采样点既不是主题背景也不在允许色集合内，
     * 说明抓到了错误主题或空白图。
     */
    private fun assertSampledPixelsUseThemePalette(
        pixels: IntArray,
        width: Int,
        height: Int,
        palette: ThemePalette,
        extraAllowedArgb: Set<Int>,
        chrome: ChromeExpectation,
    ) {
        val allowed = palette.allowedArgb + palette.backgroundArgb + extraAllowedArgb
        // 宽屏左侧 100 px 是 rail：它的 tonal elevation 会把容器色与 primary 混合，
        // 颜色不一定落在原始令牌上；采样点改从 rail 右侧的内容区取，rail 本身由区块断言覆盖。
        val leftFraction = if (chrome == ChromeExpectation.Rail) 0.12f else 0.01f
        val fractions = listOf(
            leftFraction to 0.01f,
            0.99f to 0.01f,
            leftFraction to 0.99f,
            0.99f to 0.99f,
            0.50f to 0.01f,
            0.50f to 0.99f,
        )
        for ((fx, fy) in fractions) {
            val x = (width * fx).toInt().coerceIn(0, width - 1)
            val y = (height * fy).toInt().coerceIn(0, height - 1)
            val argb = pixels[y * width + x]
            assertTrue(
                allowed.any { close(argb, it) },
                "采样点 ($x,$y) = 0x${argb.toHexString()} 不属于主题允许色集合",
            )
        }
    }

    /** 非背景像素比例必须超过阈值，防止「整张只有背景色」的空白截图蒙混过关。 */
    private fun assertNonBackgroundRatio(
        pixels: IntArray,
        width: Int,
        height: Int,
        backgroundArgb: Int,
    ) {
        var content = 0
        for (argb in pixels) {
            if (!close(argb, backgroundArgb, RATIO_BACKGROUND_TOLERANCE)) content++
        }
        val ratio = content.toFloat() / pixels.size
        assertTrue(
            ratio > MIN_NON_BACKGROUND_RATIO,
            "非背景像素比例 $ratio <= $MIN_NON_BACKGROUND_RATIO，疑似空白截图",
        )
    }

    /** 窄屏底部导航区域 / 宽屏左侧 rail 区域必须存在非背景内容。 */
    private fun assertChromeRegionHasContent(
        pixels: IntArray,
        width: Int,
        height: Int,
        backgroundArgb: Int,
        chrome: ChromeExpectation,
    ) {
        val region = when (chrome) {
            ChromeExpectation.BottomBar -> Region(0, height - BOTTOM_BAR_HEIGHT_PX, width, height)
            ChromeExpectation.Rail -> Region(0, 0, RAIL_WIDTH_PX.coerceAtMost(width), height)
        }
        var content = 0
        var total = 0
        for (y in region.y0 until region.y1) {
            for (x in region.x0 until region.x1) {
                total++
                if (!close(pixels[y * width + x], backgroundArgb, RATIO_BACKGROUND_TOLERANCE)) content++
            }
        }
        val ratio = content.toFloat() / total
        assertTrue(
            ratio > MIN_REGION_CONTENT_RATIO,
            "$chrome 区域非背景比例 $ratio <= $MIN_REGION_CONTENT_RATIO（区域 ${region.x0},${region.y0}-${region.x1},${region.y1}）",
        )
    }

    /** 逐通道容差比较，吸收字体 hinting 与抗锯齿带来的像素差异。 */
    private fun close(a: Int, b: Int, tolerance: Int = CHANNEL_TOLERANCE): Boolean =
        kotlin.math.abs(((a shr 24) and 0xFF) - ((b shr 24) and 0xFF)) <= tolerance &&
            kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tolerance &&
            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tolerance &&
            kotlin.math.abs((a and 0xFF) - (b and 0xFF)) <= tolerance

    private fun Int.toHexString(): String = toString(16).padStart(8, '0')
}
