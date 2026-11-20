@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.koyura.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

/**
 * 静态主题入口，对应参考实现 `TachiyomiTheme(appTheme, amoled, content)` 的
 * `BaseTachiyomiTheme`：从 19 套静态色板中按 [preset] / [mode] / [amoled] 选出 ColorScheme，
 * 再交给 `MaterialExpressiveTheme`（Komikku 同款表达性主题）。
 */
@Composable
fun VisualTheme(
    preset: ThemePreset = ThemePreset.DEFAULT,
    mode: ThemeMode = ThemeMode.SYSTEM,
    amoled: Boolean = false,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = remember(preset, mode, systemDark, amoled) {
        resolveColorScheme(preset, mode, systemDark, amoled)
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

/**
 * 封面取色主题入口，对应参考实现 `TachiyomiTheme(seedColor, appTheme, amoled, typography, content)`：
 * `seedColor == null` 回退静态主题；否则用 `DynamicMaterialExpressiveTheme` 按封面主色生成
 * 表达性色板。默认风格 Vibrant（参考实现 `themeCoverBasedStyle()` 默认值）。
 */
@Composable
fun VisualTheme(
    seedColor: Color?,
    preset: ThemePreset = ThemePreset.DEFAULT,
    mode: ThemeMode = ThemeMode.SYSTEM,
    amoled: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.Vibrant,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit,
) {
    if (seedColor == null) {
        VisualTheme(
            preset = preset,
            mode = mode,
            amoled = amoled,
            typography = typography,
            content = content,
        )
        return
    }

    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    DynamicMaterialExpressiveTheme(
        seedColor = seedColor,
        isDark = isDark,
        isAmoled = amoled,
        style = paletteStyle,
        typography = typography,
        animate = true,
        content = content,
    )
}
