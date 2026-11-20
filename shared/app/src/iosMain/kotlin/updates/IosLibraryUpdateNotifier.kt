package app.koyura.app.updates

import app.koyura.domain.updates.LibraryUpdateNotifier
import app.koyura.domain.updates.ScheduledUpdateCheck
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

internal object IosLibraryUpdateNotifier : LibraryUpdateNotifier {
    override suspend fun notify(check: ScheduledUpdateCheck) {
        val chinese = NSLocale.preferredLanguages.firstOrNull()?.toString()?.startsWith("zh") == true
        val content = UNMutableNotificationContent().apply {
            setTitle(if (chinese) "书架更新" else "Library updates")
            setBody(if (chinese) "发现 ${check.newChapters} 个新章节" else "${check.newChapters} new chapters")
        }
        val request = UNNotificationRequest.requestWithIdentifier("library-updates", content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }

    fun requestPermission() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }
}
