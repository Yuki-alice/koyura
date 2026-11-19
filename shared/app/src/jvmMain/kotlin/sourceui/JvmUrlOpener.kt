package app.koyura.app.sourceui

import java.awt.Desktop
import java.net.URI

/** 桌面浏览器打开：java.awt.Desktop（无桌面环境返回 false）。 */
class JvmUrlOpener : SourceUrlOpener {
    override suspend fun open(url: String): Boolean =
        runCatching {
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else return false
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
            desktop.browse(URI(url))
            true
        }.getOrDefault(false)
}
