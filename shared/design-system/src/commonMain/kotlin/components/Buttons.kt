package app.koyura.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import app.koyura.designsystem.ActionTooltip
import app.koyura.designsystem.DesignTokens

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    contentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        border = border,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        content = content,
    )
}

@Composable
fun AppFilledIconButton(
    onClick: () -> Unit,
    label: String,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    visualSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ActionTooltip(label) {
        IconButton(
            onClick = onClick,
            modifier = modifier.sizeIn(
                minWidth = DesignTokens.Sizes.MinimumTouchTarget,
                minHeight = DesignTokens.Sizes.MinimumTouchTarget,
            ),
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
        ) {
            Box(
                modifier = Modifier.size(visualSize).background(containerColor, shape),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
fun AppExtendedFab(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        SmallExtendedFloatingActionButton(text = text, icon = icon, onClick = onClick, modifier = modifier)
    } else {
        ExtendedFloatingActionButton(text = text, icon = icon, onClick = onClick, modifier = modifier)
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier)
}

@Composable
fun AppSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(onClick = onClick, label = label, modifier = modifier)
}
