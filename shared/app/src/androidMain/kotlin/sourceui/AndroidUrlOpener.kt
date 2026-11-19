package app.koyura.app.sourceui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Android 浏览器打开：ACTION_VIEW 系统选择器。 */
class AndroidUrlOpener(context: Context) : SourceUrlOpener {
    private val appContext = context.applicationContext

    override suspend fun open(url: String): Boolean =
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
}
