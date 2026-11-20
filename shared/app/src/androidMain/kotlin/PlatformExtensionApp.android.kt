package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.koyura.app.sourceui.AndroidClipboard
import app.koyura.app.sourceui.AndroidUrlOpener
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.data.session.openExtensionSession
import app.koyura.app.updates.AndroidUpdateNetwork
import app.koyura.app.updates.AndroidLibraryUpdateNotifier
import app.koyura.app.updates.requestLibraryUpdateNotifications

@Composable
internal actual fun PlatformExtensionApp() {
    val context = LocalContext.current
    val browser = remember(context) { androidWebLoginBrowser(context) }
    val environment = extensionEnvironment("android")
    val uiHost = remember(context) {
        SourceUiHost(AndroidClipboard(context), AndroidUrlOpener(context))
    }
    val application = context.applicationContext
    val network = remember(application) { AndroidUpdateNetwork(application) }
    val notifier = remember(application) { AndroidLibraryUpdateNotifier(application) }
    ExtensionApp(
        uiHost = uiHost,
        networkChanges = network.changes,
        requestUpdateNotifications = { requestLibraryUpdateNotifications(context) },
    ) { host ->
        application.openExtensionSession(
            environment, browser, sourceUi = host, isOnline = network::isOnline,
            isUnmetered = network::isUnmetered, updateNotifier = notifier,
        )
    }
}
