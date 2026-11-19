package app.koyura.app.sourceui

import platform.UIKit.UIPasteboard

/** iOS 剪贴板：UIPasteboard 通用板读写纯文本。 */
class IosClipboard : SourceClipboard {
    override suspend fun getText(): String? =
        runCatching { UIPasteboard.generalPasteboard.string }.getOrNull()

    override suspend fun setText(text: String): Boolean =
        runCatching {
            UIPasteboard.generalPasteboard.string = text
            true
        }.getOrDefault(false)
}
