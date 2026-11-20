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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens

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
                    label = "Back",
                    onClick = { onBack?.invoke() },
                )
            }
            Text(
                text = subpage.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = DesignTokens.Spacing.Small),
            )
        }
        HorizontalDivider()
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
        SettingsSectionHeader("Categories")
        SettingsSwitchRow(
            title = "Show categories",
            subtitle = "Organize the library with categories",
            checked = prefs.showCategories,
            onCheckedChange = { value -> session.update { it.copy(showCategories = value) } },
        )
        SettingsChoiceRow(
            title = "Default category",
            currentValue = "All",
            enabled = prefs.showCategories,
            onClick = {},
        )

        SettingsSectionHeader("Updates")
        SettingsSwitchRow(
            title = "Global update",
            subtitle = "Check every library entry in one pass",
            checked = prefs.globalUpdate,
            onCheckedChange = { value -> session.update { it.copy(globalUpdate = value) } },
        )
        SettingsSwitchRow(
            title = "Update restrictions",
            subtitle = "Limit updates by network and schedule",
            checked = prefs.updateRestrictions,
            onCheckedChange = { value -> session.update { it.copy(updateRestrictions = value) } },
        )

        SettingsSectionHeader("Display")
        SettingsChoiceRow(
            title = "Library columns",
            currentValue = prefs.libraryColumns.toString(),
            onClick = {
                val next = if (prefs.libraryColumns >= 4) 1 else prefs.libraryColumns + 1
                session.update { it.copy(libraryColumns = next) }
            },
        )
        SettingsSwitchRow(
            title = "Sort by title",
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
        SettingsSectionHeader("Reading")
        SettingsChoiceRow(
            title = "Reading mode",
            currentValue = prefs.readerMode,
            onClick = {
                val next = readerModes[(readerModes.indexOf(prefs.readerMode) + 1) % readerModes.size]
                session.update { it.copy(readerMode = next) }
            },
        )
        SettingsChoiceRow(
            title = "Reading direction",
            currentValue = prefs.readingDirection,
            onClick = {
                val next = directions[(directions.indexOf(prefs.readingDirection) + 1) % directions.size]
                session.update { it.copy(readingDirection = next) }
            },
        )
        SettingsSwitchRow(
            title = "Crop borders",
            subtitle = "Trim page edges before display",
            checked = prefs.cropBorders,
            onCheckedChange = { value -> session.update { it.copy(cropBorders = value) } },
        )
        SettingsSwitchRow(
            title = "Auto scroll",
            checked = prefs.autoScroll,
            onCheckedChange = { value -> session.update { it.copy(autoScroll = value) } },
        )

        SettingsSectionHeader("Display")
        SettingsSliderRow(
            title = "Screen brightness",
            value = prefs.screenBrightness,
            valueText = "${(prefs.screenBrightness * 100).toInt()}%",
            valueRange = 0.1f..1f,
            onValueChange = { value -> session.update { it.copy(screenBrightness = value) } },
        )
        SettingsChoiceRow(
            title = "Background color",
            currentValue = "Follow theme",
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
        SettingsSectionHeader("Storage")
        SettingsChoiceRow(
            title = "Download directory",
            subtitle = "Where downloaded chapters are stored",
            currentValue = prefs.downloadDirectory,
            onClick = {},
        )

        SettingsSectionHeader("Queue")
        SettingsSliderRow(
            title = "Parallel downloads",
            value = prefs.parallelDownloads.toFloat(),
            valueText = prefs.parallelDownloads.toString(),
            valueRange = 1f..8f,
            steps = 7,
            onValueChange = { value -> session.update { it.copy(parallelDownloads = value.toInt()) } },
        )
        SettingsSwitchRow(
            title = "Download only over Wi-Fi",
            checked = prefs.wifiOnly,
            onCheckedChange = { value -> session.update { it.copy(wifiOnly = value) } },
        )
        SettingsSwitchRow(
            title = "Auto-download new chapters",
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
        SettingsSectionHeader("Services")
        SettingsEmptyState("No tracking services connected. Sign in to sync reading progress across devices.")
        SettingsChoiceRow(
            title = "Tracking service",
            currentValue = "Not connected",
            onClick = {},
        )

        SettingsSectionHeader("Sync")
        SettingsSwitchRow(
            title = "Update tracking automatically",
            subtitle = "Push progress when a chapter is marked read",
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
        SettingsSectionHeader("Remote")
        SettingsEmptyState("No remote services configured yet. Connect a service to sync your library.")
        SettingsSwitchRow(
            title = "Enable sync",
            checked = prefs.syncEnabled,
            onCheckedChange = { value -> session.update { it.copy(syncEnabled = value) } },
        )
        SettingsChoiceRow(
            title = "Sync interval",
            currentValue = "Daily",
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
        SettingsSectionHeader("Search")
        SettingsSwitchRow(
            title = "Multi-source search",
            subtitle = "Search all installed sources at once",
            checked = prefs.multiSourceSearch,
            onCheckedChange = { value -> session.update { it.copy(multiSourceSearch = value) } },
        )
        SettingsChoiceRow(
            title = "Default language",
            currentValue = "All",
            onClick = {},
        )
        SettingsSwitchRow(
            title = "Show adult sources",
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
        SettingsSectionHeader("Backup")
        SettingsChoiceRow(
            title = "Backup frequency",
            currentValue = prefs.backupFrequency,
            onClick = {
                val next = frequencies[(frequencies.indexOf(prefs.backupFrequency) + 1) % frequencies.size]
                session.update { it.copy(backupFrequency = next) }
            },
        )
        SettingsChoiceRow(
            title = "Restore backup",
            currentValue = "Choose file",
            onClick = {},
        )

        SettingsSectionHeader("Storage")
        SettingsChoiceRow(
            title = "Storage usage",
            subtitle = "Tap to recalculate cache size",
            currentValue = "Not calculated",
            onClick = {},
        )
        SettingsRow(
            title = "Clear cache",
            subtitle = "Removes downloaded images and temporary files",
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
        SettingsSectionHeader("Debug")
        SettingsSwitchRow(
            title = "Enable debug mode",
            checked = prefs.debugEnabled,
            onCheckedChange = { value -> session.update { it.copy(debugEnabled = value) } },
        )
        SettingsChoiceRow(
            title = "Log level",
            currentValue = prefs.logLevel,
            onClick = {
                val next = logLevels[(logLevels.indexOf(prefs.logLevel) + 1) % logLevels.size]
                session.update { it.copy(logLevel = next) }
            },
        )

        SettingsSectionHeader("Updates")
        SettingsChoiceRow(
            title = "Update channel",
            currentValue = prefs.updateChannel,
            onClick = {
                val next = channels[(channels.indexOf(prefs.updateChannel) + 1) % channels.size]
                session.update { it.copy(updateChannel = next) }
            },
        )
        SettingsChoiceRow(
            title = "Proxy",
            currentValue = "Not configured",
            onClick = {},
        )
    }
}

@Composable
internal fun AboutSettingsScreen(
    modifier: Modifier = Modifier,
) {
    SettingsSubpageScroll(modifier) {
        SettingsSectionHeader("App")
        SettingsRow(
            title = "Version",
            subtitle = "1.0.0 · settings preview",
        )
        SettingsRow(
            title = "Build",
            subtitle = "Local development build",
        )

        SettingsSectionHeader("Links")
        SettingsRow(title = "Licenses", onClick = {})
        SettingsRow(title = "Help", onClick = {})
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
