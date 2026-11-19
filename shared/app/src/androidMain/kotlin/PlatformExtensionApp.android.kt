package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.koyura.app.sourceui.AndroidClipboard
import app.koyura.app.sourceui.AndroidUrlOpener
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val context = LocalContext.current
    val browser = remember(context) { androidWebLoginBrowser(context) }
    val environment = extensionEnvironment("android")
    val uiHost = remember(context) {
        SourceUiHost(AndroidClipboard(context), AndroidUrlOpener(context))
    }
    ExtensionApp(uiHost) { host ->
        context.applicationContext.openExtensionSession(environment, browser, sourceUi = host)
    }
}
