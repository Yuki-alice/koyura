package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.koyura.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val environment = extensionEnvironment("ios")
    val browser = remember { IosWebLoginBrowser() }
    ExtensionApp { openExtensionSession(environment = environment, webLoginBrowser = browser) }
}
