package app.koyura.designsystem.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun AppCircularProgress(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    color: Color = UiTheme.colorScheme.primary,
) {
    CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth, color = color)
}

@Composable
fun AppLinearProgress(modifier: Modifier = Modifier, progress: (() -> Float)? = null) {
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier)
    } else {
        LinearProgressIndicator(progress = progress, modifier = modifier)
    }
}
