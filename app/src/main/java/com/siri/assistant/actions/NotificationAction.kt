package com.siri.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.siri.assistant.service.AssistantNotificationListenerService
import java.util.Locale

class NotificationAction(private val context: Context) {

    fun execute(appFilter: String? = null): ActionResult {
        // 1. Check if user enabled notification listener
        if (!AssistantNotificationListenerService.isPermissionGranted(context)) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ActionResult(
                success = false,
                spokenResponse = "Notification access is required. Please enable Siri Assistant in Notification Access settings.",
                uiDetails = "Redirected to ACTION_NOTIFICATION_LISTENER_SETTINGS"
            )
        }

        val notifications = AssistantNotificationListenerService.recentNotifications

        if (notifications.isEmpty()) {
            return ActionResult(
                success = true,
                spokenResponse = "You have no active notifications right now.",
                uiDetails = "Notification inbox empty"
            )
        }

        val filtered = if (!appFilter.isNullOrBlank()) {
            val filter = appFilter.lowercase(Locale.getDefault())
            notifications.filter {
                it.packageName.lowercase(Locale.getDefault()).contains(filter) ||
                it.title.lowercase(Locale.getDefault()).contains(filter)
            }
        } else {
            notifications
        }

        if (filtered.isEmpty()) {
            return ActionResult(
                success = true,
                spokenResponse = "You don't have any notifications from $appFilter.",
                uiDetails = "No matching notifications for $appFilter"
            )
        }

        // Read top 3
        val top = filtered.take(3)
        val spoken = buildString {
            if (filtered.size == 1) {
                append("You have 1 notification: ")
            } else {
                append("You have ${filtered.size} notifications. Here are the latest: ")
            }
            top.forEachIndexed { index, notif ->
                append("${notif.title} says: ${notif.text}. ")
            }
        }

        val details = top.joinToString("\n") { "• [${it.packageName}] ${it.title}: ${it.text}" }

        return ActionResult(
            success = true,
            spokenResponse = spoken.trim(),
            uiDetails = details
        )
    }
}
