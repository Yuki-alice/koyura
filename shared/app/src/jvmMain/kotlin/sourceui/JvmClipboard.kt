package app.koyura.app.sourceui

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** 桌面 JVM 剪贴板：AWT 系统剪贴板读写纯文本（无头环境返回空/失败）。 */
class JvmClipboard : SourceClipboard {
    override suspend fun getText(): String? =
        runCatching {
            val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
            if (contents?.isDataFlavorSupported(DataFlavor.stringFlavor) == true) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        }.getOrNull()

    override suspend fun setText(text: String): Boolean =
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            true
        }.getOrDefault(false)
}
