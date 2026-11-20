package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.app.library.BridgedSettingsRepository
import app.koyura.app.library.LocalProductionSettings
import app.koyura.designsystem.theme.ThemeSelection
import app.koyura.designsystem.theme.VisualTheme
import app.koyura.features.settings.SettingsSession
import app.koyura.features.settings.rememberSettingsSession
import com.materialkolor.PaletteStyle

internal val LocalAppSettingsSession = staticCompositionLocalOf<SettingsSession?> { null }
internal val LocalAppearance = staticCompositionLocalOf<ThemeSelection?> { null }

/** One in-memory settings session supplies the app palette and all settings destinations. */
@Composable
internal fun AppAppearance(content: @Composable () -> Unit) {
    // 生产装配存在时走桥接仓储（启动 hydrate + 写回外观），预览/测试仍落内存 Fake。
    val production = LocalProductionSettings.current
    val scope = rememberCoroutineScope()
    val repository = remember(production, scope) {
        production?.let { BridgedSettingsRepository(it, scope) }
    }
    val session = rememberSettingsSession(repository)
    LaunchedEffect(repository, session) {
        repository?.let { session.replace(it.hydrate()) }
    }
    val prefs = session.uiState.preferences
    val scene = remember { movableContentOf<@Composable () -> Unit> { body -> body() } }
    val selection = ThemeSelection(
        preset = prefs.themePreset,
        mode = prefs.themeMode,
        amoled = prefs.amoled,
        coverBased = prefs.coverBased,
        coverBasedStyle = PaletteStyle.valueOf(prefs.coverBasedStyle.paletteStyleName),
        customSeedColor = prefs.customSeedColor,
        customStyle = PaletteStyle.valueOf(prefs.customStyle.paletteStyleName),
    )
    CompositionLocalProvider(
        LocalAppSettingsSession provides session,
        LocalAppearance provides selection,
    ) {
        VisualTheme(
            seedColor = selection.customSeedColor,
            preset = selection.preset,
            mode = selection.mode,
            amoled = selection.amoled,
            paletteStyle = selection.customStyle,
        ) { scene(content) }
    }
}
