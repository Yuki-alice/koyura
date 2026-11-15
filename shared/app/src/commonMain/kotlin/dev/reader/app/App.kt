package dev.reader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.reader.app.resources.Res
import dev.reader.app.resources.app_name
import dev.reader.designsystem.ReaderTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun App() {
    ReaderTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.app_name))
            }
        }
    }
}
