package app.koyura.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import app.koyura.features.resources.Res
import app.koyura.features.resources.settings_app_language_system_default_currently
import app.koyura.features.resources.settings_app_theme
import app.koyura.features.resources.settings_cover_based_theme
import app.koyura.features.resources.settings_cover_color_style
import app.koyura.features.resources.settings_custom
import app.koyura.features.resources.settings_custom_seed_color
import app.koyura.features.resources.settings_custom_theme_style
import app.koyura.features.resources.settings_dark
import app.koyura.features.resources.settings_date_format
import app.koyura.features.resources.settings_derive_colors_from_the_current_cover
import app.koyura.features.resources.settings_display
import app.koyura.features.resources.settings_falls_back_to_the_selected_theme_when_not_set
import app.koyura.features.resources.settings_language
import app.koyura.features.resources.settings_light
import app.koyura.features.resources.settings_not_set
import app.koyura.features.resources.settings_pure_black_dark_theme
import app.koyura.features.resources.settings_relative_time
import app.koyura.features.resources.settings_show_timestamps_like_3_hours_ago
import app.koyura.features.resources.settings_system
import app.koyura.features.resources.settings_theme
import app.koyura.features.resources.settings_use_amoled_black_backgrounds_in_dark_mode
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppCard
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme
import app.koyura.designsystem.theme.ThemeMode
import app.koyura.designsystem.theme.ThemePreset

private val ThemeCardWidth = DesignTokens.Sizes.ThemePreviewCard
// 主题预览卡 9:16（白皮书 07 §7.16 / T2.9）：宽 114dp → 高 ≈202.7dp。
private val ThemeCardHeight = DesignTokens.Sizes.ThemePreviewCard * (16f / 9f)

/** 外观子页（白皮书 7.9）：主题模式分段 + 19 套主题卡 + AMOLED + 封面取色 + 显示/语言占位。 */
@Composable
internal fun AppearanceSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    // composition local 只能在 @Composable 作用域读取；点击回调里复用这个值。
    val fallbackSeed = UiTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionHeader(stringResource(Res.string.settings_theme))

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

        AppText(
            text = stringResource(Res.string.settings_app_theme),
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.Small,
            ),
            color = UiTheme.colorScheme.onSurfaceVariant,
            style = UiTheme.typography.bodyMedium,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.ExtraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            items(ThemePreset.entries, key = { it.name }) { preset ->
                ThemePresetCard(
                    preset = preset,
                    selected = preset == prefs.themePreset,
                    onClick = { session.update { it.copy(themePreset = preset) } },
                )
            }
        }

        SettingsSwitchRow(
            title = stringResource(Res.string.settings_pure_black_dark_theme),
            subtitle = stringResource(Res.string.settings_use_amoled_black_backgrounds_in_dark_mode),
            checked = prefs.amoled,
            enabled = prefs.themeMode != ThemeMode.LIGHT,
            onCheckedChange = { checked -> session.update { it.copy(amoled = checked) } },
            testTag = "settings-amoled",
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_cover_based_theme),
            subtitle = stringResource(Res.string.settings_derive_colors_from_the_current_cover),
            checked = prefs.coverBased,
            onCheckedChange = { checked -> session.update { it.copy(coverBased = checked) } },
            testTag = "settings-cover-based",
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_cover_color_style),
            currentValue = settingOptionLabel(prefs.coverBasedStyle.label),
            enabled = prefs.coverBased,
            onClick = {
                val styles = CoverBlendStyle.entries
                val next = styles[(styles.indexOf(prefs.coverBasedStyle) + 1) % styles.size]
                session.update { it.copy(coverBasedStyle = next) }
            },
            testTag = "settings-cover-style",
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_custom_seed_color),
            currentValue = if (prefs.customSeedColor == null) stringResource(Res.string.settings_not_set) else stringResource(Res.string.settings_custom),
            subtitle = stringResource(Res.string.settings_falls_back_to_the_selected_theme_when_not_set),
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
            title = stringResource(Res.string.settings_custom_theme_style),
            currentValue = settingOptionLabel(prefs.customStyle.label),
            enabled = prefs.customSeedColor != null,
            onClick = {
                val styles = CoverBlendStyle.entries
                val next = styles[(styles.indexOf(prefs.customStyle) + 1) % styles.size]
                session.update { it.copy(customStyle = next) }
            },
            testTag = "settings-custom-style",
        )

        SettingsSectionHeader(stringResource(Res.string.settings_display))
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_relative_time),
            subtitle = stringResource(Res.string.settings_show_timestamps_like_3_hours_ago),
            checked = prefs.relativeTime,
            onCheckedChange = { checked -> session.update { it.copy(relativeTime = checked) } },
            testTag = "settings-relative-time",
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_date_format),
            currentValue = settingOptionLabel(prefs.dateFormat.label),
            onClick = {
                val next = when (prefs.dateFormat) {
                    SettingsDateFormat.RELATIVE -> SettingsDateFormat.ABSOLUTE
                    SettingsDateFormat.ABSOLUTE -> SettingsDateFormat.RELATIVE
                }
                session.update { it.copy(dateFormat = next) }
            },
            testTag = "settings-date-format",
        )

        SettingsSectionHeader(stringResource(Res.string.settings_language))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_language),
            subtitle = stringResource(Res.string.settings_app_language_system_default_currently),
            currentValue = settingOptionLabel(prefs.language),
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
        UiTheme.colorScheme.surfaceVariant
    } else {
        UiTheme.colorScheme.surfaceContainerLow
    }
    AppCard(
        modifier = modifier
            .width(ThemeCardWidth)
            .height(ThemeCardHeight)
            .clickable(onClick = onClick),
        containerColor = container,
        border = if (selected) {
            BorderStroke(DesignTokens.Sizes.SelectionOutline, UiTheme.colorScheme.primary)
        } else {
            null
        },
        shape = RoundedCornerShape(DesignTokens.Radius.Large),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 9:16 卡片里色块撑满剩余高度（原 40dp 色块只适合方形预览）。
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        previewSwatchColor(preset.ordinal),
                        RoundedCornerShape(DesignTokens.Radius.Small),
                    ),
            )
            AppText(
                text = preset.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.Spacing.Small)
                    .background(
                        if (selected) UiTheme.colorScheme.primaryContainer else container,
                        RoundedCornerShape(DesignTokens.Radius.Small),
                    )
                    .padding(vertical = DesignTokens.Spacing.ExtraSmall),
                color = if (selected) {
                    UiTheme.colorScheme.onPrimaryContainer
                } else {
                    UiTheme.colorScheme.onSurfaceVariant
                },
                style = UiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> stringResource(Res.string.settings_light)
    ThemeMode.DARK -> stringResource(Res.string.settings_dark)
    ThemeMode.SYSTEM -> stringResource(Res.string.settings_system)
}
