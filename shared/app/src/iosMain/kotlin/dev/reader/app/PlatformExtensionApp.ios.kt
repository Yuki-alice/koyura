package dev.reader.app

import androidx.compose.runtime.Composable
import dev.reader.data.session.openExtensionSession

@Composable
internal actual fun PlatformExtensionApp() {
    val environment = extensionEnvironment("ios")
    ExtensionApp { openExtensionSession(environment = environment) }
}
