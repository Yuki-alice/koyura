package app.koyura.app.sourceui

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** iOS 浏览器打开：sharedApplication openURL。 */
class IosUrlOpener : SourceUrlOpener {
    override suspend fun open(url: String): Boolean =
        runCatching {
            val nsUrl = NSURL.URLWithString(url) ?: return false
            UIApplication.sharedApplication.openURL(nsUrl)
            true
        }.getOrDefault(false)
}
