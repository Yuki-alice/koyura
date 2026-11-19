package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.koyura.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val context = LocalContext.current
    val browser = remember(context) { androidWebLoginBrowser(context) }
    val environment = extensionEnvironment("android")
    ExtensionApp { context.applicationContext.openExtensionSession(environment, browser) }
}
