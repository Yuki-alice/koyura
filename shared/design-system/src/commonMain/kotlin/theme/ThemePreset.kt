package app.koyura.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 明暗模式，对应参考实现 `UiPreferences.themeMode()` 的 LIGHT / DARK / SYSTEM。
 * SYSTEM 跟随系统；在离屏渲染或测试里由调用方显式传入 [systemDark] 决定。
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * 19 套静态主题，命名与参考实现 `AppTheme` 枚举一致；[label] 供设置页展示。
 * 色值来自 Komikku 的 colorscheme 实现，逐值落在 [staticThemePalette]。
 */
enum class ThemePreset(val label: String) {
    DEFAULT("Default"),
    CATPPUCCIN("Catppuccin"),
    GREEN_APPLE("Green Apple"),
    LAVENDER("Lavender"),
    MIDNIGHT_DUSK("Midnight Dusk"),
    MONOCHROME("Monochrome"),
    NORD("Nord"),
    STRAWBERRY_DAIQUIRI("Strawberry Daiquiri"),
    TAKO("Tako"),
    TEALTURQUOISE("Teal Turquoise"),
    TIDAL_WAVE("Tidal Wave"),
    YINYANG("Yin & Yang"),
    YOTSUBA("Yotsuba"),
    CLOUDFLARE("Cloudflare"),
    COTTONCANDY("Cotton Candy"),
    DOOM("Doom"),
    MATRIX("Matrix"),
    MOCHA("Mocha"),
    SAPPHIRE("Sapphire"),
}

/** 一套静态主题在浅色与深色下的完整色板。 */
internal data class ThemePalette(
    val light: ColorScheme,
    val dark: ColorScheme,
)

/**
 * AMOLED 覆写，逐值照参考实现 `BaseColorScheme.getColorScheme`：
 * 背景/表面置纯黑，容器分层用 #0C0C0C / #131313 / #1B1B1B，避免滚动内容在纯黑上消失。
 * 非 MONET 主题均覆盖深色容器（`overrideDarkSurfaceContainers = true`）。
 */
internal fun amoledColorScheme(dark: ColorScheme): ColorScheme = dark.copy(
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0C0C0C),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF131313),
    surfaceContainerHighest = Color(0xFF1B1B1B),
)

/**
 * 纯函数形式的主题决议，便于单测直接断言，也供 [VisualTheme] 与既有 [ReaderTheme] 共用。
 * 浅色主题不套用 AMOLED 覆写（与参考实现一致）。
 */
internal fun resolveColorScheme(
    preset: ThemePreset,
    mode: ThemeMode,
    systemDark: Boolean,
    amoled: Boolean,
): ColorScheme {
    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val palette = staticThemePalette(preset)
    if (!isDark) return palette.light
    val dark = palette.dark
    return if (amoled) amoledColorScheme(dark) else dark
}
