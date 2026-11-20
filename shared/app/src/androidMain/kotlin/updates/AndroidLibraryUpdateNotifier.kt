package app.koyura.app.updates

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import app.koyura.domain.updates.LibraryUpdateNotifier
import app.koyura.domain.updates.ScheduledUpdateCheck

internal class AndroidLibraryUpdateNotifier(private val context: Context) : LibraryUpdateNotifier {
    override suspend fun notify(check: ScheduledUpdateCheck) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val chinese = context.resources.configuration.locales[0].language == "zh"
        val title = if (chinese) "书架更新" else "Library updates"
        val summary = if (chinese) "发现 ${check.newChapters} 个新章节" else "${check.newChapters} new chapters"
        manager.createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_DEFAULT))
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(summary)
            .setAutoCancel(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            notification.setContentIntent(PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        }
        manager.notify(CHANNEL, 1, notification.build())
    }

    private companion object {
        const val CHANNEL = "library_updates"
    }
}

internal fun requestLibraryUpdateNotifications(context: Context) {
    if (Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) return
    var current = context
    while (current is ContextWrapper && current !is Activity) {
        current = current.baseContext
    }
    val activity = current as? Activity ?: return
    activity.runOnUiThread {
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 741)
    }
}
