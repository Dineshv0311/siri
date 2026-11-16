package com.siri.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.siri.assistant.MainActivity
import com.siri.assistant.SiriApplication
import com.siri.assistant.voice.WakeWordManager

class AssistantForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_FOREGROUND_SERVICE"
        const val ACTION_COMMAND = "com.siri.assistant.COMMAND"
        const val EXTRA_COMMAND = "command"
        const val NOTIFICATION_ID = 1001

        var isRunning: Boolean = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeWordManager: WakeWordManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        // Acquire WakeLock so microphone wake detection continues while screen is locked/off
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SiriAssistant:WakeWordWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 12 hours timeout safety
        }

        val app = application as SiriApplication
        wakeWordManager = WakeWordManager(this) { embeddedCommand ->
            handleWakeActivation(embeddedCommand)
        }

        // Keep wake-word detector synchronized with engine state
        app.engine.onWakeWordStateChange = { inStandby ->
            if (inStandby) {
                wakeWordManager?.resume()
            } else {
                wakeWordManager?.pause()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_COMMAND)
                if (!cmd.isNullOrBlank()) {
                    handleWakeActivation(cmd)
                }
            }
            else -> {
                startInForeground()
                wakeWordManager?.start()
                isRunning = true
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, SiriApplication.CHANNEL_ID)
            .setContentTitle("Siri Assistant Active")
            .setContentText("Listening for \"Hey Siri\" hands-free")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun handleWakeActivation(embeddedCommand: String?) {
        // Haptic feedback to acknowledge voice
        vibrateFeedback()

        val app = application as SiriApplication

        // If user said wake-word + command in one sentence ("Hey Siri open WhatsApp" or "Hey Siri turn on flashlight")
        if (!embeddedCommand.isNullOrBlank()) {
            app.engine.handleUserInput(embeddedCommand)
        } else {
            // User just said "Hey Siri" -> speak confirmation prompt and open microphone for command
            app.engine.speakPromptAndListen("I'm listening.")
        }
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(120)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopForegroundService() {
        isRunning = false
        wakeWordManager?.stop()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeWordManager?.stop()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
