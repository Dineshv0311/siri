package com.siri.assistant.service

import android.content.Intent
import android.speech.RecognitionService

class AssistantRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Recognition callback placeholder
    }

    override fun onStopListening(listener: Callback?) {
    }

    override fun onCancel(listener: Callback?) {
    }
}
