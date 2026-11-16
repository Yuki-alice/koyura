package app.koyura.app

import androidx.compose.runtime.Composable
import app.koyura.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val environment = extensionEnvironment("ios")
    ExtensionApp { openExtensionSession(environment = environment) }
}
