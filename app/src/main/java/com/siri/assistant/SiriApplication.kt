package com.siri.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.siri.assistant.assistant.AssistantEngine
import com.siri.assistant.service.AssistantForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SiriApplication : Application() {

    companion object {
        const val CHANNEL_ID = "siri_assistant_channel"
        const val CHANNEL_NAME = "Siri Voice Assistant"
        lateinit var instance: SiriApplication
            private set
    }

    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val engine: AssistantEngine by lazy {
        AssistantEngine(this, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // Automatically start always-on background foreground service
        AssistantForegroundService.startService(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for always-on Siri voice assistant"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
