// 由 docs/design-whitepaper/assets/design-tokens.json 生成，勿手工编辑。
// 19 套静态主题在单测中的期望值；surfaceContainer 为 null 表示参考实现未定义该角色。
package app.koyura.designsystem.theme

import androidx.compose.ui.graphics.Color

internal data class ExpectedScheme(
    val preset: ThemePreset,
    val lightPrimary: Color,
    val lightBackground: Color,
    val lightSurfaceContainer: Color?,
    val darkPrimary: Color,
    val darkBackground: Color,
    val darkSurfaceContainer: Color?,
)

internal val expectedSchemes = listOf(
    // Default
    ExpectedScheme(
        preset = ThemePreset.DEFAULT,
        lightPrimary = Color(0xFF0058CA),
        lightBackground = Color(0xFFFEFBFF),
        lightSurfaceContainer = Color(0xFFF3EDF7),
        darkPrimary = Color(0xFFB0C6FF),
        darkBackground = Color(0xFF1B1B1F),
        darkSurfaceContainer = Color(0xFF211F26),
    ),
    // Catppuccin
    ExpectedScheme(
        preset = ThemePreset.CATPPUCCIN,
        lightPrimary = Color(0xFF8839EF),
        lightBackground = Color(0xFFE6E9EF),
        lightSurfaceContainer = Color(0xFFEFF1F5),
        darkPrimary = Color(0xFFCBA6F7),
        darkBackground = Color(0xFF181825),
        darkSurfaceContainer = Color(0xFF1E1E2E),
    ),
    // Green Apple
    ExpectedScheme(
        preset = ThemePreset.GREEN_APPLE,
        lightPrimary = Color(0xFF005927),
        lightBackground = Color(0xFFF6FBF2),
        lightSurfaceContainer = Color(0xFFEAEFE6),
        darkPrimary = Color(0xFF7ADB8F),
        darkBackground = Color(0xFF0F1510),
        darkSurfaceContainer = Color(0xFF1C211C),
    ),
    // Lavender
    ExpectedScheme(
        preset = ThemePreset.LAVENDER,
        lightPrimary = Color(0xFF6D41C8),
        lightBackground = Color(0xFFEDE2FF),
        lightSurfaceContainer = Color(0xFFE4D5F8),
        darkPrimary = Color(0xFFA177FF),
        darkBackground = Color(0xFF111129),
        darkSurfaceContainer = Color(0xFF1D193B),
    ),
    // Midnight Dusk
    ExpectedScheme(
        preset = ThemePreset.MIDNIGHT_DUSK,
        lightPrimary = Color(0xFFBB0054),
        lightBackground = Color(0xFFFFFBFF),
        lightSurfaceContainer = Color(0xFFF9E6F1),
        darkPrimary = Color(0xFFF02475),
        darkBackground = Color(0xFF16151D),
        darkSurfaceContainer = Color(0xFF281624),
    ),
    // Monochrome
    ExpectedScheme(
        preset = ThemePreset.MONOCHROME,
        lightPrimary = Color(0xFF000000),
        lightBackground = Color(0xFFFFFFFF),
        lightSurfaceContainer = Color(0xFFFFFFFF),
        darkPrimary = Color(0xFFFFFFFF),
        darkBackground = Color(0xFF000000),
        darkSurfaceContainer = Color(0xFF000000),
    ),
    // Nord
    ExpectedScheme(
        preset = ThemePreset.NORD,
        lightPrimary = Color(0xFF5E81AC),
        lightBackground = Color(0xFFECEFF4),
        lightSurfaceContainer = Color(0xFFDAE0EA),
        darkPrimary = Color(0xFF88C0D0),
        darkBackground = Color(0xFF2E3440),
        darkSurfaceContainer = Color(0xFF414C5C),
    ),
    // Strawberry Daiquiri
    ExpectedScheme(
        preset = ThemePreset.STRAWBERRY_DAIQUIRI,
        lightPrimary = Color(0xFFA10833),
        lightBackground = Color(0xFFFAFAFA),
        lightSurfaceContainer = Color(0xFFF6EAED),
        darkPrimary = Color(0xFFFFB2B8),
        darkBackground = Color(0xFF201A1A),
        darkSurfaceContainer = Color(0xFF322727),
    ),
    // Tako
    ExpectedScheme(
        preset = ThemePreset.TAKO,
        lightPrimary = Color(0xFF66577E),
        lightBackground = Color(0xFFF7F5FF),
        lightSurfaceContainer = Color(0xFFE8E0EB),
        darkPrimary = Color(0xFFF3B375),
        darkBackground = Color(0xFF21212E),
        darkSurfaceContainer = Color(0xFF2A2A3C),
    ),
    // Teal & Turquoise
    ExpectedScheme(
        preset = ThemePreset.TEALTURQUOISE,
        lightPrimary = Color(0xFF008080),
        lightBackground = Color(0xFFFAFAFA),
        lightSurfaceContainer = Color(0xFFEBF3F1),
        darkPrimary = Color(0xFF40E0D0),
        darkBackground = Color(0xFF202125),
        darkSurfaceContainer = Color(0xFF233133),
    ),
    // Tidal Wave
    ExpectedScheme(
        preset = ThemePreset.TIDAL_WAVE,
        lightPrimary = Color(0xFF006780),
        lightBackground = Color(0xFFFDFBFF),
        lightSurfaceContainer = Color(0xFFE8EFF5),
        darkPrimary = Color(0xFF5ED4FC),
        darkBackground = Color(0xFF001C3B),
        darkSurfaceContainer = Color(0xFF082B4B),
    ),
    // Yin & Yang
    ExpectedScheme(
        preset = ThemePreset.YINYANG,
        lightPrimary = Color(0xFF000000),
        lightBackground = Color(0xFFFDFDFD),
        lightSurfaceContainer = Color(0xFFE8E8E8),
        darkPrimary = Color(0xFFFFFFFF),
        darkBackground = Color(0xFF1E1E1E),
        darkSurfaceContainer = Color(0xFF313131),
    ),
    // Yotsuba
    ExpectedScheme(
        preset = ThemePreset.YOTSUBA,
        lightPrimary = Color(0xFFAE3200),
        lightBackground = Color(0xFFFCFCFC),
        lightSurfaceContainer = Color(0xFFF6EBE7),
        darkPrimary = Color(0xFFFFB59D),
        darkBackground = Color(0xFF211A18),
        darkSurfaceContainer = Color(0xFF332723),
    ),
    // Cloudflare
    ExpectedScheme(
        preset = ThemePreset.CLOUDFLARE,
        lightPrimary = Color(0xFFF38020),
        lightBackground = Color(0xFFEFF2F5),
        lightSurfaceContainer = null,
        darkPrimary = Color(0xFFF38020),
        darkBackground = Color(0xFF1B1B22),
        darkSurfaceContainer = null,
    ),
    // Cotton Candy
    ExpectedScheme(
        preset = ThemePreset.COTTONCANDY,
        lightPrimary = Color(0xFF8F4A4C),
        lightBackground = Color(0xFFFFF8F7),
        lightSurfaceContainer = Color(0xFFFBEAEB),
        darkPrimary = Color(0xFFFFB3B4),
        darkBackground = Color(0xFF1A1111),
        darkSurfaceContainer = Color(0xFF261D1E),
    ),
    // Doom
    ExpectedScheme(
        preset = ThemePreset.DOOM,
        lightPrimary = Color(0xFFFF0000),
        lightBackground = Color(0xFF212121),
        lightSurfaceContainer = null,
        darkPrimary = Color(0xFFFF0000),
        darkBackground = Color(0xFF1B1B1B),
        darkSurfaceContainer = null,
    ),
    // Matrix
    ExpectedScheme(
        preset = ThemePreset.MATRIX,
        lightPrimary = Color(0xFF00FF00),
        lightBackground = Color(0xFF000000),
        lightSurfaceContainer = null,
        darkPrimary = Color(0xFF00FF00),
        darkBackground = Color(0xFF111111),
        darkSurfaceContainer = null,
    ),
    // Mocha
    ExpectedScheme(
        preset = ThemePreset.MOCHA,
        lightPrimary = Color(0xFF89511F),
        lightBackground = Color(0xFFFFF8F5),
        lightSurfaceContainer = Color(0xFFFBEBE1),
        darkPrimary = Color(0xFFFFB77F),
        darkBackground = Color(0xFF19120C),
        darkSurfaceContainer = Color(0xFF261E18),
    ),
    // Sapphire
    ExpectedScheme(
        preset = ThemePreset.SAPPHIRE,
        lightPrimary = Color(0xFF1E88E5),
        lightBackground = Color(0xFFFFFFFF),
        lightSurfaceContainer = null,
        darkPrimary = Color(0xFF1E88E5),
        darkBackground = Color(0xFF212121),
        darkSurfaceContainer = null,
    ),
)
