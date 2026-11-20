package app.koyura.features.settings

import androidx.compose.runtime.Composable
import app.koyura.features.resources.Res
import app.koyura.features.resources.settings_about
import app.koyura.features.resources.settings_absolute
import app.koyura.features.resources.settings_advanced
import app.koyura.features.resources.settings_appearance
import app.koyura.features.resources.settings_backup_storage_and_cache
import app.koyura.features.resources.settings_beta
import app.koyura.features.resources.settings_browse
import app.koyura.features.resources.settings_categories_updates_and_display
import app.koyura.features.resources.settings_connections
import app.koyura.features.resources.settings_content
import app.koyura.features.resources.settings_continuous
import app.koyura.features.resources.settings_daily
import app.koyura.features.resources.settings_data_storage
import app.koyura.features.resources.settings_debug_logs_and_updates
import app.koyura.features.resources.settings_default_directory
import app.koyura.features.resources.settings_directory_network_and_queue
import app.koyura.features.resources.settings_downloads
import app.koyura.features.resources.settings_english
import app.koyura.features.resources.settings_expressive
import app.koyura.features.resources.settings_fidelity
import app.koyura.features.resources.settings_fruit_salad
import app.koyura.features.resources.settings_left_to_right
import app.koyura.features.resources.settings_library
import app.koyura.features.resources.settings_monochrome
import app.koyura.features.resources.settings_monthly
import app.koyura.features.resources.settings_neutral
import app.koyura.features.resources.settings_normal
import app.koyura.features.resources.settings_off
import app.koyura.features.resources.settings_rainbow
import app.koyura.features.resources.settings_reader
import app.koyura.features.resources.settings_reading_mode_and_page_layout
import app.koyura.features.resources.settings_relative
import app.koyura.features.resources.settings_right_to_left
import app.koyura.features.resources.settings_single
import app.koyura.features.resources.settings_sources_and_search
import app.koyura.features.resources.settings_stable
import app.koyura.features.resources.settings_sync_and_remote_services
import app.koyura.features.resources.settings_system
import app.koyura.features.resources.settings_theme_display_and_language
import app.koyura.features.resources.settings_tonal_spot
import app.koyura.features.resources.settings_tracking
import app.koyura.features.resources.settings_tracking_services
import app.koyura.features.resources.settings_verbose
import app.koyura.features.resources.settings_version_and_licenses
import app.koyura.features.resources.settings_vertical
import app.koyura.features.resources.settings_vibrant
import app.koyura.features.resources.settings_webtoon
import app.koyura.features.resources.settings_weekly
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsSubpage.displayLabel(): String = stringResource(
    when (this) {
        SettingsSubpage.APPEARANCE -> Res.string.settings_appearance
        SettingsSubpage.LIBRARY -> Res.string.settings_library
        SettingsSubpage.READER -> Res.string.settings_reader
        SettingsSubpage.DOWNLOADS -> Res.string.settings_downloads
        SettingsSubpage.TRACKING -> Res.string.settings_tracking
        SettingsSubpage.CONNECTIONS -> Res.string.settings_connections
        SettingsSubpage.BROWSE -> Res.string.settings_browse
        SettingsSubpage.DATA_STORAGE -> Res.string.settings_data_storage
        SettingsSubpage.ADVANCED -> Res.string.settings_advanced
        SettingsSubpage.ABOUT -> Res.string.settings_about
    },
)

@Composable
internal fun SettingsSubpage.displaySummary(): String = stringResource(
    when (this) {
        SettingsSubpage.APPEARANCE -> Res.string.settings_theme_display_and_language
        SettingsSubpage.LIBRARY -> Res.string.settings_categories_updates_and_display
        SettingsSubpage.READER -> Res.string.settings_reading_mode_and_page_layout
        SettingsSubpage.DOWNLOADS -> Res.string.settings_directory_network_and_queue
        SettingsSubpage.TRACKING -> Res.string.settings_tracking_services
        SettingsSubpage.CONNECTIONS -> Res.string.settings_sync_and_remote_services
        SettingsSubpage.BROWSE -> Res.string.settings_sources_and_search
        SettingsSubpage.DATA_STORAGE -> Res.string.settings_backup_storage_and_cache
        SettingsSubpage.ADVANCED -> Res.string.settings_debug_logs_and_updates
        SettingsSubpage.ABOUT -> Res.string.settings_version_and_licenses
    },
)

/** Localizes the display of stored option values while retaining the existing settings keys. */
@Composable
internal fun settingOptionLabel(value: String): String = when (value) {
    "Webtoon" -> stringResource(Res.string.settings_webtoon)
    "Continuous" -> stringResource(Res.string.settings_continuous)
    "Single" -> stringResource(Res.string.settings_single)
    "Vertical" -> stringResource(Res.string.settings_vertical)
    "Left to right" -> stringResource(Res.string.settings_left_to_right)
    "Right to left" -> stringResource(Res.string.settings_right_to_left)
    "Off" -> stringResource(Res.string.settings_off)
    "Daily" -> stringResource(Res.string.settings_daily)
    "Weekly" -> stringResource(Res.string.settings_weekly)
    "Monthly" -> stringResource(Res.string.settings_monthly)
    "Normal" -> stringResource(Res.string.settings_normal)
    "Verbose" -> stringResource(Res.string.settings_verbose)
    "Stable" -> stringResource(Res.string.settings_stable)
    "Beta" -> stringResource(Res.string.settings_beta)
    "Default directory" -> stringResource(Res.string.settings_default_directory)
    "Relative" -> stringResource(Res.string.settings_relative)
    "Absolute" -> stringResource(Res.string.settings_absolute)
    "Tonal spot" -> stringResource(Res.string.settings_tonal_spot)
    "Neutral" -> stringResource(Res.string.settings_neutral)
    "Vibrant" -> stringResource(Res.string.settings_vibrant)
    "Expressive" -> stringResource(Res.string.settings_expressive)
    "Rainbow" -> stringResource(Res.string.settings_rainbow)
    "Fruit salad" -> stringResource(Res.string.settings_fruit_salad)
    "Monochrome" -> stringResource(Res.string.settings_monochrome)
    "Fidelity" -> stringResource(Res.string.settings_fidelity)
    "Content" -> stringResource(Res.string.settings_content)
    "System" -> stringResource(Res.string.settings_system)
    "English" -> stringResource(Res.string.settings_english)
    else -> value
}
