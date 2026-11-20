package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
    val session = rememberSettingsSession()
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
