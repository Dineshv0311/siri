package com.siri.assistant.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings

class SettingsAction(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    fun execute(setting: SettingType, value: Int? = null): ActionResult {
        return when (setting) {
            SettingType.FLASHLIGHT_ON -> toggleFlashlight(true)
            SettingType.FLASHLIGHT_OFF -> toggleFlashlight(false)
            SettingType.VOLUME_INCREASE -> adjustVolume(AudioManager.ADJUST_RAISE)
            SettingType.VOLUME_DECREASE -> adjustVolume(AudioManager.ADJUST_LOWER)
            SettingType.BRIGHTNESS_INCREASE -> adjustBrightness(increase = true)
            SettingType.BRIGHTNESS_DECREASE -> adjustBrightness(increase = false)
            SettingType.BRIGHTNESS_SET -> setBrightness(value ?: 50)
            SettingType.WIFI_ON, SettingType.WIFI_OFF -> openWifiPanel(setting == SettingType.WIFI_ON)
            SettingType.BLUETOOTH_ON, SettingType.BLUETOOTH_OFF -> openBluetoothSettings(setting == SettingType.BLUETOOTH_ON)
            SettingType.AIRPLANE_MODE -> openSystemSetting(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode settings")
            SettingType.LOCATION_SETTINGS -> openSystemSetting(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location settings")
            SettingType.SOUND_SETTINGS -> openSystemSetting(Settings.ACTION_SOUND_SETTINGS, "Sound settings")
        }
    }

    private fun toggleFlashlight(enable: Boolean): ActionResult {
        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                hasFlash && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager?.cameraIdList?.firstOrNull()

            if (cameraId != null) {
                cameraManager?.setTorchMode(cameraId, enable)
                val state = if (enable) "on" else "off"
                ActionResult(
                    success = true,
                    spokenResponse = "Flashlight turned $state.",
                    uiDetails = "Torch set to $enable on camera $cameraId"
                )
            } else {
                ActionResult(
                    success = false,
                    spokenResponse = "Flashlight hardware was not found on your device.",
                    uiDetails = "No compatible camera with flash found"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Unable to toggle the flashlight.",
                uiDetails = "Flashlight error: ${e.message}"
            )
        }
    }

    private fun adjustVolume(direction: Int): ActionResult {
        return try {
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
            )
            val dirWord = if (direction == AudioManager.ADJUST_RAISE) "increased" else "decreased"
            ActionResult(
                success = true,
                spokenResponse = "Volume $dirWord.",
                uiDetails = "Adjusted music stream volume: $dirWord"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Couldn't adjust volume.",
                uiDetails = "Audio manager error: ${e.message}"
            )
        }
    }

    private fun adjustBrightness(increase: Boolean): ActionResult {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ActionResult(
                success = true,
                spokenResponse = "Opening display settings so you can adjust brightness.",
                uiDetails = "System write permissions needed; redirected to display settings"
            )
        }

        return try {
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val delta = 40
            val target = if (increase) (current + delta).coerceAtMost(255) else (current - delta).coerceAtLeast(10)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
            val pct = (target * 100) / 255
            ActionResult(
                success = true,
                spokenResponse = "Brightness set to $pct%.",
                uiDetails = "Screen brightness updated to $target/255"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Could not adjust screen brightness.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }

    private fun setBrightness(percent: Int): ActionResult {
        val clampedPct = percent.coerceIn(0, 100)
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ActionResult(
                success = true,
                spokenResponse = "Opening display settings to change brightness.",
                uiDetails = "WRITE_SETTINGS permission required"
            )
        }

        return try {
            val rawValue = (clampedPct * 255) / 100
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, rawValue)
            ActionResult(
                success = true,
                spokenResponse = "Brightness set to $clampedPct%.",
                uiDetails = "Brightness changed to $rawValue ($clampedPct%)"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Failed to update brightness.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }

    private fun openWifiPanel(turnOn: Boolean): ActionResult {
        val actionWord = if (turnOn) "on" else "off"
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                spokenResponse = "Opening Wi-Fi controls to turn it $actionWord.",
                uiDetails = "Launched Wi-Fi panel"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Couldn't open Wi-Fi settings.",
                uiDetails = "Error launching Wi-Fi settings: ${e.message}"
            )
        }
    }

    private fun openBluetoothSettings(turnOn: Boolean): ActionResult {
        val actionWord = if (turnOn) "on" else "off"
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                spokenResponse = "Opening Bluetooth settings to turn it $actionWord.",
                uiDetails = "Launched Bluetooth settings"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Couldn't open Bluetooth settings.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }

    private fun openSystemSetting(action: String, title: String): ActionResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                spokenResponse = "Opening $title.",
                uiDetails = "Opened settings action: $action"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Unable to open $title.",
                uiDetails = "Error opening $action: ${e.message}"
            )
        }
    }
}
