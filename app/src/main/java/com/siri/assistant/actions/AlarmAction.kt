package com.siri.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.Locale

class AlarmAction(private val context: Context) {

    fun setAlarm(hour: Int, minute: Int, label: String = "Alarm"): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val amPm = if (hour >= 12) "PM" else "AM"
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                val formattedTime = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
                ActionResult(
                    success = true,
                    spokenResponse = "Your alarm is set for $formattedTime.",
                    uiDetails = "Alarm set for $formattedTime with label '$label'"
                )
            } else {
                ActionResult(
                    success = false,
                    spokenResponse = "No clock or alarm application is installed.",
                    uiDetails = "ACTION_SET_ALARM not resolved"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Failed to set the alarm.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }

    fun setTimer(durationSeconds: Int, label: String = "Timer"): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val mins = durationSeconds / 60
                val secs = durationSeconds % 60
                val timeDesc = if (mins > 0 && secs > 0) {
                    "$mins minute${if (mins > 1) "s" else ""} and $secs seconds"
                } else if (mins > 0) {
                    "$mins minute${if (mins > 1) "s" else ""}"
                } else {
                    "$secs seconds"
                }
                ActionResult(
                    success = true,
                    spokenResponse = "Setting a timer for $timeDesc.",
                    uiDetails = "Timer created for $durationSeconds seconds"
                )
            } else {
                ActionResult(
                    success = false,
                    spokenResponse = "No timer application found.",
                    uiDetails = "ACTION_SET_TIMER not resolved"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Failed to start timer.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }

    fun showAlarms(): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    spokenResponse = "Opening your alarms.",
                    uiDetails = "Launched clock alarms screen"
                )
            } else {
                ActionResult(
                    success = false,
                    spokenResponse = "Could not open clock alarms.",
                    uiDetails = "No alarm app resolved"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Failed to open alarms.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }
}
