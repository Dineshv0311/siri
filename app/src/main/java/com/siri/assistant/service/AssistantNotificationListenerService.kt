package com.siri.assistant.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

data class AppNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

class AssistantNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _recentNotifications = mutableListOf<AppNotification>()
        val recentNotifications: List<AppNotification>
            @Synchronized get() = _recentNotifications.toList()

        var isConnected: Boolean = false
            private set

        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        fetchActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val pkg = sbn.packageName ?: ""

        if (title.isNotEmpty() || text.isNotEmpty()) {
            synchronized(_recentNotifications) {
                _recentNotifications.removeAll { it.packageName == pkg && it.title == title }
                _recentNotifications.add(
                    0,
                    AppNotification(
                        packageName = pkg,
                        title = title,
                        text = text,
                        postTime = sbn.postTime
                    )
                )
                // Keep last 30
                if (_recentNotifications.size > 30) {
                    _recentNotifications.removeAt(_recentNotifications.size - 1)
                }
            }
        }
    }

    private fun fetchActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            synchronized(_recentNotifications) {
                _recentNotifications.clear()
                for (sbn in active) {
                    val extras = sbn.notification.extras ?: continue
                    val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                    val pkg = sbn.packageName ?: ""
                    if (title.isNotEmpty() || text.isNotEmpty()) {
                        _recentNotifications.add(
                            AppNotification(
                                packageName = pkg,
                                title = title,
                                text = text,
                                postTime = sbn.postTime
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
