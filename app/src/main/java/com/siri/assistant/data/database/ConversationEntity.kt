package com.siri.assistant.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String, // "USER" or "ASSISTANT"
    val message: String,
    val actionType: String? = null,
    val status: String? = null // "SUCCESS", "EXECUTING", "FAILED"
)
