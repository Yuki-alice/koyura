package app.koyura.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.theme.ThemeMode
import app.koyura.designsystem.theme.ThemePreset

private val ThemeCardWidth = 112.dp
private val ThemeCardHeight = 112.dp

/** 外观子页（白皮书 7.9）：主题模式分段 + 19 套主题卡 + AMOLED + 封面取色 + 显示/语言占位。 */
@Composable
internal fun AppearanceSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    // composition local 只能在 @Composable 作用域读取；点击回调里复用这个值。
    val fallbackSeed = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionHeader("Theme")

        SettingsSegmentedControl(
            items = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
            selected = prefs.themeMode,
            labelOf = { it.label() },
            onSelect = { mode -> session.update { it.copy(themeMode = mode) } },
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.Small,
            ),
            testTagPrefix = "settings-theme-mode",
        )

        Text(
            text = "App theme",
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.Small,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.ExtraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            items(ThemePreset.entries) { preset ->
                ThemePresetCard(
                    preset = preset,
                    selected = preset == prefs.themePreset,
                    onClick = { session.update { it.copy(themePreset = preset) } },
                )
            }
        }

        SettingsSwitchRow(
            title = "Pure black dark theme",
            subtitle = "Use AMOLED black backgrounds in dark mode",
            checked = prefs.amoled,
            enabled = prefs.themeMode != ThemeMode.LIGHT,
            onCheckedChange = { checked -> session.update { it.copy(amoled = checked) } },
            testTag = "settings-amoled",
        )
        SettingsSwitchRow(
            title = "Cover based theme",
            subtitle = "Derive colors from the current cover",
            checked = prefs.coverBased,
            onCheckedChange = { checked -> session.update { it.copy(coverBased = checked) } },
            testTag = "settings-cover-based",
        )
        SettingsChoiceRow(
            title = "Cover color style",
            currentValue = prefs.coverBasedStyle.label,
            enabled = prefs.coverBased,
            onClick = {
                val styles = CoverBlendStyle.entries
                val next = styles[(styles.indexOf(prefs.coverBasedStyle) + 1) % styles.size]
                session.update { it.copy(coverBasedStyle = next) }
            },
            testTag = "settings-cover-style",
        )
        SettingsChoiceRow(
            title = "Custom seed color",
            currentValue = if (prefs.customSeedColor == null) "Not set" else "Custom",
            subtitle = "Falls back to the selected theme when not set",
            onClick = {
                val next = if (prefs.customSeedColor == null) {
                    fallbackSeed
                } else {
                    null
                }
                session.update { it.copy(customSeedColor = next) }
            },
            testTag = "settings-custom-seed",
        )
        SettingsChoiceRow(
            title = "Custom theme style",
            currentValue = prefs.customStyle.label,
            enabled = prefs.customSeedColor != null,
            onClick = {
                val styles = CoverBlendStyle.entries
                val next = styles[(styles.indexOf(prefs.customStyle) + 1) % styles.size]
                session.update { it.copy(customStyle = next) }
            },
            testTag = "settings-custom-style",
        )

        SettingsSectionHeader("Display")
        SettingsSwitchRow(
            title = "Relative time",
            subtitle = "Show timestamps like “3 hours ago”",
            checked = prefs.relativeTime,
            onCheckedChange = { checked -> session.update { it.copy(relativeTime = checked) } },
            testTag = "settings-relative-time",
        )
        SettingsChoiceRow(
            title = "Date format",
            currentValue = prefs.dateFormat.label,
            onClick = {
                val next = when (prefs.dateFormat) {
                    SettingsDateFormat.RELATIVE -> SettingsDateFormat.ABSOLUTE
                    SettingsDateFormat.ABSOLUTE -> SettingsDateFormat.RELATIVE
                }
                session.update { it.copy(dateFormat = next) }
            },
            testTag = "settings-date-format",
        )

        SettingsSectionHeader("Language")
        SettingsChoiceRow(
            title = "Language",
            subtitle = "App language (system default currently)",
            currentValue = prefs.language,
            onClick = {
                val next = if (prefs.language == "System") "English" else "System"
                session.update { it.copy(language = next) }
            },
            testTag = "settings-language",
        )
    }
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = modifier
            .width(ThemeCardWidth)
            .height(ThemeCardHeight)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        shape = RoundedCornerShape(DesignTokens.Radius.Large),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SettingsPreviewSwatch(
                color = previewSwatchColor(preset.ordinal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = preset.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.Spacing.Small)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else container,
                        RoundedCornerShape(DesignTokens.Radius.Small),
                    )
                    .padding(vertical = DesignTokens.Spacing.ExtraSmall),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}
