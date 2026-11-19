package app.koyura.features.reader.comic.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.koyura.domain.reader.preferences.ReaderBackground
import app.koyura.domain.reader.preferences.ReaderFit
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.OverrideField
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.WorkReaderOverridePatch
import app.koyura.features.reader.comic.viewer.ComicTapZoneMode
import app.koyura.features.resources.Res
import app.koyura.features.resources.reader_chrome_background
import app.koyura.features.resources.reader_chrome_background_automatic
import app.koyura.features.resources.reader_chrome_background_black
import app.koyura.features.resources.reader_chrome_background_gray
import app.koyura.features.resources.reader_chrome_background_white
import app.koyura.features.resources.reader_chrome_cancel
import app.koyura.features.resources.reader_chrome_crop_borders
import app.koyura.features.resources.reader_chrome_double_tap_zoom
import app.koyura.features.resources.reader_chrome_dual_page_reversed
import app.koyura.features.resources.reader_chrome_first_page_single
import app.koyura.features.resources.reader_chrome_fit
import app.koyura.features.resources.reader_chrome_global_title
import app.koyura.features.resources.reader_chrome_inherit
import app.koyura.features.resources.reader_chrome_layout
import app.koyura.features.resources.reader_chrome_mode
import app.koyura.features.resources.reader_chrome_off
import app.koyura.features.resources.reader_chrome_on
import app.koyura.features.resources.reader_chrome_override_title
import app.koyura.features.resources.reader_chrome_reset_override
import app.koyura.features.resources.reader_chrome_settings
import app.koyura.features.resources.reader_chrome_tap_zone
import app.koyura.features.resources.reader_chrome_tap_zone_block
import app.koyura.features.resources.reader_chrome_tap_zone_disabled
import app.koyura.features.resources.reader_chrome_tap_zone_edge
import app.koyura.features.resources.reader_chrome_tap_zone_invert
import app.koyura.features.resources.reader_chrome_tap_zone_kindle
import app.koyura.features.resources.reader_chrome_tap_zone_l_shape
import app.koyura.features.resources.reader_chrome_tap_zone_left_right
import app.koyura.features.resources.reader_chrome_tap_zone_title
import org.jetbrains.compose.resources.stringResource

/** Test hooks for the settings dialog. */
const val ReaderChromeSettingsDialogTag = "reader-chrome-dialog-settings"
const val ReaderChromeOverrideResetTag = "reader-chrome-override-reset"
fun readerChromeGlobalOptionTag(field: String, value: String) = "reader-chrome-global-$field-$value"
fun readerChromeOverrideOptionTag(field: String, value: String) = "reader-chrome-override-$field-$value"

/**
 * Settings sheet with two sections: global defaults (always concrete) and the per-work override
 * (every row offers DEFAULT = inherit). Emits [ReaderChromeAction.ChangeGlobalSettings] with a
 * full replacement and [ReaderChromeAction.ChangeWorkOverride] with field patches; the host
 * persists both. [ReaderChromeAction.ResetWorkOverride] drops the override.
 */
@Composable
fun ReaderSettingsDialog(
    state: ReaderChromeState,
    onAction: (ReaderChromeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .testTag(ReaderChromeSettingsDialogTag)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.reader_chrome_settings),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.reader_chrome_global_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GlobalSection(
                global = state.global,
                onChange = { onAction(ReaderChromeAction.ChangeGlobalSettings(it)) },
            )
            Text(
                text = stringResource(Res.string.reader_chrome_override_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OverrideSection(
                global = state.global,
                override = state.override,
                onPatch = { onAction(ReaderChromeAction.ChangeWorkOverride(it)) },
            )
            Text(
                text = stringResource(Res.string.reader_chrome_tap_zone_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // 点击区契约尚未进 ReaderSettings（domain 为禁改区），由宿主持有并按次会话生效。
            EnumRow(
                label = stringResource(Res.string.reader_chrome_tap_zone),
                options = ComicTapZoneMode.entries.map { tapZoneLabel(it) to it.id },
                selected = state.tapZone.id,
                tag = { readerChromeGlobalOptionTag("tapZone", it) },
                onSelect = { onAction(ReaderChromeAction.SetTapZone(ComicTapZoneMode.fromId(it))) },
            )
            SwitchRow(
                label = stringResource(Res.string.reader_chrome_tap_zone_invert),
                checked = state.invertTapZone,
                tag = readerChromeGlobalOptionTag("tapZoneInvert", state.invertTapZone.toString()),
                onToggle = { onAction(ReaderChromeAction.SetInvertTapZone(it)) },
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.override != null) {
                    TextButton(
                        onClick = {
                            onAction(ReaderChromeAction.ResetWorkOverride)
                        },
                        modifier = Modifier.testTag(ReaderChromeOverrideResetTag),
                    ) {
                        Text(stringResource(Res.string.reader_chrome_reset_override))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.reader_chrome_cancel))
                }
            }
        }
    }
}

@Composable
private fun GlobalSection(
    global: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    EnumRow(
        label = stringResource(Res.string.reader_chrome_mode),
        options = ReadingMode.entries.map { modeLabel(it) to it.name },
        selected = global.mode.name,
        tag = { readerChromeGlobalOptionTag("mode", it) },
        onSelect = { onChange(global.copy(mode = ReadingMode.valueOf(it))) },
    )
    EnumRow(
        label = stringResource(Res.string.reader_chrome_layout),
        options = ReaderLayout.entries.map { layoutLabel(it) to it.name },
        selected = global.layout.name,
        tag = { readerChromeGlobalOptionTag("layout", it) },
        onSelect = { onChange(global.copy(layout = ReaderLayout.valueOf(it))) },
    )
    EnumRow(
        label = stringResource(Res.string.reader_chrome_fit),
        options = ReaderFit.entries.map { fitLabel(it) to it.name },
        selected = global.fit.name,
        tag = { readerChromeGlobalOptionTag("fit", it) },
        onSelect = { onChange(global.copy(fit = ReaderFit.valueOf(it))) },
    )
    EnumRow(
        label = stringResource(Res.string.reader_chrome_background),
        options = ReaderBackground.entries.map { backgroundLabel(it) to it.name },
        selected = global.background.name,
        tag = { readerChromeGlobalOptionTag("background", it) },
        onSelect = { onChange(global.copy(background = ReaderBackground.valueOf(it))) },
    )
    SwitchRow(
        label = stringResource(Res.string.reader_chrome_first_page_single),
        checked = global.firstPageSingle,
        tag = readerChromeGlobalOptionTag("firstPageSingle", global.firstPageSingle.toString()),
        onToggle = { onChange(global.copy(firstPageSingle = it)) },
    )
    SwitchRow(
        label = stringResource(Res.string.reader_chrome_dual_page_reversed),
        checked = global.dualPageReversed,
        tag = readerChromeGlobalOptionTag("dualPageReversed", global.dualPageReversed.toString()),
        onToggle = { onChange(global.copy(dualPageReversed = it)) },
    )
    SwitchRow(
        label = stringResource(Res.string.reader_chrome_crop_borders),
        checked = global.cropBorders,
        tag = readerChromeGlobalOptionTag("cropBorders", global.cropBorders.toString()),
        onToggle = { onChange(global.copy(cropBorders = it)) },
    )
    SwitchRow(
        label = stringResource(Res.string.reader_chrome_double_tap_zoom),
        checked = global.zoomOnDoubleTap,
        tag = readerChromeGlobalOptionTag("doubleTapZoom", global.zoomOnDoubleTap.toString()),
        onToggle = { onChange(global.copy(zoomOnDoubleTap = it)) },
    )
}

@Composable
private fun OverrideSection(
    global: ReaderSettings,
    override: WorkReaderOverride?,
    onPatch: (WorkReaderOverridePatch) -> Unit,
) {
    val current = override ?: WorkReaderOverride()
    InheritEnumRow(
        label = stringResource(Res.string.reader_chrome_mode),
        options = ReadingMode.entries.map { modeLabel(it) to it.name },
        selected = current.mode?.name,
        inheritLabel = "${stringResource(Res.string.reader_chrome_inherit)} (${modeLabel(global.mode)})",
        tag = { readerChromeOverrideOptionTag("mode", it) },
        onSelect = { onPatch(WorkReaderOverridePatch(mode = it?.let { OverrideField.Set(ReadingMode.valueOf(it)) } ?: OverrideField.Clear)) },
    )
    InheritEnumRow(
        label = stringResource(Res.string.reader_chrome_layout),
        options = ReaderLayout.entries.map { layoutLabel(it) to it.name },
        selected = current.layout?.name,
        inheritLabel = "${stringResource(Res.string.reader_chrome_inherit)} (${layoutLabel(global.layout)})",
        tag = { readerChromeOverrideOptionTag("layout", it) },
        onSelect = { onPatch(WorkReaderOverridePatch(layout = it?.let { OverrideField.Set(ReaderLayout.valueOf(it)) } ?: OverrideField.Clear)) },
    )
    InheritEnumRow(
        label = stringResource(Res.string.reader_chrome_fit),
        options = ReaderFit.entries.map { fitLabel(it) to it.name },
        selected = current.fit?.name,
        inheritLabel = "${stringResource(Res.string.reader_chrome_inherit)} (${fitLabel(global.fit)})",
        tag = { readerChromeOverrideOptionTag("fit", it) },
        onSelect = { onPatch(WorkReaderOverridePatch(fit = it?.let { OverrideField.Set(ReaderFit.valueOf(it)) } ?: OverrideField.Clear)) },
    )
    InheritEnumRow(
        label = stringResource(Res.string.reader_chrome_background),
        options = ReaderBackground.entries.map { backgroundLabel(it) to it.name },
        selected = current.background?.name,
        inheritLabel = "${stringResource(Res.string.reader_chrome_inherit)} (${backgroundLabel(global.background)})",
        tag = { readerChromeOverrideOptionTag("background", it) },
        onSelect = {
            onPatch(
                WorkReaderOverridePatch(
                    background = it?.let { name -> OverrideField.Set(ReaderBackground.valueOf(name)) }
                        ?: OverrideField.Clear,
                ),
            )
        },
    )
    InheritBoolRow(
        label = stringResource(Res.string.reader_chrome_first_page_single),
        selected = current.firstPageSingle,
        global = global.firstPageSingle,
        tag = { readerChromeOverrideOptionTag("firstPageSingle", it) },
        onSelect = { onPatch(WorkReaderOverridePatch(firstPageSingle = it?.let { OverrideField.Set(it) } ?: OverrideField.Clear)) },
    )
    InheritBoolRow(
        label = stringResource(Res.string.reader_chrome_dual_page_reversed),
        selected = current.dualPageReversed,
        global = global.dualPageReversed,
        tag = { readerChromeOverrideOptionTag("dualPageReversed", it) },
        onSelect = { onPatch(WorkReaderOverridePatch(dualPageReversed = it?.let { OverrideField.Set(it) } ?: OverrideField.Clear)) },
    )
    InheritBoolRow(
        label = stringResource(Res.string.reader_chrome_crop_borders),
        selected = current.cropBorders,
        global = global.cropBorders,
        tag = { readerChromeOverrideOptionTag("cropBorders", it) },
        onSelect = { onPatch(WorkReaderOverridePatch(cropBorders = it?.let { OverrideField.Set(it) } ?: OverrideField.Clear)) },
    )
    InheritBoolRow(
        label = stringResource(Res.string.reader_chrome_double_tap_zoom),
        selected = current.zoomOnDoubleTap,
        global = global.zoomOnDoubleTap,
        tag = { readerChromeOverrideOptionTag("doubleTapZoom", it) },
        onSelect = { onPatch(WorkReaderOverridePatch(zoomOnDoubleTap = it?.let { OverrideField.Set(it) } ?: OverrideField.Clear)) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    tag: (String) -> String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (text, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                    modifier = Modifier.testTag(tag(value)),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InheritEnumRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    inheritLabel: String,
    tag: (String) -> String,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(inheritLabel) },
                modifier = Modifier.testTag(tag("DEFAULT")),
            )
            options.forEach { (text, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                    modifier = Modifier.testTag(tag(value)),
                )
            }
        }
    }
}

@Composable
private fun InheritBoolRow(
    label: String,
    selected: Boolean?,
    global: Boolean,
    tag: (String) -> String,
    onSelect: (Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("${stringResource(Res.string.reader_chrome_inherit)} ($global)") },
                modifier = Modifier.testTag(tag("DEFAULT")),
            )
            FilterChip(
                selected = selected == true,
                onClick = { onSelect(true) },
                label = { Text(stringResource(Res.string.reader_chrome_on)) },
                modifier = Modifier.testTag(tag("true")),
            )
            FilterChip(
                selected = selected == false,
                onClick = { onSelect(false) },
                label = { Text(stringResource(Res.string.reader_chrome_off)) },
                modifier = Modifier.testTag(tag("false")),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    tag: String,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun backgroundLabel(background: ReaderBackground): String = stringResource(
    when (background) {
        ReaderBackground.AUTOMATIC -> Res.string.reader_chrome_background_automatic
        ReaderBackground.BLACK -> Res.string.reader_chrome_background_black
        ReaderBackground.GRAY -> Res.string.reader_chrome_background_gray
        ReaderBackground.WHITE -> Res.string.reader_chrome_background_white
    },
)

@Composable
private fun tapZoneLabel(zone: ComicTapZoneMode): String = stringResource(
    when (zone) {
        ComicTapZoneMode.DEFAULT -> Res.string.reader_chrome_tap_zone_block
        ComicTapZoneMode.LEFT_RIGHT -> Res.string.reader_chrome_tap_zone_left_right
        ComicTapZoneMode.KINDLE -> Res.string.reader_chrome_tap_zone_kindle
        ComicTapZoneMode.EDGE -> Res.string.reader_chrome_tap_zone_edge
        ComicTapZoneMode.L_SHAPE -> Res.string.reader_chrome_tap_zone_l_shape
        ComicTapZoneMode.DISABLED -> Res.string.reader_chrome_tap_zone_disabled
    },
)
