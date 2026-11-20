package app.koyura.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import app.koyura.features.resources.Res
import app.koyura.features.resources.settings_1_0_0_settings_preview
import app.koyura.features.resources.settings_all
import app.koyura.features.resources.settings_app
import app.koyura.features.resources.settings_auto_download_new_chapters
import app.koyura.features.resources.settings_auto_scroll
import app.koyura.features.resources.settings_back
import app.koyura.features.resources.settings_background_color
import app.koyura.features.resources.settings_backup
import app.koyura.features.resources.settings_backup_frequency
import app.koyura.features.resources.settings_build
import app.koyura.features.resources.settings_categories
import app.koyura.features.resources.settings_check_every_library_entry_in_one_pass
import app.koyura.features.resources.settings_choose_file
import app.koyura.features.resources.settings_clear_cache
import app.koyura.features.resources.settings_crop_borders
import app.koyura.features.resources.settings_daily
import app.koyura.features.resources.settings_debug
import app.koyura.features.resources.settings_default_category
import app.koyura.features.resources.settings_default_language
import app.koyura.features.resources.settings_display
import app.koyura.features.resources.settings_download_directory
import app.koyura.features.resources.settings_download_only_over_wi_fi
import app.koyura.features.resources.settings_enable_debug_mode
import app.koyura.features.resources.settings_enable_sync
import app.koyura.features.resources.settings_follow_theme
import app.koyura.features.resources.settings_global_update
import app.koyura.features.resources.settings_help
import app.koyura.features.resources.settings_library_columns
import app.koyura.features.resources.settings_licenses
import app.koyura.features.resources.settings_limit_updates_by_network_and_schedule
import app.koyura.features.resources.settings_links
import app.koyura.features.resources.settings_local_development_build
import app.koyura.features.resources.settings_log_level
import app.koyura.features.resources.settings_multi_source_search
import app.koyura.features.resources.settings_no_remote_services_configured_yet_connect_a_service_to
import app.koyura.features.resources.settings_no_tracking_services_connected_sign_in_to_sync_reading
import app.koyura.features.resources.settings_not_calculated
import app.koyura.features.resources.settings_not_configured
import app.koyura.features.resources.settings_not_connected
import app.koyura.features.resources.settings_organize_the_library_with_categories
import app.koyura.features.resources.settings_parallel_downloads
import app.koyura.features.resources.settings_proxy
import app.koyura.features.resources.settings_push_progress_when_a_chapter_is_marked_read
import app.koyura.features.resources.settings_queue
import app.koyura.features.resources.settings_reading
import app.koyura.features.resources.settings_reading_direction
import app.koyura.features.resources.settings_reading_mode
import app.koyura.features.resources.settings_remote
import app.koyura.features.resources.settings_removes_downloaded_images_and_temporary_files
import app.koyura.features.resources.settings_restore_backup
import app.koyura.features.resources.settings_screen_brightness
import app.koyura.features.resources.settings_search
import app.koyura.features.resources.settings_search_all_installed_sources_at_once
import app.koyura.features.resources.settings_services
import app.koyura.features.resources.settings_show_adult_sources
import app.koyura.features.resources.settings_show_categories
import app.koyura.features.resources.settings_sort_by_title
import app.koyura.features.resources.settings_storage
import app.koyura.features.resources.settings_storage_usage
import app.koyura.features.resources.settings_sync
import app.koyura.features.resources.settings_sync_interval
import app.koyura.features.resources.settings_tap_to_recalculate_cache_size
import app.koyura.features.resources.settings_tracking_service
import app.koyura.features.resources.settings_trim_page_edges_before_display
import app.koyura.features.resources.settings_update_channel
import app.koyura.features.resources.settings_update_restrictions
import app.koyura.features.resources.settings_update_tracking_automatically
import app.koyura.features.resources.settings_updates
import app.koyura.features.resources.settings_version
import app.koyura.features.resources.settings_where_downloaded_chapters_are_stored
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme

/** 子页分发：手机 push 页和平板右栏共用同一份内容，避免两套实现漂移。 */
@Composable
internal fun SettingsSubpageContent(
    session: SettingsSession,
    subpage: SettingsSubpage,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showBackButton: Boolean = false,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
                .padding(horizontal = DesignTokens.Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                ActionIconButton(
                    icon = ActionIcon.Back,
                    label = stringResource(Res.string.settings_back),
                    onClick = { onBack?.invoke() },
                )
            }
            AppText(
                text = subpage.displayLabel(),
                color = UiTheme.colorScheme.onSurface,
                style = UiTheme.typography.titleLarge,
                modifier = Modifier.padding(start = DesignTokens.Spacing.Small),
            )
        }
        AppDivider()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (subpage) {
                SettingsSubpage.APPEARANCE -> AppearanceSettingsScreen(session)
                SettingsSubpage.LIBRARY -> LibrarySettingsScreen(session)
                SettingsSubpage.READER -> ReaderSettingsScreen(session)
                SettingsSubpage.DOWNLOADS -> DownloadsSettingsScreen(session)
                SettingsSubpage.TRACKING -> TrackingSettingsScreen(session)
                SettingsSubpage.CONNECTIONS -> ConnectionsSettingsScreen(session)
                SettingsSubpage.BROWSE -> BrowseSettingsScreen(session)
                SettingsSubpage.DATA_STORAGE -> DataStorageSettingsScreen(session)
                SettingsSubpage.ADVANCED -> AdvancedSettingsScreen(session)
                SettingsSubpage.ABOUT -> AboutSettingsScreen()
            }
        }
    }
}

@Composable
internal fun LibrarySettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_categories))
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_show_categories),
            subtitle = stringResource(Res.string.settings_organize_the_library_with_categories),
            checked = prefs.showCategories,
            onCheckedChange = { value -> session.update { it.copy(showCategories = value) } },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_default_category),
            currentValue = stringResource(Res.string.settings_all),
            enabled = prefs.showCategories,
            onClick = {},
        )

        SettingsSectionHeader(stringResource(Res.string.settings_updates))
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_global_update),
            subtitle = stringResource(Res.string.settings_check_every_library_entry_in_one_pass),
            checked = prefs.globalUpdate,
            onCheckedChange = { value -> session.update { it.copy(globalUpdate = value) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_update_restrictions),
            subtitle = stringResource(Res.string.settings_limit_updates_by_network_and_schedule),
            checked = prefs.updateRestrictions,
            onCheckedChange = { value -> session.update { it.copy(updateRestrictions = value) } },
        )

        SettingsSectionHeader(stringResource(Res.string.settings_display))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_library_columns),
            currentValue = prefs.libraryColumns.toString(),
            onClick = {
                val next = if (prefs.libraryColumns >= 4) 1 else prefs.libraryColumns + 1
                session.update { it.copy(libraryColumns = next) }
            },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_sort_by_title),
            checked = prefs.sortByTitle,
            onCheckedChange = { value -> session.update { it.copy(sortByTitle = value) } },
        )
    }
}

@Composable
internal fun ReaderSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    val readerModes = listOf("Webtoon", "Continuous", "Single")
    val directions = listOf("Vertical", "Left to right", "Right to left")
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_reading))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_reading_mode),
            currentValue = settingOptionLabel(prefs.readerMode),
            onClick = {
                val next = readerModes[(readerModes.indexOf(prefs.readerMode) + 1) % readerModes.size]
                session.update { it.copy(readerMode = next) }
            },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_reading_direction),
            currentValue = settingOptionLabel(prefs.readingDirection),
            onClick = {
                val next = directions[(directions.indexOf(prefs.readingDirection) + 1) % directions.size]
                session.update { it.copy(readingDirection = next) }
            },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_crop_borders),
            subtitle = stringResource(Res.string.settings_trim_page_edges_before_display),
            checked = prefs.cropBorders,
            onCheckedChange = { value -> session.update { it.copy(cropBorders = value) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_auto_scroll),
            checked = prefs.autoScroll,
            onCheckedChange = { value -> session.update { it.copy(autoScroll = value) } },
        )

        SettingsSectionHeader(stringResource(Res.string.settings_display))
        SettingsSliderRow(
            title = stringResource(Res.string.settings_screen_brightness),
            value = prefs.screenBrightness,
            valueText = "${(prefs.screenBrightness * 100).toInt()}%",
            valueRange = 0.1f..1f,
            onValueChange = { value -> session.update { it.copy(screenBrightness = value) } },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_background_color),
            currentValue = stringResource(Res.string.settings_follow_theme),
            onClick = {},
        )
    }
}

@Composable
internal fun DownloadsSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_storage))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_download_directory),
            subtitle = stringResource(Res.string.settings_where_downloaded_chapters_are_stored),
            currentValue = settingOptionLabel(prefs.downloadDirectory),
            onClick = {},
        )

        SettingsSectionHeader(stringResource(Res.string.settings_queue))
        SettingsSliderRow(
            title = stringResource(Res.string.settings_parallel_downloads),
            value = prefs.parallelDownloads.toFloat(),
            valueText = prefs.parallelDownloads.toString(),
            valueRange = 1f..8f,
            steps = 7,
            onValueChange = { value -> session.update { it.copy(parallelDownloads = value.toInt()) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_download_only_over_wi_fi),
            checked = prefs.wifiOnly,
            onCheckedChange = { value -> session.update { it.copy(wifiOnly = value) } },
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_auto_download_new_chapters),
            checked = prefs.autoDownload,
            onCheckedChange = { value -> session.update { it.copy(autoDownload = value) } },
        )
    }
}

@Composable
internal fun TrackingSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_services))
        SettingsEmptyState(stringResource(Res.string.settings_no_tracking_services_connected_sign_in_to_sync_reading))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_tracking_service),
            currentValue = stringResource(Res.string.settings_not_connected),
            onClick = {},
        )

        SettingsSectionHeader(stringResource(Res.string.settings_sync))
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_update_tracking_automatically),
            subtitle = stringResource(Res.string.settings_push_progress_when_a_chapter_is_marked_read),
            checked = prefs.autoUpdateTracking,
            onCheckedChange = { value -> session.update { it.copy(autoUpdateTracking = value) } },
        )
    }
}

@Composable
internal fun ConnectionsSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_remote))
        SettingsEmptyState(stringResource(Res.string.settings_no_remote_services_configured_yet_connect_a_service_to))
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_enable_sync),
            checked = prefs.syncEnabled,
            onCheckedChange = { value -> session.update { it.copy(syncEnabled = value) } },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_sync_interval),
            currentValue = stringResource(Res.string.settings_daily),
            enabled = prefs.syncEnabled,
            onClick = {},
        )
    }
}

@Composable
internal fun BrowseSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_search))
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_multi_source_search),
            subtitle = stringResource(Res.string.settings_search_all_installed_sources_at_once),
            checked = prefs.multiSourceSearch,
            onCheckedChange = { value -> session.update { it.copy(multiSourceSearch = value) } },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_default_language),
            currentValue = stringResource(Res.string.settings_all),
            onClick = {},
        )
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_show_adult_sources),
            checked = prefs.showAdultSources,
            onCheckedChange = { value -> session.update { it.copy(showAdultSources = value) } },
        )
    }
}

@Composable
internal fun DataStorageSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    val frequencies = listOf("Off", "Daily", "Weekly", "Monthly")
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_backup))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_backup_frequency),
            currentValue = settingOptionLabel(prefs.backupFrequency),
            onClick = {
                val next = frequencies[(frequencies.indexOf(prefs.backupFrequency) + 1) % frequencies.size]
                session.update { it.copy(backupFrequency = next) }
            },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_restore_backup),
            currentValue = stringResource(Res.string.settings_choose_file),
            onClick = {},
        )

        SettingsSectionHeader(stringResource(Res.string.settings_storage))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_storage_usage),
            subtitle = stringResource(Res.string.settings_tap_to_recalculate_cache_size),
            currentValue = stringResource(Res.string.settings_not_calculated),
            onClick = {},
        )
        SettingsRow(
            title = stringResource(Res.string.settings_clear_cache),
            subtitle = stringResource(Res.string.settings_removes_downloaded_images_and_temporary_files),
            danger = true,
            onClick = {},
        )
    }
}

@Composable
internal fun AdvancedSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    val logLevels = listOf("Normal", "Verbose", "Off")
    val channels = listOf("Stable", "Beta")
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_debug))
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_enable_debug_mode),
            checked = prefs.debugEnabled,
            onCheckedChange = { value -> session.update { it.copy(debugEnabled = value) } },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_log_level),
            currentValue = settingOptionLabel(prefs.logLevel),
            onClick = {
                val next = logLevels[(logLevels.indexOf(prefs.logLevel) + 1) % logLevels.size]
                session.update { it.copy(logLevel = next) }
            },
        )

        SettingsSectionHeader(stringResource(Res.string.settings_updates))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_update_channel),
            currentValue = settingOptionLabel(prefs.updateChannel),
            onClick = {
                val next = channels[(channels.indexOf(prefs.updateChannel) + 1) % channels.size]
                session.update { it.copy(updateChannel = next) }
            },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_proxy),
            currentValue = stringResource(Res.string.settings_not_configured),
            onClick = {},
        )
    }
}

@Composable
internal fun AboutSettingsScreen(
    modifier: Modifier = Modifier,
) {
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_app))
        SettingsRow(
            title = stringResource(Res.string.settings_version),
            subtitle = stringResource(Res.string.settings_1_0_0_settings_preview),
        )
        SettingsRow(
            title = stringResource(Res.string.settings_build),
            subtitle = stringResource(Res.string.settings_local_development_build),
        )

        SettingsSectionHeader(stringResource(Res.string.settings_links))
        SettingsRow(title = stringResource(Res.string.settings_licenses), onClick = {})
        SettingsRow(title = stringResource(Res.string.settings_help), onClick = {})
    }
}

/** 各子页共用的可滚动内容容器。 */
@Composable
private fun SettingsSubpageScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}
