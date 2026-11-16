package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.koyura.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val context = LocalContext.current.applicationContext
    val environment = extensionEnvironment("android")
    ExtensionApp { context.openExtensionSession(environment) }
}
