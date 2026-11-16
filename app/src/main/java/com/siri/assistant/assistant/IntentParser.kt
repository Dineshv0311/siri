package com.siri.assistant.assistant

import com.siri.assistant.actions.*
import com.siri.assistant.ai.GeminiRepository
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

class IntentParser(private val geminiRepository: GeminiRepository) {

    /**
     * Parses user text into a structured AssistantIntent.
     */
    suspend fun parse(rawText: String): AssistantIntent {
        val trimmed = rawText.trim()
        val text = trimmed.lowercase(Locale.getDefault())

        // 1. Local fast pattern matching
        parseLocal(text, trimmed)?.let { return it }

        // 2. Gemini NLU fallback
        val geminiJson = geminiRepository.parseIntentWithGemini(trimmed)
        if (!geminiJson.isNullOrBlank()) {
            parseStructuredJson(geminiJson)?.let { return it }
        }

        // 3. Fallback to General Knowledge / Web query if text looks like a question or conversational prompt
        if (text.startsWith("who ") || text.startsWith("what ") || text.startsWith("where ") ||
            text.startsWith("when ") || text.startsWith("why ") || text.startsWith("how ") ||
            text.startsWith("explain ") || text.startsWith("tell me about ") || text.startsWith("search ")
        ) {
            return AssistantIntent.GeneralQuery(trimmed)
        }

        return AssistantIntent.GeneralQuery(trimmed)
    }

    private fun parseLocal(lower: String, original: String): AssistantIntent? {
        // Strip leading "hey siri", "siri", "ok siri" preserving original casing for extracted arguments
        val prefixRegex = Regex("^(hey siri|siri|ok siri)[,\\s]*", RegexOption.IGNORE_CASE)
        val clean = original.replace(prefixRegex, "").trim()
        val cleanLower = clean.lowercase(Locale.getDefault())

        // --- CALL ACTIONS ---
        val callPattern = Pattern.compile(
            "^(?:please\\s+)?(?:can you\\s+)?(?:i want to\\s+)?(?:call|phone|dial|ring|speak with)\\s+(?:my\\s+)?(.+?)(?:\\s+please)?$",
            Pattern.CASE_INSENSITIVE
        )
        val callMatcher = callPattern.matcher(clean)
        if (callMatcher.matches()) {
            val target = callMatcher.group(1)?.trim().orEmpty()
            if (target.isNotEmpty()) {
                return AssistantIntent.MakeCall(target = target)
            }
        }

        // --- APP LAUNCH ACTIONS ---
        val launchPattern = Pattern.compile(
            "^(?:open|launch|start|run)\\s+(.+?)(?:\\s+app)?$",
            Pattern.CASE_INSENSITIVE
        )
        val launchMatcher = launchPattern.matcher(clean)
        if (launchMatcher.matches()) {
            val app = launchMatcher.group(1)?.trim().orEmpty()
            if (app.isNotEmpty()) {
                return AssistantIntent.LaunchApp(appName = app)
            }
        }

        // --- WHATSAPP MESSAGING ---
        val waPattern = Pattern.compile(
            "^(?:send\\s+(?:a\\s+)?whatsapp(?:\\s+message)?\\s+to|whatsapp)\\s+(.+?)\\s+(?:saying|that)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val waMatcher = waPattern.matcher(clean)
        if (waMatcher.matches()) {
            val recipient = waMatcher.group(1)?.trim().orEmpty()
            val msg = waMatcher.group(2)?.trim().orEmpty()
            return AssistantIntent.SendMessage(
                platform = MessagePlatform.WHATSAPP,
                recipient = recipient,
                message = msg
            )
        }

        // --- SMS MESSAGING ---
        val smsPattern = Pattern.compile(
            "^(?:send\\s+(?:an?\\s+)?(?:sms|text|message)\\s+to|text)\\s+(.+?)\\s+(?:saying|that)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val smsMatcher = smsPattern.matcher(clean)
        if (smsMatcher.matches()) {
            val recipient = smsMatcher.group(1)?.trim().orEmpty()
            val msg = smsMatcher.group(2)?.trim().orEmpty()
            return AssistantIntent.SendMessage(
                platform = MessagePlatform.SMS,
                recipient = recipient,
                message = msg
            )
        }

        // --- SETTINGS CONTROLS ---
        if (cleanLower.contains("flashlight") || cleanLower.contains("torch")) {
            return if (cleanLower.contains("off")) {
                AssistantIntent.ControlSetting(SettingType.FLASHLIGHT_OFF)
            } else {
                AssistantIntent.ControlSetting(SettingType.FLASHLIGHT_ON)
            }
        }

        if (cleanLower.contains("wi-fi") || cleanLower.contains("wifi")) {
            return if (cleanLower.contains("off") || cleanLower.contains("disable")) {
                AssistantIntent.ControlSetting(SettingType.WIFI_OFF)
            } else {
                AssistantIntent.ControlSetting(SettingType.WIFI_ON)
            }
        }

        if (cleanLower.contains("bluetooth")) {
            return if (cleanLower.contains("off") || cleanLower.contains("disable")) {
                AssistantIntent.ControlSetting(SettingType.BLUETOOTH_OFF)
            } else {
                AssistantIntent.ControlSetting(SettingType.BLUETOOTH_ON)
            }
        }

        if (cleanLower.contains("airplane mode")) {
            return AssistantIntent.ControlSetting(SettingType.AIRPLANE_MODE)
        }

        if (cleanLower.contains("brightness")) {
            val pctMatch = Pattern.compile("(\\d+)\\s*%?").matcher(cleanLower)
            return if (cleanLower.contains("increase") || cleanLower.contains("up") || cleanLower.contains("raise") || cleanLower.contains("more")) {
                AssistantIntent.ControlSetting(SettingType.BRIGHTNESS_INCREASE)
            } else if (cleanLower.contains("decrease") || cleanLower.contains("down") || cleanLower.contains("lower") || cleanLower.contains("dim")) {
                AssistantIntent.ControlSetting(SettingType.BRIGHTNESS_DECREASE)
            } else if (pctMatch.find()) {
                val pct = pctMatch.group(1)?.toIntOrNull() ?: 50
                AssistantIntent.ControlSetting(SettingType.BRIGHTNESS_SET, value = pct)
            } else {
                AssistantIntent.ControlSetting(SettingType.BRIGHTNESS_INCREASE)
            }
        }

        if (cleanLower.contains("volume")) {
            return if (cleanLower.contains("down") || cleanLower.contains("decrease") || cleanLower.contains("lower")) {
                AssistantIntent.ControlSetting(SettingType.VOLUME_DECREASE)
            } else {
                AssistantIntent.ControlSetting(SettingType.VOLUME_INCREASE)
            }
        }

        if (cleanLower.contains("location settings")) {
            return AssistantIntent.ControlSetting(SettingType.LOCATION_SETTINGS)
        }
        if (cleanLower.contains("sound settings")) {
            return AssistantIntent.ControlSetting(SettingType.SOUND_SETTINGS)
        }

        // --- ALARM ACTIONS ---
        val alarmPattern = Pattern.compile(
            "^(?:set\\s+(?:an?\\s+)?alarm\\s+(?:for|at)|wake\\s+me\\s+up\\s+at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE
        )
        val alarmMatcher = alarmPattern.matcher(clean)
        if (alarmMatcher.find()) {
            var hour = alarmMatcher.group(1)?.toIntOrNull() ?: 7
            val min = alarmMatcher.group(2)?.toIntOrNull() ?: 0
            val amPm = alarmMatcher.group(3)?.lowercase(Locale.getDefault())

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            return AssistantIntent.SetAlarm(hour = hour, minute = min)
        }

        // --- TIMER ACTIONS ---
        val timerPattern = Pattern.compile(
            "^(?:set\\s+(?:a\\s+)?timer\\s+(?:for\\s+)?|timer\\s+for\\s+)(\\d+)\\s*(minute|minutes|min|mins|second|seconds|sec|secs|hour|hours)",
            Pattern.CASE_INSENSITIVE
        )
        val timerMatcher = timerPattern.matcher(clean)
        if (timerMatcher.find()) {
            val count = timerMatcher.group(1)?.toIntOrNull() ?: 1
            val unit = timerMatcher.group(2)?.lowercase(Locale.getDefault()).orEmpty()
            val seconds = when {
                unit.startsWith("hour") -> count * 3600
                unit.startsWith("sec") -> count
                else -> count * 60
            }
            return AssistantIntent.SetTimer(durationSeconds = seconds)
        }

        // --- NOTIFICATIONS ---
        if (cleanLower.contains("notification")) {
            val filter = if (cleanLower.contains("whatsapp")) "whatsapp" else null
            return AssistantIntent.ReadNotifications(appFilter = filter)
        }
        if (cleanLower.contains("whatsapp message") || cleanLower.contains("messages on whatsapp")) {
            return AssistantIntent.ReadNotifications(appFilter = "whatsapp")
        }

        // --- MEDIA ACTIONS ---
        if (cleanLower == "play music" || cleanLower == "play song" || cleanLower == "play") {
            return AssistantIntent.ControlMedia(MediaCommand.PLAY)
        }
        if (cleanLower == "pause music" || cleanLower == "pause song" || cleanLower == "pause") {
            return AssistantIntent.ControlMedia(MediaCommand.PAUSE)
        }
        if (cleanLower == "resume music" || cleanLower == "resume") {
            return AssistantIntent.ControlMedia(MediaCommand.RESUME)
        }
        if (cleanLower == "next song" || cleanLower == "next track" || cleanLower == "skip song") {
            return AssistantIntent.ControlMedia(MediaCommand.NEXT)
        }
        if (cleanLower == "previous song" || cleanLower == "previous track") {
            return AssistantIntent.ControlMedia(MediaCommand.PREVIOUS)
        }

        // --- WEATHER ---
        if (cleanLower.contains("weather") || cleanLower.contains("rain today") || cleanLower.contains("temperature")) {
            val loc = if (cleanLower.contains(" in ")) clean.substring(cleanLower.indexOf(" in ") + 4).trim() else null
            return AssistantIntent.GetWeather(location = loc)
        }

        return null
    }

    private fun parseStructuredJson(jsonString: String): AssistantIntent? {
        return try {
            val json = JSONObject(jsonString)
            val intent = json.optString("intent", "")

            when (intent) {
                "MAKE_CALL" -> {
                    val target = json.optString("target", "")
                    if (target.isNotBlank()) AssistantIntent.MakeCall(target = target) else null
                }
                "SEND_WHATSAPP" -> {
                    val target = json.optString("target", "")
                    val msg = json.optString("message", "")
                    if (target.isNotBlank()) {
                        AssistantIntent.SendMessage(MessagePlatform.WHATSAPP, target, msg)
                    } else null
                }
                "SEND_SMS" -> {
                    val target = json.optString("target", "")
                    val msg = json.optString("message", "")
                    if (target.isNotBlank()) {
                        AssistantIntent.SendMessage(MessagePlatform.SMS, target, msg)
                    } else null
                }
                "OPEN_APP" -> {
                    val app = json.optString("app_name", "")
                    if (app.isNotBlank()) AssistantIntent.LaunchApp(app) else null
                }
                "SET_ALARM" -> {
                    val h = json.optInt("time_hour", 7)
                    val m = json.optInt("time_minute", 0)
                    AssistantIntent.SetAlarm(h, m)
                }
                "SET_TIMER" -> {
                    val s = json.optInt("timer_seconds", 60)
                    AssistantIntent.SetTimer(s)
                }
                "READ_NOTIFICATIONS" -> {
                    val filter = if (json.has("app_name") && !json.isNull("app_name")) json.getString("app_name") else null
                    AssistantIntent.ReadNotifications(filter)
                }
                "GET_WEATHER" -> {
                    val loc = if (json.has("target") && !json.isNull("target")) json.getString("target") else null
                    AssistantIntent.GetWeather(loc)
                }
                "GENERAL_QA" -> {
                    val q = json.optString("query", "")
                    if (q.isNotBlank()) AssistantIntent.GeneralQuery(q) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
