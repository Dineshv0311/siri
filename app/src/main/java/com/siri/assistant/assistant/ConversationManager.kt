package com.siri.assistant.assistant

import com.siri.assistant.data.database.AssistantDatabase
import com.siri.assistant.data.database.ConversationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ConversationManager(private val database: AssistantDatabase) {

    private val scope = CoroutineScope(Dispatchers.IO)

    val conversationHistory: Flow<List<ConversationEntity>> =
        database.conversationDao().getAllMessages()

    fun logUserMessage(message: String) {
        scope.launch {
            database.conversationDao().insertMessage(
                ConversationEntity(
                    sender = "USER",
                    message = message
                )
            )
        }
    }

    fun logAssistantResponse(message: String, actionType: String? = null, status: String = "SUCCESS") {
        scope.launch {
            database.conversationDao().insertMessage(
                ConversationEntity(
                    sender = "ASSISTANT",
                    message = message,
                    actionType = actionType,
                    status = status
                )
            )
        }
    }

    fun clearHistory() {
        scope.launch {
            database.conversationDao().clearHistory()
        }
    }
}
