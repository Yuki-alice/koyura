package app.koyura.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.koyura.app.resources.Res
import app.koyura.app.resources.status_downloaded_only
import app.koyura.app.resources.status_incognito
import app.koyura.app.resources.status_indexing
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppStatusBanners(indexing: Boolean, downloadedOnly: Boolean, incognito: Boolean) {
    val scheme = UiTheme.colorScheme
    if (indexing) {
        StatusBanner(
            tag = "status-indexing",
            label = stringResource(Res.string.status_indexing),
            container = scheme.secondary,
            content = scheme.onSecondary,
            inProgress = true,
        )
    }
    if (downloadedOnly) {
        StatusBanner(
            tag = "status-downloaded-only",
            label = stringResource(Res.string.status_downloaded_only),
            container = scheme.tertiary,
            content = scheme.onTertiary,
        )
    }
    if (incognito) {
        StatusBanner(
            tag = "status-incognito",
            label = stringResource(Res.string.status_incognito),
            container = scheme.primary,
            content = scheme.onPrimary,
        )
    }
}

@Composable
private fun StatusBanner(
    tag: String,
    label: String,
    container: Color,
    content: Color,
    inProgress: Boolean = false,
) {
    val style = UiTheme.typography.labelMedium
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(container)
            .testTag(tag)
            .padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = if (inProgress) DesignTokens.Spacing.Small else DesignTokens.Spacing.ExtraSmall,
            ),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inProgress) {
            val size = with(LocalDensity.current) { style.lineHeight.toDp() }
            AppCircularProgress(Modifier.size(size), strokeWidth = size / 8, color = content)
        }
        AppText(label, style = style, color = content)
    }
}
