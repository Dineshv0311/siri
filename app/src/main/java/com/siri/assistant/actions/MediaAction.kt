package com.siri.assistant.actions

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

class MediaAction(
    private val context: Context,
    private val appLaunchAction: AppLaunchAction
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun execute(command: MediaCommand): ActionResult {
        return when (command) {
            MediaCommand.PLAY -> dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY, "Playing music.")
            MediaCommand.PAUSE -> dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE, "Pausing music.")
            MediaCommand.RESUME -> dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Resuming playback.")
            MediaCommand.NEXT -> dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, "Skipping to next track.")
            MediaCommand.PREVIOUS -> dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Skipping to previous track.")
            MediaCommand.VOLUME_UP -> {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                ActionResult(success = true, spokenResponse = "Volume turned up.")
            }
            MediaCommand.VOLUME_DOWN -> {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                ActionResult(success = true, spokenResponse = "Volume turned down.")
            }
        }
    }

    private fun dispatchKeyEvent(keyCode: Int, spoken: String): ActionResult {
        return try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)

            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)

            ActionResult(
                success = true,
                spokenResponse = spoken,
                uiDetails = "Dispatched media key code: $keyCode"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Failed to control media playback.",
                uiDetails = "Media key event failed: ${e.message}"
            )
        }
    }
}
