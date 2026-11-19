package app.koyura.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.koyura.app.sourceui.JvmClipboard
import app.koyura.app.sourceui.JvmUrlOpener
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.data.session.openExtensionSession
import okio.Path.Companion.toPath

@Composable
internal actual fun PlatformExtensionApp() {
    val platform = remember {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.startsWith("mac") -> "macos"
            os.startsWith("win") -> "windows"
            else -> "linux"
        }
    }
    val environment = extensionEnvironment(platform)
    val uiHost = remember { SourceUiHost(JvmClipboard(), JvmUrlOpener()) }
    ExtensionApp(uiHost) { host ->
        val home = System.getProperty("user.home").toPath()
        val dataDir = when (platform) {
            "macos" -> home / "Library" / "Application Support" / "Reader"
            "windows" -> {
                val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.toPath()
                    ?: (home / "AppData" / "Local")
                local / "Reader"
            }
            else -> {
                val data = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.toPath()
                    ?.takeIf { it.isAbsolute } ?: (home / ".local" / "share")
                data / "reader"
            }
        }
        openExtensionSession(dataDir / "venera-ext", environment, sourceUi = host)
    }
}
