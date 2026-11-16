package com.siri.assistant.assistant

import android.content.Context
import android.util.Log
import com.siri.assistant.actions.*
import com.siri.assistant.ai.GeminiRepository
import com.siri.assistant.contacts.ContactRepository
import com.siri.assistant.data.database.AssistantDatabase
import com.siri.assistant.voice.SpeechManager
import com.siri.assistant.voice.TTSManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantState {
    STANDBY,
    LISTENING,
    PROCESSING,
    EXECUTING,
    SPEAKING,
    ERROR
}

class AssistantEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val database = AssistantDatabase.getInstance(context)
    val conversationManager = ConversationManager(database)
    val contactRepository = ContactRepository(context)
    val geminiRepository = GeminiRepository(context)
    val intentParser = IntentParser(geminiRepository)

    // Actions
    val callAction = CallAction(context, contactRepository)
    val appLaunchAction = AppLaunchAction(context)
    val messageAction = MessageAction(context, contactRepository)
    val settingsAction = SettingsAction(context)
    val alarmAction = AlarmAction(context)
    val notificationAction = NotificationAction(context)
    val mediaAction = MediaAction(context, appLaunchAction)

    // Voice
    val ttsManager = TTSManager(context)
    val speechManager = SpeechManager(context)

    private val _assistantState = MutableStateFlow(AssistantState.STANDBY)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private var pendingIntent: AssistantIntent? = null
    private var isAwaitingCommandAfterWake = false
    var onWakeWordStateChange: ((Boolean) -> Unit)? = null

    init {
        speechManager.onRmsChanged = { rms ->
            _rmsDb.value = rms
        }

        speechManager.onPartialResult = { partial ->
            _lastSpokenText.value = partial
        }

        speechManager.onSpeechResult = { text ->
            if (text.isNotBlank()) {
                handleUserInput(text)
            } else {
                returnToStandby()
            }
        }

        speechManager.onError = { errorCode ->
            returnToStandby()
        }

        ttsManager.onSpeechStarted = {
            _assistantState.value = AssistantState.SPEAKING
        }

        ttsManager.onSpeechCompleted = {
            if (isAwaitingCommandAfterWake) {
                isAwaitingCommandAfterWake = false
                startListening()
            } else if (pendingIntent != null) {
                // If follow-up question was asked, automatically listen for answer
                startListening()
            } else {
                returnToStandby()
            }
        }

        ttsManager.onSpeechError = {
            returnToStandby()
        }
    }

    private fun returnToStandby() {
        _assistantState.value = AssistantState.STANDBY
        onWakeWordStateChange?.invoke(true)
    }

    fun startListening() {
        ttsManager.stop()
        onWakeWordStateChange?.invoke(false)
        _assistantState.value = AssistantState.LISTENING
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
        returnToStandby()
    }

    fun onWakeWordDetected() {
        speakPromptAndListen("I'm listening.")
    }

    fun speakPromptAndListen(prompt: String = "I'm listening.") {
        onWakeWordStateChange?.invoke(false)
        isAwaitingCommandAfterWake = true
        _assistantState.value = AssistantState.SPEAKING
        ttsManager.speak(prompt)
    }

    fun handleUserInput(rawText: String) {
        Log.i("AssistantEngine", "handleUserInput received: '$rawText'")
        onWakeWordStateChange?.invoke(false)
        scope.launch {
            _assistantState.value = AssistantState.PROCESSING
            conversationManager.logUserMessage(rawText)

            // Check if answering a pending follow-up (e.g. confirmation or contact choice)
            val currentPending = pendingIntent
            if (currentPending != null) {
                handleFollowUp(rawText, currentPending)
                return@launch
            }

            val intent = intentParser.parse(rawText)
            Log.i("AssistantEngine", "Parsed intent: $intent")
            executeIntent(intent)
        }
    }

    private suspend fun handleFollowUp(userResponse: String, pending: AssistantIntent) {
        val lower = userResponse.lowercase()
        pendingIntent = null

        when (pending) {
            is AssistantIntent.SendMessage -> {
                if (lower.contains("yes") || lower.contains("send") || lower.contains("sure") || lower.contains("ok")) {
                    executeIntent(pending.copy(confirmed = true))
                } else {
                    speakResponse("Message cancelled.", actionType = "SEND_MESSAGE", success = false)
                }
            }
            is AssistantIntent.DisambiguateContact -> {
                val matched = pending.choices.firstOrNull {
                    userResponse.contains(it.name, ignoreCase = true) ||
                    userResponse.contains(it.phoneNumber)
                } ?: pending.choices.firstOrNull()

                if (matched != null) {
                    when (val orig = pending.originalIntent) {
                        is AssistantIntent.MakeCall -> {
                            val res = callAction.execute(orig.target, directNumber = matched.phoneNumber, confirmed = true)
                            handleResult(res, "MAKE_CALL")
                        }
                        else -> {
                            speakResponse("Understood.", actionType = "DISAMBIGUATION", success = true)
                        }
                    }
                } else {
                    speakResponse("Cancelled.", actionType = "DISAMBIGUATION", success = false)
                }
            }
            else -> {
                val intent = intentParser.parse(userResponse)
                executeIntent(intent)
            }
        }
    }

    private suspend fun executeIntent(intent: AssistantIntent) {
        _assistantState.value = AssistantState.EXECUTING

        when (intent) {
            is AssistantIntent.MakeCall -> {
                val result = callAction.execute(intent.target, intent.phoneNumber, intent.confirmed)
                handleResult(result, "MAKE_CALL")
            }
            is AssistantIntent.LaunchApp -> {
                val result = appLaunchAction.execute(intent.appName)
                handleResult(result, "OPEN_APP")
            }
            is AssistantIntent.SendMessage -> {
                val result = messageAction.execute(intent.platform, intent.recipient, intent.message, intent.confirmed)
                handleResult(result, "SEND_MESSAGE")
            }
            is AssistantIntent.ControlSetting -> {
                val result = settingsAction.execute(intent.setting, intent.value)
                handleResult(result, "CONTROL_SETTING")
            }
            is AssistantIntent.SetAlarm -> {
                val result = alarmAction.setAlarm(intent.hour, intent.minute, intent.label)
                handleResult(result, "SET_ALARM")
            }
            is AssistantIntent.SetTimer -> {
                val result = alarmAction.setTimer(intent.durationSeconds, intent.label)
                handleResult(result, "SET_TIMER")
            }
            is AssistantIntent.ReadNotifications -> {
                val result = notificationAction.execute(intent.appFilter)
                handleResult(result, "READ_NOTIFICATIONS")
            }
            is AssistantIntent.ControlMedia -> {
                val result = mediaAction.execute(intent.command)
                handleResult(result, "CONTROL_MEDIA")
            }
            is AssistantIntent.GetWeather -> {
                val weatherText = geminiRepository.getLiveWeather(intent.location)
                speakResponse(weatherText, actionType = "WEATHER", success = true)
            }
            is AssistantIntent.GeneralQuery -> {
                val answer = geminiRepository.askGemini(intent.question)
                speakResponse(answer, actionType = "GENERAL_QA", success = true)
            }
            is AssistantIntent.DisambiguateContact -> {
                pendingIntent = intent
                speakResponse(
                    "Which contact did you mean?",
                    actionType = "DISAMBIGUATE",
                    success = true,
                    requiresFollowUp = true
                )
            }
            is AssistantIntent.Unknown -> {
                val answer = geminiRepository.askGemini(intent.rawText)
                speakResponse(answer, actionType = "GENERAL_QA", success = true)
            }
        }
    }

    private fun handleResult(result: ActionResult, actionType: String) {
        if (result.requiresFollowUp) {
            pendingIntent = result.pendingIntent
        } else {
            pendingIntent = null
        }
        speakResponse(result.spokenResponse, actionType = actionType, success = result.success)
    }

    private fun speakResponse(text: String, actionType: String, success: Boolean, requiresFollowUp: Boolean = false) {
        Log.i("AssistantEngine", "speakResponse: '$text' (type=$actionType, success=$success)")
        _lastSpokenText.value = text
        conversationManager.logAssistantResponse(text, actionType = actionType, status = if (success) "SUCCESS" else "FAILED")
        _assistantState.value = AssistantState.SPEAKING
        ttsManager.speak(text)
    }

    fun shutdown() {
        speechManager.destroy()
        ttsManager.shutdown()
    }
}
