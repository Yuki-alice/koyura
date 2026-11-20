package app.koyura.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * 封面种子色生成色板的纯函数表示，与 [VisualTheme] 的 seed 分支使用同一套 materialkolor 算法。
 * 抽出纯函数是为了让「seed 不同 → scheme 不同」可以在普通单测中断言，
 * 不必把带颜色过渡动画的 Composable 主题拖进测试。
 */
fun seedColorScheme(
    seedColor: Color,
    isDark: Boolean,
    amoled: Boolean = false,
    style: PaletteStyle = PaletteStyle.Vibrant,
): ColorScheme = dynamicColorScheme(
    seedColor = seedColor,
    isDark = isDark,
    isAmoled = amoled,
    style = style,
)

/**
 * 主题决议的纯函数映射：[seedColor] 为空时回退静态主题，
 * 否则按封面种子色生成动态色板。与 [VisualTheme] 两个重载的分支一一对应。
 */
fun resolveThemeColorScheme(
    seedColor: Color?,
    preset: ThemePreset = ThemePreset.DEFAULT,
    mode: ThemeMode = ThemeMode.SYSTEM,
    systemDark: Boolean = false,
    amoled: Boolean = false,
    style: PaletteStyle = PaletteStyle.Vibrant,
): ColorScheme {
    if (seedColor == null) {
        return resolveColorScheme(preset, mode, systemDark, amoled)
    }
    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    return seedColorScheme(seedColor, isDark, amoled, style)
}
