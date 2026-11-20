package app.koyura.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.koyura.designsystem.AmoledDarkColors
import app.koyura.designsystem.TachiyomiDarkColors
import app.koyura.designsystem.TachiyomiLightColors
import com.materialkolor.PaletteStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 主题机制验收：19 套静态色板逐值对齐白皮书、模式/AMOLED 决议、封面取色与回退路径。
 * 期望值由 docs/design-whitepaper/assets/design-tokens.json 生成，见 SchemeExpectations.kt。
 */
class ThemePaletteTest {

    /**
     * CMP 的 ColorScheme 未实现值语义 equals，整对象断言只会比较引用。
     * 这里逐角色比较全部 36 个角色，确保「逐值一致」是真断言。
     */
    private fun assertSameColorScheme(expected: ColorScheme, actual: ColorScheme, tag: String) {
        assertEquals(expected.primary, actual.primary, "$tag primary")
        assertEquals(expected.onPrimary, actual.onPrimary, "$tag onPrimary")
        assertEquals(expected.primaryContainer, actual.primaryContainer, "$tag primaryContainer")
        assertEquals(expected.onPrimaryContainer, actual.onPrimaryContainer, "$tag onPrimaryContainer")
        assertEquals(expected.inversePrimary, actual.inversePrimary, "$tag inversePrimary")
        assertEquals(expected.secondary, actual.secondary, "$tag secondary")
        assertEquals(expected.onSecondary, actual.onSecondary, "$tag onSecondary")
        assertEquals(expected.secondaryContainer, actual.secondaryContainer, "$tag secondaryContainer")
        assertEquals(expected.onSecondaryContainer, actual.onSecondaryContainer, "$tag onSecondaryContainer")
        assertEquals(expected.tertiary, actual.tertiary, "$tag tertiary")
        assertEquals(expected.onTertiary, actual.onTertiary, "$tag onTertiary")
        assertEquals(expected.tertiaryContainer, actual.tertiaryContainer, "$tag tertiaryContainer")
        assertEquals(expected.onTertiaryContainer, actual.onTertiaryContainer, "$tag onTertiaryContainer")
        assertEquals(expected.background, actual.background, "$tag background")
        assertEquals(expected.onBackground, actual.onBackground, "$tag onBackground")
        assertEquals(expected.surface, actual.surface, "$tag surface")
        assertEquals(expected.onSurface, actual.onSurface, "$tag onSurface")
        assertEquals(expected.surfaceVariant, actual.surfaceVariant, "$tag surfaceVariant")
        assertEquals(expected.onSurfaceVariant, actual.onSurfaceVariant, "$tag onSurfaceVariant")
        assertEquals(expected.surfaceTint, actual.surfaceTint, "$tag surfaceTint")
        assertEquals(expected.inverseSurface, actual.inverseSurface, "$tag inverseSurface")
        assertEquals(expected.inverseOnSurface, actual.inverseOnSurface, "$tag inverseOnSurface")
        assertEquals(expected.error, actual.error, "$tag error")
        assertEquals(expected.onError, actual.onError, "$tag onError")
        assertEquals(expected.errorContainer, actual.errorContainer, "$tag errorContainer")
        assertEquals(expected.onErrorContainer, actual.onErrorContainer, "$tag onErrorContainer")
        assertEquals(expected.outline, actual.outline, "$tag outline")
        assertEquals(expected.outlineVariant, actual.outlineVariant, "$tag outlineVariant")
        assertEquals(expected.scrim, actual.scrim, "$tag scrim")
        assertEquals(expected.surfaceBright, actual.surfaceBright, "$tag surfaceBright")
        assertEquals(expected.surfaceDim, actual.surfaceDim, "$tag surfaceDim")
        assertEquals(expected.surfaceContainer, actual.surfaceContainer, "$tag surfaceContainer")
        assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh, "$tag surfaceContainerHigh")
        assertEquals(expected.surfaceContainerHighest, actual.surfaceContainerHighest, "$tag surfaceContainerHighest")
        assertEquals(expected.surfaceContainerLow, actual.surfaceContainerLow, "$tag surfaceContainerLow")
        assertEquals(expected.surfaceContainerLowest, actual.surfaceContainerLowest, "$tag surfaceContainerLowest")
    }

    @Test
    fun allNineteenStaticSchemesMatchWhitepaperKeyRoles() {
        assertEquals(19, ThemePreset.entries.size)
        assertEquals(19, expectedSchemes.size)
        val m3Light = lightColorScheme()
        val m3Dark = darkColorScheme()

        for (expected in expectedSchemes) {
            val palette = staticThemePalette(expected.preset)
            val tag = expected.preset.name
            assertEquals(expected.lightPrimary, palette.light.primary, "$tag light primary")
            assertEquals(expected.lightBackground, palette.light.background, "$tag light background")
            assertEquals(
                expected.lightSurfaceContainer ?: m3Light.surfaceContainer,
                palette.light.surfaceContainer,
                "$tag light surfaceContainer",
            )
            assertEquals(expected.darkPrimary, palette.dark.primary, "$tag dark primary")
            assertEquals(expected.darkBackground, palette.dark.background, "$tag dark background")
            assertEquals(
                expected.darkSurfaceContainer ?: m3Dark.surfaceContainer,
                palette.dark.surfaceContainer,
                "$tag dark surfaceContainer",
            )
        }
    }

    @Test
    fun generatedDefaultPaletteKeepsExistingThreeSchemesIntact() {
        val palette = staticThemePalette(ThemePreset.DEFAULT)
        assertSameColorScheme(TachiyomiLightColors, palette.light, "default light")
        assertSameColorScheme(TachiyomiDarkColors, palette.dark, "default dark")

        val amoled = amoledColorScheme(TachiyomiDarkColors)
        assertEquals(AmoledDarkColors.background, amoled.background)
        assertEquals(AmoledDarkColors.surface, amoled.surface)
        assertEquals(AmoledDarkColors.surfaceContainer, amoled.surfaceContainer)
        assertEquals(AmoledDarkColors.surfaceContainerHigh, amoled.surfaceContainerHigh)
        assertEquals(AmoledDarkColors.surfaceContainerHighest, amoled.surfaceContainerHighest)
    }

    @Test
    fun themeModeResolvesLightDarkAndSystem() {
        val light = staticThemePalette(ThemePreset.DEFAULT).light
        val dark = staticThemePalette(ThemePreset.DEFAULT).dark
        assertSameColorScheme(light, resolveColorScheme(ThemePreset.DEFAULT, ThemeMode.LIGHT, systemDark = true, amoled = false), "LIGHT")
        assertSameColorScheme(dark, resolveColorScheme(ThemePreset.DEFAULT, ThemeMode.DARK, systemDark = false, amoled = false), "DARK")
        assertSameColorScheme(light, resolveColorScheme(ThemePreset.DEFAULT, ThemeMode.SYSTEM, systemDark = false, amoled = false), "SYSTEM light")
        assertSameColorScheme(dark, resolveColorScheme(ThemePreset.DEFAULT, ThemeMode.SYSTEM, systemDark = true, amoled = false), "SYSTEM dark")
    }

    @Test
    fun amoledOverridesOnlyDarkScheme() {
        val light = staticThemePalette(ThemePreset.DEFAULT).light
        val dark = staticThemePalette(ThemePreset.DEFAULT).dark
        assertSameColorScheme(light, resolveColorScheme(ThemePreset.DEFAULT, ThemeMode.LIGHT, false, amoled = true), "light ignores amoled")
        val amoled = resolveColorScheme(ThemePreset.DEFAULT, ThemeMode.DARK, false, amoled = true)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color(0xFF0C0C0C), amoled.surfaceContainer)
        assertEquals(Color(0xFF131313), amoled.surfaceContainerHigh)
        assertEquals(Color(0xFF1B1B1B), amoled.surfaceContainerHighest)
        assertNotEquals(dark.surfaceContainer, amoled.surfaceContainer)
    }

    @Test
    fun differentCoverSeedsProduceDifferentSchemes() {
        val red = seedColorScheme(Color(0xFFB00020), isDark = false)
        val green = seedColorScheme(Color(0xFF006E1B), isDark = false)
        assertNotEquals(red.primary, green.primary)
        assertNotEquals(red.primaryContainer, green.primaryContainer)

        val dark = seedColorScheme(Color(0xFFB00020), isDark = true)
        assertNotEquals(red.primary, dark.primary)

        val fidelity = seedColorScheme(Color(0xFFB00020), isDark = false, style = PaletteStyle.Fidelity)
        assertNotEquals(red.primary, fidelity.primary)
    }

    @Test
    fun nullSeedFallsBackToStaticTheme() {
        val static = resolveColorScheme(ThemePreset.LAVENDER, ThemeMode.DARK, systemDark = false, amoled = false)
        val fallback = resolveThemeColorScheme(
            seedColor = null,
            preset = ThemePreset.LAVENDER,
            mode = ThemeMode.DARK,
        )
        assertSameColorScheme(static, fallback, "null seed fallback")

        val dynamic = resolveThemeColorScheme(
            seedColor = Color(0xFFB00020),
            preset = ThemePreset.LAVENDER,
            mode = ThemeMode.DARK,
        )
        assertNotEquals(static.primary, dynamic.primary)
    }

    @Test
    fun themeSelectionContractDefaultsAndFallback() {
        val selection = ThemeSelection()
        assertEquals(ThemePreset.DEFAULT, selection.preset)
        assertEquals(ThemeMode.SYSTEM, selection.mode)
        assertEquals(PaletteStyle.Vibrant, selection.coverBasedStyle)
        assertEquals(PaletteStyle.Fidelity, selection.customStyle)
        assertNull(selection.effectiveSeedColor(null))

        val coverSeed = Color(0xFFB00020)
        assertEquals(coverSeed, selection.effectiveSeedColor(coverSeed))
        assertNull(selection.copy(coverBased = false).effectiveSeedColor(coverSeed))

        val custom = Color(0xFF006E1B)
        assertEquals(custom, selection.copy(customSeedColor = custom).effectiveSeedColor(coverSeed))
    }

    @Test
    fun coverSeedExtractionIsDeterministicAndHandlesEmptyInput() {
        assertNull(extractCoverSeedColor(IntArray(0)))
        val redPixels = IntArray(64) { 0xFFFF0000.toInt() }
        val greenPixels = IntArray(64) { 0xFF00FF00.toInt() }
        val red = extractCoverSeedColor(redPixels)
        val green = extractCoverSeedColor(greenPixels)
        assertNotNull(red)
        assertNotNull(green)
        assertEquals(red, extractCoverSeedColor(redPixels))
        assertNotEquals(red, green)
    }
}
