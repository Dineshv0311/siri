package com.siri.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.siri.assistant.MainActivity

class AssistantVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return SiriVoiceSession(this)
    }

    private class SiriVoiceSession(context: Context) : VoiceInteractionSession(context) {

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            val app = context.applicationContext as? com.siri.assistant.SiriApplication
            app?.engine?.startListening()
            hide()
        }
    }
}
