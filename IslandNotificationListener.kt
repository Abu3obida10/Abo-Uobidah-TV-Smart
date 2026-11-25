package com.uobidah.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.uobidah.launcher.core.AlertType

/** يلتقط إشعارات التطبيقات ويمررها للموزّع الموحد (سياسة لكل تطبيق + منع التكرار). */
class IslandNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val pkg = sbn.packageName
            if (pkg == packageName) return
            val n = sbn.notification ?: return
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
            val extras = n.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (title.isEmpty() && text.isEmpty()) return
            val label = AppUtil.label(packageManager, pkg)
            val head = if (title.isEmpty()) label else "$label • $title"
            // المفتاح = التطبيق + المحتوى؛ تحديثات التقدم المتكررة لنفس الإشعار تُتجاهل.
            AlertDispatcher.dispatch(this, AlertType.APP, pkg, pkg + "|" + title + "|" + text, head, text, "🔔")
        } catch (e: Exception) {
        }
    }
}
