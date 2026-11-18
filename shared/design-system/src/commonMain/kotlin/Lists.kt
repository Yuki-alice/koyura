package app.koyura.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Komikku `ListGroupHeader`: semibold bodyMedium in `onSurfaceVariant` inside a full-width
 * surface, used for source language groups, extension groups, history dates and chapter counts.
 */
@Composable
fun ListGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 0.dp,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = tonalElevation,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Komikku `BaseBrowseItem`: 40 dp leading icon, weighted content, trailing action in one
 * center-aligned row with 16 dp horizontal / 8 dp vertical padding.
 */
@Composable
fun BrowseRow(
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    icon: @Composable RowScope.() -> Unit = {},
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        content()
        action()
    }
}

/**
 * Leading icon placeholder for sources/extensions without packaged artwork: a 40 dp
 * `surfaceVariant` circle with the entry's initial, matching the size of Komikku's
 * `ExtensionIcon`/`SourceIcon` slot so rows keep the same rhythm.
 */
@Composable
fun IconPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
) {
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifEmpty { "?" }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Rectangular cover placeholder for history/detail surfaces without loaded artwork: a
 * `surfaceVariant` box with the work's initial, sized by the caller. Corners are always 8 dp
 * rounded so placeholders and loaded [Cover] art share one rhythm without layout shift.
 */
@Composable
fun CoverPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
) {
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifEmpty { "?" }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
