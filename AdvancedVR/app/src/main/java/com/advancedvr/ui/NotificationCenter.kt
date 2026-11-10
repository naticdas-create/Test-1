package com.advancedvr.ui

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.advancedvr.VrServices
import java.util.concurrent.CopyOnWriteArrayList

data class VrNotification(val title: String, val text: String, val time: Long, val source: String)

/** Histórico de notificações + toasts espaciais. Recebe notificações reais do Android via NotificationListenerService. */
class NotificationCenter(private val svc: VrServices) {
    val items = CopyOnWriteArrayList<VrNotification>()
    @Volatile var listener: (() -> Unit)? = null

    fun post(title: String, text: String, source: String = "Sistema", toast: Boolean = true) {
        svc.main.post {
            items.add(0, VrNotification(title, text, System.currentTimeMillis(), source))
            while (items.size > 100) items.removeAt(items.size - 1)
            listener?.invoke()
            if (toast) svc.wmOrNull()?.showToast(title, text)
        }
    }

    fun clear() { items.clear(); listener?.invoke() }
}

object VrBus { @Volatile var notifications: NotificationCenter? = null }

class VrNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: sbn.packageName
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        VrBus.notifications?.post(title, text, sbn.packageName)
    }
}
