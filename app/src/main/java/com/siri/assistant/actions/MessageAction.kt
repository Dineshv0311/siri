package com.siri.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.siri.assistant.contacts.ContactRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MessageAction(
    private val context: Context,
    private val contactRepository: ContactRepository
) {

    fun execute(
        platform: MessagePlatform,
        recipient: String,
        message: String,
        confirmed: Boolean = false
    ): ActionResult {
        if (message.isBlank()) {
            return ActionResult(
                success = false,
                spokenResponse = "What message would you like to send to $recipient?",
                requiresFollowUp = true,
                pendingIntent = AssistantIntent.SendMessage(
                    platform = platform,
                    recipient = recipient,
                    message = ""
                )
            )
        }

        // Contact lookup
        val matches = contactRepository.findContactByRelationshipOrName(recipient)
        val phoneNumber = if (matches.isNotEmpty()) {
            matches[0].phoneNumber
        } else if (recipient.replace(Regex("[^0-9+]"), "").length >= 7) {
            recipient
        } else {
            null
        }

        val displayName = if (matches.isNotEmpty()) matches[0].name else recipient

        // Confirmation requirement before dispatching
        if (!confirmed) {
            val appName = if (platform == MessagePlatform.WHATSAPP) "WhatsApp" else "SMS"
            return ActionResult(
                success = true,
                spokenResponse = "Ready to send via $appName to $displayName: \"$message\". Should I send it?",
                uiDetails = "Drafted $appName message to $displayName ($phoneNumber): \"$message\"",
                requiresFollowUp = true,
                pendingIntent = AssistantIntent.SendMessage(
                    platform = platform,
                    recipient = displayName,
                    message = message,
                    confirmed = true
                )
            )
        }

        return when (platform) {
            MessagePlatform.WHATSAPP -> sendWhatsApp(phoneNumber, displayName, message)
            MessagePlatform.SMS -> sendSms(phoneNumber, displayName, message)
        }
    }

    private fun sendWhatsApp(phoneNumber: String?, displayName: String, message: String): ActionResult {
        return try {
            val encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (phoneNumber != null) {
                    val digits = phoneNumber.replace(Regex("[^0-9]"), "")
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=$encodedMsg")
                } else {
                    data = Uri.parse("whatsapp://send?text=$encodedMsg")
                }
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if WhatsApp is installed
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    spokenResponse = "Opening WhatsApp to send your message to $displayName.",
                    uiDetails = "WhatsApp opened with prepared message for $displayName"
                )
            } else {
                ActionResult(
                    success = false,
                    spokenResponse = "WhatsApp does not appear to be installed on your device.",
                    uiDetails = "WhatsApp package not found"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Failed to launch WhatsApp.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }

    private fun sendSms(phoneNumber: String?, displayName: String, message: String): ActionResult {
        return try {
            val uri = if (phoneNumber != null) {
                val clean = phoneNumber.replace(Regex("[\\s-()]"), "")
                Uri.parse("smsto:$clean")
            } else {
                Uri.parse("smsto:")
            }

            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    spokenResponse = "Opening SMS to send your message to $displayName.",
                    uiDetails = "SMS composer opened for $displayName"
                )
            } else {
                ActionResult(
                    success = false,
                    spokenResponse = "No SMS messaging app found on your phone.",
                    uiDetails = "No SMS activity resolved"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Could not open messaging app.",
                uiDetails = "Error: ${e.message}"
            )
        }
    }
}
