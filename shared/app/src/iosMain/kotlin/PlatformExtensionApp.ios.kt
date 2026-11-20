package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.koyura.app.sourceui.IosClipboard
import app.koyura.app.sourceui.IosUrlOpener
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.data.session.openExtensionSession
import app.koyura.app.updates.IosLibraryBackgroundTasks
import app.koyura.app.updates.IosLibraryUpdateNotifier
import app.koyura.app.updates.IosUpdateNetwork

@Composable
internal actual fun PlatformExtensionApp() {
    val environment = extensionEnvironment("ios")
    val browser = remember { IosWebLoginBrowser() }
    val uiHost = remember { SourceUiHost(IosClipboard(), IosUrlOpener()) }
    ExtensionApp(
        uiHost = uiHost,
        backgroundUpdates = IosLibraryBackgroundTasks.sessions,
        networkChanges = IosUpdateNetwork.changes,
        scheduleBackgroundUpdate = IosLibraryBackgroundTasks::schedule,
        requestUpdateNotifications = IosLibraryUpdateNotifier::requestPermission,
    ) { host ->
        openExtensionSession(
            environment = environment, webLoginBrowser = browser, sourceUi = host,
            isOnline = IosUpdateNetwork::isOnline, isUnmetered = IosUpdateNetwork::isUnmetered,
            updateNotifier = IosLibraryUpdateNotifier,
        )
    }
}
