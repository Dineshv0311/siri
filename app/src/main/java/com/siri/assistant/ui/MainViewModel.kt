package com.siri.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siri.assistant.SiriApplication
import com.siri.assistant.assistant.AssistantEngine
import com.siri.assistant.assistant.AssistantState
import com.siri.assistant.data.database.ConversationEntity
import com.siri.assistant.service.AssistantForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val engine: AssistantEngine = (application as SiriApplication).engine

    val assistantState: StateFlow<AssistantState> = engine.assistantState
    val rmsDb: StateFlow<Float> = engine.rmsDb
    val lastSpokenText: StateFlow<String> = engine.lastSpokenText
    val conversationHistory: StateFlow<List<ConversationEntity>> =
        engine.conversationManager.conversationHistory.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

    fun onOrbClick() {
        if (assistantState.value == AssistantState.LISTENING) {
            engine.stopListening()
        } else {
            engine.startListening()
        }
    }

    fun handleWakeWordTrigger() {
        engine.onWakeWordDetected()
    }

    fun submitTextCommand(text: String) {
        engine.handleUserInput(text)
    }

    fun clearHistory() {
        engine.conversationManager.clearHistory()
    }

    fun toggleForegroundService(enable: Boolean) {
        val app = getApplication<Application>()
        if (enable) {
            AssistantForegroundService.startService(app)
        } else {
            AssistantForegroundService.stopService(app)
        }
    }
}
