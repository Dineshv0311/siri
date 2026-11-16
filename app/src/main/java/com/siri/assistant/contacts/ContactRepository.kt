package com.siri.assistant.contacts

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import java.util.Locale

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String
)

class ContactRepository(private val context: Context) {

    /**
     * Resolves a contact query. Returns all matching contacts (for disambiguation if multiple).
     */
    fun findContacts(query: String): List<ContactInfo> {
        val results = mutableListOf<ContactInfo>()
        val cleanQuery = query.trim().lowercase(Locale.getDefault())

        if (cleanQuery.isEmpty()) return results

        // If query itself is a valid phone number (contains mostly digits)
        val digitsOnly = cleanQuery.replace(Regex("[^0-9+]"), "")
        if (digitsOnly.length >= 7) {
            return listOf(ContactInfo(id = "-1", name = digitsOnly, phoneNumber = digitsOnly))
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$cleanQuery%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            val seenNumbers = mutableSetOf<String>()
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else ""
                    val rawNumber = if (numberIdx >= 0) it.getString(numberIdx) else ""
                    val normalizedNumber = rawNumber.replace(Regex("[\\s-()]"), "")

                    if (normalizedNumber.isNotEmpty() && !seenNumbers.contains(normalizedNumber)) {
                        seenNumbers.add(normalizedNumber)
                        results.add(ContactInfo(id = id, name = name, phoneNumber = rawNumber))
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        // If exact matches exist, prioritize them
        val exactMatches = results.filter { it.name.equals(cleanQuery, ignoreCase = true) }
        return if (exactMatches.isNotEmpty()) exactMatches else results
    }

    /**
     * Resolves relationships like "Mom", "Dad", "Mother", "Father" by looking for
     * saved contacts or contacts with nickname/notes, or exact names.
     */
    fun findContactByRelationshipOrName(query: String): List<ContactInfo> {
        val matches = findContacts(query)
        if (matches.isNotEmpty()) return matches

        // Fallback for common nicknames
        val alternates = when (query.lowercase(Locale.getDefault())) {
            "mom", "mother" -> listOf("mom", "mother", "mummy", "maa", "amma")
            "dad", "father" -> listOf("dad", "father", "papa", "appa", "daddy")
            "wife" -> listOf("wife", "honey", "wifey", "darling")
            "husband" -> listOf("husband", "hubby")
            "brother" -> listOf("brother", "bro")
            "sister" -> listOf("sister", "sis")
            else -> emptyList()
        }

        for (alt in alternates) {
            val altMatches = findContacts(alt)
            if (altMatches.isNotEmpty()) return altMatches
        }

        return emptyList()
    }
}
