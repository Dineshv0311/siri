package com.siri.assistant

import com.siri.assistant.actions.AssistantIntent
import com.siri.assistant.actions.MediaCommand
import com.siri.assistant.actions.MessagePlatform
import com.siri.assistant.actions.SettingType
import com.siri.assistant.ai.GeminiRepository
import com.siri.assistant.assistant.IntentParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class IntentParserTest {

    private lateinit var mockGeminiRepo: GeminiRepository
    private lateinit var parser: IntentParser

    @Before
    fun setUp() {
        mockGeminiRepo = Mockito.mock(GeminiRepository::class.java)
        parser = IntentParser(mockGeminiRepo)
    }

    @Test
    fun testCallParsing() = runBlocking {
        val result1 = parser.parse("Hey Siri, call Mom")
        assertTrue(result1 is AssistantIntent.MakeCall)
        assertEquals("Mom", (result1 as AssistantIntent.MakeCall).target)

        val result2 = parser.parse("can you call my father please")
        assertTrue(result2 is AssistantIntent.MakeCall)
        assertEquals("father", (result2 as AssistantIntent.MakeCall).target)

        val result3 = parser.parse("call 9876543210")
        assertTrue(result3 is AssistantIntent.MakeCall)
        assertEquals("9876543210", (result3 as AssistantIntent.MakeCall).target)
    }

    @Test
    fun testAppLaunchParsing() = runBlocking {
        val result1 = parser.parse("open Instagram")
        assertTrue(result1 is AssistantIntent.LaunchApp)
        assertEquals("Instagram", (result1 as AssistantIntent.LaunchApp).appName)

        val result2 = parser.parse("Hey Siri, launch Spotify app")
        assertTrue(result2 is AssistantIntent.LaunchApp)
        assertEquals("Spotify", (result2 as AssistantIntent.LaunchApp).appName)
    }

    @Test
    fun testMessagingParsing() = runBlocking {
        val waResult = parser.parse("send a WhatsApp message to Rahul saying I will be late")
        assertTrue(waResult is AssistantIntent.SendMessage)
        val wa = waResult as AssistantIntent.SendMessage
        assertEquals(MessagePlatform.WHATSAPP, wa.platform)
        assertEquals("Rahul", wa.recipient)
        assertEquals("I will be late", wa.message)

        val smsResult = parser.parse("send an SMS to Mom saying I am coming home")
        assertTrue(smsResult is AssistantIntent.SendMessage)
        val sms = smsResult as AssistantIntent.SendMessage
        assertEquals(MessagePlatform.SMS, sms.platform)
        assertEquals("Mom", sms.recipient)
        assertEquals("I am coming home", sms.message)
    }

    @Test
    fun testSettingsParsing() = runBlocking {
        val flashOn = parser.parse("turn on flashlight")
        assertTrue(flashOn is AssistantIntent.ControlSetting)
        assertEquals(SettingType.FLASHLIGHT_ON, (flashOn as AssistantIntent.ControlSetting).setting)

        val flashOff = parser.parse("torch off")
        assertTrue(flashOff is AssistantIntent.ControlSetting)
        assertEquals(SettingType.FLASHLIGHT_OFF, (flashOff as AssistantIntent.ControlSetting).setting)

        val wifiOn = parser.parse("turn on Wi-Fi")
        assertTrue(wifiOn is AssistantIntent.ControlSetting)
        assertEquals(SettingType.WIFI_ON, (wifiOn as AssistantIntent.ControlSetting).setting)

        val brightness = parser.parse("set brightness to 75%")
        assertTrue(brightness is AssistantIntent.ControlSetting)
        assertEquals(SettingType.BRIGHTNESS_SET, (brightness as AssistantIntent.ControlSetting).setting)
        assertEquals(75, (brightness as AssistantIntent.ControlSetting).value)
    }

    @Test
    fun testAlarmAndTimerParsing() = runBlocking {
        val alarm = parser.parse("set an alarm for 6 AM")
        assertTrue(alarm is AssistantIntent.SetAlarm)
        assertEquals(6, (alarm as AssistantIntent.SetAlarm).hour)
        assertEquals(0, (alarm as AssistantIntent.SetAlarm).minute)

        val timer = parser.parse("set a timer for 10 minutes")
        assertTrue(timer is AssistantIntent.SetTimer)
        assertEquals(600, (timer as AssistantIntent.SetTimer).durationSeconds)
    }

    @Test
    fun testMediaAndNotificationParsing() = runBlocking {
        val play = parser.parse("play music")
        assertTrue(play is AssistantIntent.ControlMedia)
        assertEquals(MediaCommand.PLAY, (play as AssistantIntent.ControlMedia).command)

        val next = parser.parse("next song")
        assertTrue(next is AssistantIntent.ControlMedia)
        assertEquals(MediaCommand.NEXT, (next as AssistantIntent.ControlMedia).command)

        val notif = parser.parse("read my notifications")
        assertTrue(notif is AssistantIntent.ReadNotifications)
    }
}
