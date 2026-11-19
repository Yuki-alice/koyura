package app.koyura.app.sourceui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Android 剪贴板：经系统 ClipboardManager 读写纯文本。 */
class AndroidClipboard(context: Context) : SourceClipboard {
    private val appContext = context.applicationContext
    private val manager = appContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override suspend fun getText(): String? =
        runCatching {
            manager.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()
        }.getOrNull()

    override suspend fun setText(text: String): Boolean =
        runCatching {
            manager.setPrimaryClip(ClipData.newPlainText("koyura", text))
            true
        }.getOrDefault(false)
}
