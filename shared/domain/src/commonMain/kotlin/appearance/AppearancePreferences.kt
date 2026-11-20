package app.koyura.domain.appearance

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppearancePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val themeId: String = "default",
    val amoled: Boolean = false,
    val useCoverColors: Boolean = false,
)
