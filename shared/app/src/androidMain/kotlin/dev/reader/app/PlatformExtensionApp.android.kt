package dev.reader.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.reader.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val context = LocalContext.current.applicationContext
    val environment = extensionEnvironment("android")
    ExtensionApp { context.openExtensionSession(environment) }
}
