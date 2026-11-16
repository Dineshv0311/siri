package com.siri.assistant.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

class WakeWordManager(
    private val context: Context,
    private val onWakeWordDetected: (embeddedCommand: String?) -> Unit
) {

    private var porcupineManager: PorcupineManager? = null
    private var audioRecord: AudioRecord? = null
    private var recognizer: SpeechRecognizer? = null

    private var isRunning = false
    private var isListeningCommand = false
    private var vadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastTriggerTime = 0L
    private val debounceMs = 2000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("siri_assistant_prefs", Context.MODE_PRIVATE)

    private val wakeKeywords = listOf(
        "hey siri", "siri", "hi siri", "ok siri",
        "hey serious", "serious", "hey series", "series",
        "hey city", "hey cera", "cera", "syri", "hey syri",
        "hey assistant", "assistant"
    )

    private val directCommandKeywords = listOf(
        "turn on flashlight", "turn off flashlight", "flashlight on", "flashlight off",
        "torch on", "torch off", "open ", "launch ", "call ", "set alarm",
        "set timer", "what is the weather", "what's the weather", "play music",
        "stop music", "next song", "previous song", "volume up", "volume down"
    )

    fun start() {
        if (isRunning) return
        isRunning = true

        val accessKey = prefs.getString("porcupine_access_key", "")?.trim().orEmpty()
        val customModelFile = getCustomKeywordFile()

        // If user entered a valid Picovoice AccessKey (base64 string >= 20 chars)
        if (accessKey.length >= 20 && !accessKey.equals("hey siri", ignoreCase = true)) {
            if (customModelFile != null && customModelFile.exists()) {
                initPorcupineCustom(accessKey, customModelFile.absolutePath)
            } else {
                initPorcupineBuiltIn(accessKey)
            }
        } else {
            startCalmVadListener()
        }
    }

    fun pause() {
        stopCalmVadListener()
        stopPorcupine()
        cleanupRecognizer()
    }

    fun resume() {
        if (isRunning && !isListeningCommand) {
            val accessKey = prefs.getString("porcupine_access_key", "")?.trim().orEmpty()
            if (accessKey.length >= 20 && !accessKey.equals("hey siri", ignoreCase = true)) {
                initPorcupineBuiltIn(accessKey)
            } else {
                startCalmVadListener()
            }
        }
    }

    fun stop() {
        isRunning = false
        isListeningCommand = false
        stopPorcupine()
        stopCalmVadListener()
        cleanupRecognizer()
    }

    private fun getCustomKeywordFile(): File? {
        val modelName = "hey_siri_android.ppn"
        val destFile = File(context.filesDir, modelName)
        if (!destFile.exists()) {
            try {
                context.assets.open("porcupine/$modelName").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                return null
            }
        }
        return if (destFile.exists()) destFile else null
    }

    private fun initPorcupineBuiltIn(accessKey: String) {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.HEY_SIRI)
                .setSensitivity(0.7f)
                .build(context) { keywordIndex ->
                    if (keywordIndex >= 0) {
                        mainHandler.post {
                            playActivationChime()
                            triggerWakeWord(null)
                        }
                    }
                }
            porcupineManager?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            startCalmVadListener()
        }
    }

    private fun initPorcupineCustom(accessKey: String, keywordPath: String) {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordPath)
                .setSensitivity(0.7f)
                .build(context) { keywordIndex ->
                    if (keywordIndex >= 0) {
                        mainHandler.post {
                            playActivationChime()
                            triggerWakeWord(null)
                        }
                    }
                }
            porcupineManager?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            startCalmVadListener()
        }
    }

    private fun stopPorcupine() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        porcupineManager = null
    }

    /**
     * Calm Voice Activity Detection:
     * - Dynamically tracks ambient room noise
     * - Requires sustained speech energy (> ambient + 14dB AND >= 55dB)
     * - Remains quiet and dormant during silence (no mic flickering)
     */
    private fun startCalmVadListener() {
        if (vadJob?.isActive == true || isListeningCommand || !isRunning) return

        vadJob = scope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(1024)
                var consecutiveSpeechFrames = 0
                var ambientNoiseDb = -1.0
                val minAbsoluteVoiceDb = 68.0 // Speech loud enough to be intentional voice (room noise is 54-60dB)
                val voiceDeltaOverAmbient = 12.0

                while (isActive && isRunning && !isListeningCommand) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val rmsDb = calculateRmsDb(buffer, read)

                        // Dynamically adapt to room background noise (exponential moving average)
                        if (ambientNoiseDb < 0.0) {
                            if (rmsDb > 20.0) {
                                ambientNoiseDb = rmsDb
                            }
                        } else {
                            // Smoothly track real room ambient floor
                            ambientNoiseDb = 0.95 * ambientNoiseDb + 0.05 * rmsDb
                        }

                        // A voice event must be louder than ambient noise AND above speech volume
                        val isVoicePresent = (rmsDb >= ambientNoiseDb + voiceDeltaOverAmbient) && (rmsDb >= minAbsoluteVoiceDb)

                        if (isVoicePresent) {
                            consecutiveSpeechFrames++
                            Log.i("WakeWordVAD", "Voice frame $consecutiveSpeechFrames/5: rms=$rmsDb, ambient=$ambientNoiseDb")
                            // Require at least 5 consecutive voice frames (~250ms of sustained speech)
                            if (consecutiveSpeechFrames >= 5) {
                                consecutiveSpeechFrames = 0
                                Log.i("WakeWordVAD", "Speech threshold reached! Launching recognition.")
                                stopAudioRecordQuietly()
                                mainHandler.post {
                                    playActivationChime()
                                    startOneShotCommandRecognition()
                                }
                                break
                            }
                        } else {
                            consecutiveSpeechFrames = 0
                        }
                    }
                    delay(40)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopAudioRecordQuietly()
            }
        }
    }

    private fun playActivationChime() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
            mainHandler.postDelayed({
                try { tone.release() } catch (e: Exception) {}
            }, 300)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudioRecordQuietly() {
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
        }
    }

    private fun stopCalmVadListener() {
        vadJob?.cancel()
        vadJob = null
        stopAudioRecordQuietly()
    }

    private fun startOneShotCommandRecognition() {
        if (!isRunning || isListeningCommand) return
        isListeningCommand = true

        mainHandler.post {
            cleanupRecognizer()

            recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }

            val languageCode = try {
                Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
            } catch (e: Exception) {
                "en-US"
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    finishOneShotRecognition(null)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()
                    finishOneShotRecognition(best)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
                    for (kw in wakeKeywords) {
                        if (text.startsWith(kw) || text.contains(kw)) {
                            val remainder = text.substringAfter(kw).replace(Regex("^[,\\s]+"), "").trim()
                            if (remainder.isNotBlank()) {
                                try {
                                    recognizer?.stopListening()
                                } catch (e: Exception) {}
                            }
                            break
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                finishOneShotRecognition(null)
            }
        }
    }

    private fun finishOneShotRecognition(spokenText: String?) {
        cleanupRecognizer()
        isListeningCommand = false
        Log.i("WakeWordVAD", "finishOneShotRecognition result='$spokenText'")

        if (!spokenText.isNullOrBlank()) {
            val lower = spokenText.lowercase(Locale.getDefault()).trim()
            var commandToExecute: String? = null

            // 1. Check if user said "Hey Siri <command>"
            for (kw in wakeKeywords) {
                if (lower.startsWith(kw) || lower.contains(kw)) {
                    val remainder = lower.substringAfter(kw).replace(Regex("^[,\\s]+"), "").trim()
                    commandToExecute = if (remainder.isNotBlank()) remainder else null
                    triggerWakeWord(commandToExecute)
                    return
                }
            }

            // 2. Check if user spoke a direct device command
            for (cmd in directCommandKeywords) {
                if (lower.startsWith(cmd) || lower.contains(cmd)) {
                    triggerWakeWord(spokenText)
                    return
                }
            }

            // 3. User spoke any general statement or command after voice activation
            if (spokenText.length >= 3) {
                triggerWakeWord(spokenText)
                return
            }
        }

        // Return to calm quiet VAD standby with cooldown to avoid re-triggering on echo
        if (isRunning) {
            mainHandler.postDelayed({
                if (isRunning && !isListeningCommand) {
                    startCalmVadListener()
                }
            }, 2000)
        }
    }

    private fun triggerWakeWord(embeddedCommand: String?) {
        val now = System.currentTimeMillis()
        Log.i("WakeWordVAD", "triggerWakeWord command='$embeddedCommand'")
        if (now - lastTriggerTime > debounceMs) {
            lastTriggerTime = now
            mainHandler.post {
                onWakeWordDetected(embeddedCommand)
            }
        }

        // Return to calm standby after execution
        if (isRunning) {
            mainHandler.postDelayed({
                if (isRunning && !isListeningCommand) {
                    startCalmVadListener()
                }
            }, 3000)
        }
    }

    private fun calculateRmsDb(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        val mean = sum / readSize
        val rms = sqrt(mean)
        return if (rms > 0) 20 * log10(rms) else 0.0
    }

    private fun cleanupRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recognizer = null
        }
    }
}
