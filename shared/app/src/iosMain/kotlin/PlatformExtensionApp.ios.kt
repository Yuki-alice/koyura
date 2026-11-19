package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.koyura.app.sourceui.IosClipboard
import app.koyura.app.sourceui.IosUrlOpener
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val environment = extensionEnvironment("ios")
    val browser = remember { IosWebLoginBrowser() }
    val uiHost = remember { SourceUiHost(IosClipboard(), IosUrlOpener()) }
    ExtensionApp(uiHost) { host ->
        openExtensionSession(environment = environment, webLoginBrowser = browser, sourceUi = host)
    }
}
