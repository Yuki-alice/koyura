package app.koyura.features.reader.comic.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.koyura.domain.reader.preferences.ReaderFit
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.features.resources.Res
import app.koyura.features.resources.reader_chrome_apply
import app.koyura.features.resources.reader_chrome_cancel
import app.koyura.features.resources.reader_chrome_fit_height
import app.koyura.features.resources.reader_chrome_fit_original
import app.koyura.features.resources.reader_chrome_fit_screen
import app.koyura.features.resources.reader_chrome_fit_width
import app.koyura.features.resources.reader_chrome_inherit
import app.koyura.features.resources.reader_chrome_layout_auto
import app.koyura.features.resources.reader_chrome_layout_double
import app.koyura.features.resources.reader_chrome_layout_single
import app.koyura.features.resources.reader_chrome_mode
import app.koyura.features.resources.reader_chrome_mode_continuous
import app.koyura.features.resources.reader_chrome_mode_ltr
import app.koyura.features.resources.reader_chrome_mode_rtl
import app.koyura.features.resources.reader_chrome_mode_vertical
import app.koyura.features.resources.reader_chrome_mode_webtoon
import app.koyura.features.resources.reader_chrome_reading_mode
import org.jetbrains.compose.resources.stringResource

/** Test hooks for the reading-mode dialog. */
const val ReaderChromeModeDialogTag = "reader-chrome-dialog-mode"
const val ReaderChromeModeApplyTag = "reader-chrome-mode-apply"
const val ReaderChromeModeDefaultTag = "reader-chrome-mode-default"
fun readerChromeModeOptionTag(mode: String) = "reader-chrome-mode-option-$mode"
fun readerChromeLayoutOptionTag(layout: String) = "reader-chrome-layout-option-$layout"
fun readerChromeFitOptionTag(fit: String) = "reader-chrome-fit-option-$fit"

/**
 * Reading-mode picker. [current] null means DEFAULT (inherit the global value); the Default row
 * is only shown when [allowDefault]. Apply emits the pick; the host decides whether it lands on
 * the global defaults or the work override.
 */
@Composable
fun ReadingModeDialog(
    current: ReadingMode?,
    allowDefault: Boolean,
    onApply: (ReadingMode?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .testTag(ReaderChromeModeDialogTag)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.reader_chrome_reading_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (allowDefault) {
                OptionRow(
                    label = stringResource(Res.string.reader_chrome_inherit),
                    selected = selected == null,
                    tag = ReaderChromeModeDefaultTag,
                    onSelect = { selected = null },
                )
            }
            ReadingMode.entries.forEach { mode ->
                OptionRow(
                    label = modeLabel(mode),
                    selected = selected == mode,
                    tag = readerChromeModeOptionTag(mode.name),
                    onSelect = { selected = mode },
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.reader_chrome_cancel))
                }
                Button(
                    onClick = { onApply(selected) },
                    modifier = Modifier.testTag(ReaderChromeModeApplyTag),
                ) {
                    Text(stringResource(Res.string.reader_chrome_apply))
                }
            }
        }
    }
}

@Composable
internal fun modeLabel(mode: ReadingMode): String = stringResource(
    when (mode) {
        ReadingMode.LEFT_TO_RIGHT -> Res.string.reader_chrome_mode_ltr
        ReadingMode.RIGHT_TO_LEFT -> Res.string.reader_chrome_mode_rtl
        ReadingMode.VERTICAL -> Res.string.reader_chrome_mode_vertical
        ReadingMode.WEBTOON -> Res.string.reader_chrome_mode_webtoon
        ReadingMode.CONTINUOUS_VERTICAL -> Res.string.reader_chrome_mode_continuous
    },
)

@Composable
internal fun layoutLabel(layout: ReaderLayout): String = stringResource(
    when (layout) {
        ReaderLayout.SINGLE_PAGE -> Res.string.reader_chrome_layout_single
        ReaderLayout.DOUBLE_PAGES -> Res.string.reader_chrome_layout_double
        ReaderLayout.AUTOMATIC -> Res.string.reader_chrome_layout_auto
    },
)

@Composable
internal fun fitLabel(fit: ReaderFit): String = stringResource(
    when (fit) {
        ReaderFit.FIT -> Res.string.reader_chrome_fit_screen
        ReaderFit.FIT_WIDTH -> Res.string.reader_chrome_fit_width
        ReaderFit.FIT_HEIGHT -> Res.string.reader_chrome_fit_height
        ReaderFit.ORIGINAL -> Res.string.reader_chrome_fit_original
    },
)

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
