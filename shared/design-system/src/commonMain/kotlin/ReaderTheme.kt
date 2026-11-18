package app.koyura.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Application theme aligned with Komikku's `TachiyomiTheme` default (`TachiyomiColorScheme`).
 *
 * Key colors mirror `eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme`:
 * primary #2979FF family, tertiary #47A84A family, neutral #919094. The optional [amoled]
 * flag reproduces Komikku's AMOLED override (`BaseColorScheme.getColorScheme`): pure black
 * backgrounds/surfaces with white content, and `#0C0C0C/#131313/#1B1B1B` containers so the
 * navigation bar stays distinguishable over scrolled content (whitepaper 02 §2.1/§2.4).
 */
private val TachiyomiLightColors = lightColorScheme(
    primary = Color(0xFF0058CA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    inversePrimary = Color(0xFFB0C6FF),
    secondary = Color(0xFF0058CA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF001945),
    tertiary = Color(0xFF006E1B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF95F990),
    onTertiaryContainer = Color(0xFF002203),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF3EDF7),
    onSurfaceVariant = Color(0xFF44464F),
    surfaceTint = Color(0xFF0058CA),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color(0xFFF5F1F8),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFFCF7FF),
    surfaceContainerHighest = Color(0xFFFCF7FF),
)

private val TachiyomiDarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF00429B),
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF0058CA),
    secondary = Color(0xFFB0C6FF),
    onSecondary = Color(0xFF002D6E),
    secondaryContainer = Color(0xFF00429B),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFF7ADC77),
    onTertiary = Color(0xFF003909),
    tertiaryContainer = Color(0xFF005312),
    onTertiaryContainer = Color(0xFF95F990),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF211F26),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB0C6FF),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color(0xFF1A181D),
    surfaceContainerLow = Color(0xFF1E1C22),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF292730),
    surfaceContainerHighest = Color(0xFF302E38),
)

private val AmoledDarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF00429B),
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF0058CA),
    secondary = Color(0xFFB0C6FF),
    onSecondary = Color(0xFF002D6E),
    secondaryContainer = Color(0xFF00429B),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFF7ADC77),
    onTertiary = Color(0xFF003909),
    tertiaryContainer = Color(0xFF005312),
    onTertiaryContainer = Color(0xFF95F990),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0C0C0C),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB0C6FF),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF131313),
    surfaceContainerHighest = Color(0xFF1B1B1B),
)

@Composable
fun ReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme && amoled -> AmoledDarkColors
        darkTheme -> TachiyomiDarkColors
        else -> TachiyomiLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * Komikku list-group header style (`Typography.header`): semibold bodyMedium in
 * `onSurfaceVariant`. Used for source language groups, extension groups, history dates and the
 * chapter count header so the four surfaces share one header voice.
 */
val Typography.komikkuHeader: TextStyle
    @Composable
    get() = bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )

/** Disabled-content alpha matching Komikku's `DISABLED_ALPHA`. */
const val KomikkuDisabledAlpha = 0.38f

/** Secondary-row alpha matching Komikku's `secondaryItemAlpha`. */
const val KomikkuSecondaryAlpha = 0.78f
