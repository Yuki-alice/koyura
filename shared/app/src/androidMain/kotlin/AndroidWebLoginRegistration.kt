package app.koyura.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import app.koyura.data.extension.WebLoginBrowser

private var webLoginFactory: ((Activity) -> WebLoginBrowser)? = null

fun registerAndroidWebLoginBrowser(factory: (Activity) -> WebLoginBrowser) {
    webLoginFactory = factory
}

internal fun androidWebLoginBrowser(context: Context): WebLoginBrowser? {
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull() ?: return null
    return webLoginFactory?.invoke(activity)
}
