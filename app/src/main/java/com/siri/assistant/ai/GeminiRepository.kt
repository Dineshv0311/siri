package com.siri.assistant.ai

import android.content.Context
import android.content.SharedPreferences
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("siri_assistant_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "").orEmpty()
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    /**
     * Answers general knowledge, internet search, and conversational queries using Gemini.
     */
    suspend fun askGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "To enable natural language Q&A, please add your Gemini API key in Siri settings."
        }

        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = com.google.ai.client.generativeai.type.content {
                    text(
                        "You are an intelligent voice assistant running natively on Android. " +
                        "Provide concise, accurate, direct, and conversational responses suitable for Text-To-Speech. " +
                        "Keep responses under 2-3 sentences unless an explanation is explicitly requested. " +
                        "Avoid markdown tables, asterisks, or formatting that sounds awkward when spoken aloud."
                    )
                }
            )

            val response = generativeModel.generateContent(prompt)
            val reply = response.text?.trim()
            if (!reply.isNullOrEmpty()) {
                reply
            } else {
                "I couldn't find an answer to that."
            }
        } catch (e: Exception) {
            "I'm having trouble connecting to the internet right now: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Uses Gemini to parse complex natural voice commands into structured JSON.
     */
    suspend fun parseIntentWithGemini(userUtterance: String): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext null

        try {
            val nluModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = com.google.ai.client.generativeai.type.content {
                    text(
                        "You are a command parser for an Android voice assistant. Analyze the user utterance and output ONLY a valid JSON object matching this schema:\n" +
                        "{\n" +
                        "  \"intent\": \"MAKE_CALL\" | \"SEND_WHATSAPP\" | \"SEND_SMS\" | \"OPEN_APP\" | \"CONTROL_SETTING\" | \"SET_ALARM\" | \"SET_TIMER\" | \"READ_NOTIFICATIONS\" | \"CONTROL_MEDIA\" | \"GET_WEATHER\" | \"GENERAL_QA\",\n" +
                        "  \"target\": string or null,\n" +
                        "  \"message\": string or null,\n" +
                        "  \"app_name\": string or null,\n" +
                        "  \"setting_type\": string or null,\n" +
                        "  \"setting_value\": number or null,\n" +
                        "  \"time_hour\": number or null,\n" +
                        "  \"time_minute\": number or null,\n" +
                        "  \"timer_seconds\": number or null,\n" +
                        "  \"media_command\": string or null,\n" +
                        "  \"query\": string or null\n" +
                        "}\n" +
                        "Output NO explanation or markdown backticks, only raw JSON."
                    )
                }
            )

            val response = nluModel.generateContent(userUtterance)
            response.text?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches live weather data from open-meteo without requiring API keys.
     */
    suspend fun getLiveWeather(locationName: String? = null): String = withContext(Dispatchers.IO) {
        try {
            // Default to Delhi or dynamic coordinates
            val url = "https://api.open-meteo.com/v1/forecast?latitude=28.6139&longitude=77.2090&current_weather=true"
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Unable to fetch current weather data."
                val body = response.body?.string() ?: return@withContext "Empty weather response."
                val json = JSONObject(body)
                val current = json.getJSONObject("current_weather")
                val temp = current.getDouble("temperature")
                val wind = current.getDouble("windspeed")
                val weatherCode = current.getInt("weathercode")

                val condition = when (weatherCode) {
                    0 -> "clear skies"
                    1, 2, 3 -> "partly cloudy"
                    45, 48 -> "foggy"
                    51, 53, 55, 61, 63, 65 -> "rainy"
                    71, 73, 75 -> "snowy"
                    95, 96, 99 -> "thunderstorms"
                    else -> "fair"
                }

                val loc = locationName ?: "your area"
                "Right now in $loc, it's ${temp.toInt()} degrees Celsius with $condition and winds around ${wind.toInt()} kilometers per hour."
            }
        } catch (e: Exception) {
            "I couldn't retrieve the live weather at this moment."
        }
    }
}
