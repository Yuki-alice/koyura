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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.koyura.features.resources.Res
import app.koyura.features.resources.settings_1_0_0_settings_preview
import app.koyura.features.resources.settings_all
import app.koyura.features.resources.settings_app
import app.koyura.features.resources.settings_auto_download_new_chapters
import app.koyura.features.resources.settings_auto_scroll
import app.koyura.features.resources.settings_weekly
import app.koyura.features.resources.settings_hourly
import app.koyura.features.resources.settings_chinese
import app.koyura.features.resources.settings_japanese
import app.koyura.features.resources.settings_gray
import app.koyura.features.resources.settings_white
import app.koyura.features.resources.settings_http
import app.koyura.features.resources.settings_socks5
import app.koyura.features.resources.settings_storage_usage_format
import app.koyura.features.resources.settings_licenses_body
import app.koyura.features.resources.settings_help_body
import app.koyura.features.resources.settings_picker_unavailable
import app.koyura.features.resources.settings_cache_cleared
import app.koyura.features.resources.settings_cache_noop
import app.koyura.features.resources.settings_backup_unavailable
import app.koyura.features.resources.settings_off
import app.koyura.features.resources.settings_monthly
import app.koyura.features.resources.settings_english
import app.koyura.features.resources.settings_back
import app.koyura.features.resources.settings_show_onboarding
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
import app.koyura.features.resources.settings_dark
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
import app.koyura.features.resources.settings_light
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
    onOpenOnboarding: (() -> Unit)? = null,
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
                SettingsSubpage.ABOUT -> AboutSettingsScreen(onOpenOnboarding = onOpenOnboarding)
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
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val libraryRepository = app.koyura.features.library.LocalLibraryRepository.current
    LaunchedEffect(libraryRepository) {
        categoryOptions = runCatching {
            libraryRepository?.load()?.orderedCategories?.map { it.name to it.id.toString() }.orEmpty()
        }.getOrElse { emptyList() }
    }
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
            currentValue = categoryOptions.firstOrNull { it.second == prefs.defaultCategoryId }?.first
                ?: stringResource(Res.string.settings_all),
            enabled = prefs.showCategories,
            onClick = { showCategoryDialog = true },
        )
        if (showCategoryDialog) {
            SettingsChoiceDialog(
                title = stringResource(Res.string.settings_default_category),
                options = listOf(stringResource(Res.string.settings_all) to "") + categoryOptions,
                selectedValue = prefs.defaultCategoryId.orEmpty(),
                onSelect = { value ->
                    session.update { it.copy(defaultCategoryId = value.ifEmpty { null }) }
                    showCategoryDialog = false
                },
                onDismiss = { showCategoryDialog = false },
            )
        }

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
    var showBackgroundDialog by remember { mutableStateOf(false) }
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
            currentValue = settingOptionLabel(prefs.readerBackground),
            onClick = { showBackgroundDialog = true },
        )
        if (showBackgroundDialog) {
            val backgrounds = listOf(
                stringResource(Res.string.settings_follow_theme) to "Follow theme",
                stringResource(Res.string.settings_light) to "White",
                stringResource(Res.string.settings_gray) to "Gray",
                stringResource(Res.string.settings_dark) to "Black",
            )
            SettingsChoiceDialog(
                title = stringResource(Res.string.settings_background_color),
                options = backgrounds,
                selectedValue = prefs.readerBackground,
                onSelect = { value ->
                    session.update { it.copy(readerBackground = value) }
                    showBackgroundDialog = false
                },
                onDismiss = { showBackgroundDialog = false },
            )
        }
    }
}

@Composable
internal fun DownloadsSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    var showDirectoryDialog by remember { mutableStateOf(false) }
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_storage))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_download_directory),
            subtitle = stringResource(Res.string.settings_where_downloaded_chapters_are_stored),
            currentValue = settingOptionLabel(prefs.downloadDirectory),
            onClick = { showDirectoryDialog = true },
        )
        if (showDirectoryDialog) {
            SettingsMessageDialog(
                title = stringResource(Res.string.settings_download_directory),
                message = stringResource(Res.string.settings_picker_unavailable),
                onDismiss = { showDirectoryDialog = false },
            )
        }

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
    var showTrackingDialog by remember { mutableStateOf(false) }
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader(stringResource(Res.string.settings_services))
        SettingsEmptyState(stringResource(Res.string.settings_no_tracking_services_connected_sign_in_to_sync_reading))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_tracking_service),
            currentValue = stringResource(Res.string.settings_not_connected),
            onClick = { showTrackingDialog = true },
        )
        if (showTrackingDialog) {
            SettingsMessageDialog(
                title = stringResource(Res.string.settings_tracking_service),
                message = stringResource(Res.string.settings_no_tracking_services_connected_sign_in_to_sync_reading),
                onDismiss = { showTrackingDialog = false },
            )
        }

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
    var showIntervalDialog by remember { mutableStateOf(false) }
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
            currentValue = settingOptionLabel(prefs.syncInterval),
            enabled = prefs.syncEnabled,
            onClick = { showIntervalDialog = true },
        )
        if (showIntervalDialog) {
            SettingsChoiceDialog(
                title = stringResource(Res.string.settings_sync_interval),
                options = listOf(
                    stringResource(Res.string.settings_off) to "Off",
                    stringResource(Res.string.settings_hourly) to "Hourly",
                    stringResource(Res.string.settings_daily) to "Daily",
                    stringResource(Res.string.settings_weekly) to "Weekly",
                    stringResource(Res.string.settings_monthly) to "Monthly",
                ),
                selectedValue = prefs.syncInterval,
                onSelect = { value ->
                    session.update { it.copy(syncInterval = value) }
                    showIntervalDialog = false
                },
                onDismiss = { showIntervalDialog = false },
            )
        }
    }
}

@Composable
internal fun BrowseSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    var showLanguageDialog by remember { mutableStateOf(false) }
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
            currentValue = settingOptionLabel(prefs.defaultLanguage),
            onClick = { showLanguageDialog = true },
        )
        if (showLanguageDialog) {
            SettingsChoiceDialog(
                title = stringResource(Res.string.settings_default_language),
                options = listOf(
                    stringResource(Res.string.settings_all) to "All",
                    stringResource(Res.string.settings_chinese) to "Chinese",
                    stringResource(Res.string.settings_english) to "English",
                    stringResource(Res.string.settings_japanese) to "Japanese",
                ),
                selectedValue = prefs.defaultLanguage,
                onSelect = { value ->
                    session.update { it.copy(defaultLanguage = value) }
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false },
            )
        }
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
    val libraryRepository = app.koyura.features.library.LocalLibraryRepository.current
    var showRestoreDialog by remember { mutableStateOf(false) }
    // 点一次「存储用量」就重新读一次快照；文本在 Composable 上下文里格式化（协程内不能调 stringResource）。
    var usageRefresh by remember { mutableStateOf(0) }
    val storageSnapshot by androidx.compose.runtime.produceState<app.koyura.features.library.LibrarySnapshot?>(
        initialValue = null,
        libraryRepository,
        usageRefresh,
    ) {
        value = runCatching { libraryRepository?.load() }.getOrNull()
    }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearResult by remember { mutableStateOf(false) }
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
            onClick = { showRestoreDialog = true },
        )
        if (showRestoreDialog) {
            SettingsMessageDialog(
                title = stringResource(Res.string.settings_restore_backup),
                message = stringResource(Res.string.settings_backup_unavailable),
                onDismiss = { showRestoreDialog = false },
            )
        }

        SettingsSectionHeader(stringResource(Res.string.settings_storage))
        SettingsChoiceRow(
            title = stringResource(Res.string.settings_storage_usage),
            subtitle = stringResource(Res.string.settings_tap_to_recalculate_cache_size),
            currentValue = storageSnapshot?.let {
                stringResource(
                    Res.string.settings_storage_usage_format,
                    it.entries.size,
                    it.categories.size,
                )
            } ?: stringResource(Res.string.settings_not_calculated),
            onClick = { usageRefresh++ },
        )
        SettingsRow(
            title = stringResource(Res.string.settings_clear_cache),
            subtitle = stringResource(Res.string.settings_removes_downloaded_images_and_temporary_files),
            danger = true,
            onClick = { showClearDialog = true },
        )
        if (showClearDialog) {
            SettingsMessageDialog(
                title = stringResource(Res.string.settings_clear_cache),
                message = stringResource(Res.string.settings_cache_noop),
                onDismiss = {
                    showClearDialog = false
                    showClearResult = true
                },
            )
        }
        if (showClearResult) {
            SettingsMessageDialog(
                title = stringResource(Res.string.settings_clear_cache),
                message = stringResource(Res.string.settings_cache_cleared),
                onDismiss = { showClearResult = false },
            )
        }
    }
}

@Composable
internal fun AdvancedSettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
) {
    val prefs = session.uiState.preferences
    var showProxyDialog by remember { mutableStateOf(false) }
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
            currentValue = settingOptionLabel(prefs.proxy),
            onClick = { showProxyDialog = true },
        )
        if (showProxyDialog) {
            SettingsChoiceDialog(
                title = stringResource(Res.string.settings_proxy),
                options = listOf(
                    stringResource(Res.string.settings_not_configured) to "Not configured",
                    stringResource(Res.string.settings_http) to "HTTP",
                    stringResource(Res.string.settings_socks5) to "SOCKS5",
                ),
                selectedValue = prefs.proxy,
                onSelect = { value ->
                    session.update { it.copy(proxy = value) }
                    showProxyDialog = false
                },
                onDismiss = { showProxyDialog = false },
            )
        }
    }
}

@Composable
internal fun AboutSettingsScreen(
    modifier: Modifier = Modifier,
    onOpenOnboarding: (() -> Unit)? = null,
) {
    var showLicenses by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
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
        SettingsRow(
            title = stringResource(Res.string.settings_licenses),
            onClick = { showLicenses = true },
        )
        SettingsRow(
            title = stringResource(Res.string.settings_help),
            onClick = { showHelp = true },
        )
        if (onOpenOnboarding != null) {
            SettingsRow(
                title = stringResource(Res.string.settings_show_onboarding),
                onClick = onOpenOnboarding,
            )
        }
        if (showLicenses) {
            SettingsMessageDialog(
                title = stringResource(Res.string.settings_licenses),
                message = stringResource(Res.string.settings_licenses_body),
                onDismiss = { showLicenses = false },
            )
        }
        if (showHelp) {
            SettingsMessageDialog(
                title = stringResource(Res.string.settings_help),
                message = stringResource(Res.string.settings_help_body),
                onDismiss = { showHelp = false },
            )
        }
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
