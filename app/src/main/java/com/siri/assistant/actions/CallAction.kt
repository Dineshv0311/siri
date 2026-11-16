package com.siri.assistant.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.siri.assistant.contacts.ContactInfo
import com.siri.assistant.contacts.ContactRepository

class CallAction(
    private val context: Context,
    private val contactRepository: ContactRepository
) {

    fun execute(target: String, directNumber: String? = null, confirmed: Boolean = false): ActionResult {
        // If direct number is supplied or target itself is a phone number
        val rawNumber = directNumber ?: if (target.replace(Regex("[^0-9+]"), "").length >= 7) target else null

        if (rawNumber != null) {
            return placeCall(targetName = rawNumber, phoneNumber = rawNumber)
        }

        // Check contacts permission
        val hasContactPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactPermission) {
            return ActionResult(
                success = false,
                spokenResponse = "I need permission to access your contacts to call $target. Please grant it in settings.",
                uiDetails = "Missing READ_CONTACTS permission"
            )
        }

        val matches = contactRepository.findContactByRelationshipOrName(target)

        return when {
            matches.isEmpty() -> {
                ActionResult(
                    success = false,
                    spokenResponse = "I couldn't find $target in your contacts. What number should I call?",
                    uiDetails = "No contact found for: $target"
                )
            }
            matches.size == 1 -> {
                val contact = matches[0]
                placeCall(targetName = contact.name, phoneNumber = contact.phoneNumber)
            }
            else -> {
                // Disambiguate multiple matches
                val names = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                ActionResult(
                    success = false,
                    spokenResponse = "I found multiple matches for $target: $names. Which one would you like to call?",
                    uiDetails = "Multiple matches found: $names",
                    requiresFollowUp = true,
                    pendingIntent = AssistantIntent.DisambiguateContact(
                        choices = matches,
                        originalIntent = AssistantIntent.MakeCall(target = target)
                    )
                )
            }
        }
    }

    private fun placeCall(targetName: String, phoneNumber: String): ActionResult {
        val cleanNumber = phoneNumber.replace(Regex("[\\s-()]"), "")
        val uri = Uri.parse("tel:$cleanNumber")

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            // Default safe approach requested in Section 3
            Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            ActionResult(
                success = true,
                spokenResponse = "Calling $targetName.",
                uiDetails = "Placed call to $phoneNumber via ${if (hasCallPermission) "ACTION_CALL" else "ACTION_DIAL"}"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                spokenResponse = "Sorry, I couldn't place the call to $targetName.",
                uiDetails = "Error starting call activity: ${e.message}"
            )
        }
    }
}
