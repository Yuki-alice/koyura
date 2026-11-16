package dev.reader.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import dev.reader.app.reader.VeneraReaderService
import dev.reader.app.browse.venera.VeneraBrowseService
import dev.reader.app.resources.Res
import dev.reader.app.resources.extension_startup_failed
import dev.reader.app.resources.extension_startup_loading
import dev.reader.app.resources.extension_startup_retry
import dev.reader.data.session.ExtensionSession
import dev.reader.designsystem.ReaderTheme
import dev.reader.source.runtime.VeneraEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal expect fun PlatformExtensionApp()

@Composable
internal fun extensionEnvironment(platform: String): VeneraEnvironment {
    val locale = LocalLocale.current
    val veneraLocale = if (locale.region.isBlank()) locale.language else "${locale.language}_${locale.region}"
    return VeneraEnvironment(
        compatibleAppVersion = "1.16.0",
        locale = veneraLocale,
        platform = platform,
    )
}

@Composable
internal fun ExtensionApp(openSession: suspend () -> ExtensionSession) {
    var openedSession by remember { mutableStateOf<ExtensionSession?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        var session: ExtensionSession? = null
        try {
            // Capture ownership on the opening dispatcher before a cancelled caller can lose it.
            withContext(Dispatchers.IO) { session = openSession() }
            openedSession = checkNotNull(session)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                session?.close()
            }
        }
    }

    val session = openedSession
    if (session != null) {
        val browse = remember(session) { VeneraBrowseService(session.service) }
        val reader = remember(session) { VeneraReaderService(session.comicReading) }
        App(browseService = browse, readerService = reader)
    } else {
        ReaderTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    if (failed) {
                        Text(stringResource(Res.string.extension_startup_failed))
                        Button(onClick = {
                            failed = false
                            attempt += 1
                        }) { Text(stringResource(Res.string.extension_startup_retry)) }
                    } else {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.extension_startup_loading))
                    }
                }
            }
        }
    }
}
