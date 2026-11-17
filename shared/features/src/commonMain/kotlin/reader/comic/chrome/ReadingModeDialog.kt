package app.koyura.features.reader.comic.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Reading-mode picker mirroring Komikku's `ReadingModeSelectDialog`: an adaptive grid of mode
 * cells with icons, an Apply button, and a use-default row when [allowDefault] is set.
 * Selecting only previews; [onApply] fires once with the pick (`null` = follow global).
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
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.testTag(ReaderChromeModeDialogTag),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.reader_chrome_reading_mode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                if (allowDefault) {
                    ModeDefaultRow(
                        selected = selected == null,
                        onSelect = { selected = null },
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ReadingMode.entries, key = { it.name }) { mode ->
                        ModeCell(
                            mode = mode,
                            selected = selected == mode,
                            onSelect = { selected = mode },
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
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
}

@Composable
private fun ModeDefaultRow(
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ReaderChromeModeDefaultTag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(Res.string.reader_chrome_inherit),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun ModeCell(
    mode: ReadingMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier
            .testTag(readerChromeModeOptionTag(mode.name))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
        ) {
            ModeGlyph(mode = mode, tint = MaterialTheme.colorScheme.onSurface)
            Text(
                text = modeLabel(mode),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
            )
        }
    }
}

/**
 * Hand-drawn mode glyphs (no icon dependency): the reading direction as pages plus a flow
 * arrow. Content description comes from the adjacent label for accessibility.
 */
@Composable
private fun ModeGlyph(mode: ReadingMode, tint: Color) {
    Canvas(Modifier.size(width = 44.dp, height = 32.dp)) {
        when (mode) {
            ReadingMode.LEFT_TO_RIGHT -> {
                pagePair(tint)
                flowArrow(fromLeft = true, tint = tint)
            }
            ReadingMode.RIGHT_TO_LEFT -> {
                pagePair(tint)
                flowArrow(fromLeft = false, tint = tint)
            }
            ReadingMode.VERTICAL -> {
                pageStack(vertical = true, tint = tint)
                flowArrowDown(tint = tint)
            }
            ReadingMode.WEBTOON -> pageStrip(tint = tint)
            ReadingMode.CONTINUOUS_VERTICAL -> pageStack(vertical = false, tint = tint)
        }
    }
}

private fun DrawScope.pagePair(tint: Color) {
    val w = size.width
    val h = size.height
    drawRect(tint, topLeft = Offset(w * 0.08f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.7f), style = Stroke(width = 3f))
    drawRect(tint, topLeft = Offset(w * 0.56f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.7f), style = Stroke(width = 3f))
}

private fun DrawScope.flowArrow(fromLeft: Boolean, tint: Color) {
    val w = size.width
    val h = size.height
    val y = h * 0.5f
    val x0 = if (fromLeft) w * 0.18f else w * 0.82f
    val x1 = if (fromLeft) w * 0.82f else w * 0.18f
    drawLine(tint, Offset(x0, y), Offset(x1, y), strokeWidth = 4f)
    val dir = if (fromLeft) 1f else -1f
    drawLine(tint, Offset(x1, y), Offset(x1 - dir * 10f, y - 8f), strokeWidth = 4f)
    drawLine(tint, Offset(x1, y), Offset(x1 - dir * 10f, y + 8f), strokeWidth = 4f)
}

private fun DrawScope.flowArrowDown(tint: Color) {
    val w = size.width
    val x = w * 0.8f
    drawLine(tint, Offset(x, size.height * 0.1f), Offset(x, size.height * 0.9f), strokeWidth = 4f)
    drawLine(tint, Offset(x, size.height * 0.9f), Offset(x - 8f, size.height * 0.9f - 10f), strokeWidth = 4f)
    drawLine(tint, Offset(x, size.height * 0.9f), Offset(x + 8f, size.height * 0.9f - 10f), strokeWidth = 4f)
}

private fun DrawScope.pageStack(vertical: Boolean, tint: Color) {
    val w = size.width
    val h = size.height
    if (vertical) {
        drawRect(tint, topLeft = Offset(w * 0.1f, h * 0.08f), size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.36f), style = Stroke(width = 3f))
        drawRect(tint, topLeft = Offset(w * 0.1f, h * 0.56f), size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.36f), style = Stroke(width = 3f))
    } else {
        drawRect(tint, topLeft = Offset(w * 0.08f, h * 0.1f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.24f), style = Stroke(width = 3f))
        drawRect(tint, topLeft = Offset(w * 0.08f, h * 0.4f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.24f), style = Stroke(width = 3f))
        drawRect(tint, topLeft = Offset(w * 0.08f, h * 0.7f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.24f), style = Stroke(width = 3f))
    }
}

private fun DrawScope.pageStrip(tint: Color) {
    val w = size.width
    val h = size.height
    drawRect(tint, topLeft = Offset(w * 0.25f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.5f, h), style = Stroke(width = 3f))
    drawLine(tint, Offset(w * 0.25f, h * 0.33f), Offset(w * 0.75f, h * 0.33f), strokeWidth = 2f)
    drawLine(tint, Offset(w * 0.25f, h * 0.66f), Offset(w * 0.75f, h * 0.66f), strokeWidth = 2f)
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
