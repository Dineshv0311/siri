package com.siri.assistant.actions

enum class MessagePlatform {
    WHATSAPP,
    SMS
}

enum class SettingType {
    WIFI_ON,
    WIFI_OFF,
    BLUETOOTH_ON,
    BLUETOOTH_OFF,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    BRIGHTNESS_INCREASE,
    BRIGHTNESS_DECREASE,
    BRIGHTNESS_SET,
    VOLUME_INCREASE,
    VOLUME_DECREASE,
    AIRPLANE_MODE,
    LOCATION_SETTINGS,
    SOUND_SETTINGS
}

enum class MediaCommand {
    PLAY,
    PAUSE,
    RESUME,
    NEXT,
    PREVIOUS,
    VOLUME_UP,
    VOLUME_DOWN
}

sealed class AssistantIntent {
    data class MakeCall(
        val target: String,
        val phoneNumber: String? = null,
        val confirmed: Boolean = false
    ) : AssistantIntent()

    data class SendMessage(
        val platform: MessagePlatform,
        val recipient: String,
        val message: String,
        val confirmed: Boolean = false
    ) : AssistantIntent()

    data class LaunchApp(
        val appName: String
    ) : AssistantIntent()

    data class ControlSetting(
        val setting: SettingType,
        val value: Int? = null
    ) : AssistantIntent()

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String = "Alarm"
    ) : AssistantIntent()

    data class SetTimer(
        val durationSeconds: Int,
        val label: String = "Timer"
    ) : AssistantIntent()

    data class ReadNotifications(
        val appFilter: String? = null
    ) : AssistantIntent()

    data class ControlMedia(
        val command: MediaCommand
    ) : AssistantIntent()

    data class GetWeather(
        val location: String? = null
    ) : AssistantIntent()

    data class GeneralQuery(
        val question: String
    ) : AssistantIntent()

    data class DisambiguateContact(
        val choices: List<com.siri.assistant.contacts.ContactInfo>,
        val originalIntent: AssistantIntent
    ) : AssistantIntent()

    data class Unknown(
        val rawText: String
    ) : AssistantIntent()
}

data class ActionResult(
    val success: Boolean,
    val spokenResponse: String,
    val uiDetails: String? = null,
    val requiresFollowUp: Boolean = false,
    val pendingIntent: AssistantIntent? = null
)
