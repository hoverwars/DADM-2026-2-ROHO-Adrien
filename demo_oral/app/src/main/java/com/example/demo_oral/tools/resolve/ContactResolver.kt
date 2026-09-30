package com.example.demo_oral.tools.resolve

import android.content.Context
import android.provider.ContactsContract
import com.example.demo_oral.tools.ToolRegistry

/** Finds a real phone number for a name said aloud: the model never invents numbers. */
class ContactResolver(private val context: Context) {

    data class Contact(val name: String, val number: String)

    /** The contact's real name if it is found, what was said otherwise (also without the permission). */
    fun displayName(query: String): String = find(query)?.name ?: query

    /** Needs READ_CONTACTS (null without it). A spoken or typed phone number is used as is. */
    fun find(query: String): Contact? = try {
        search(query)
    } catch (_: SecurityException) {
        null
    }

    private fun search(query: String): Contact? {
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 6) return Contact(query.trim(), digits)

        val wanted = ToolRegistry.normalize(query).trim()
        if (wanted.isEmpty()) return null
        var bestScore = 0
        var best: Contact? = null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val number = cursor.getString(1) ?: continue
                val score = score(ToolRegistry.normalize(name).trim(), wanted)
                if (score > bestScore) {
                    bestScore = score
                    best = Contact(name, number)
                }
            }
        }
        return best
    }

    /** Exact name beats a name starting with the query, which beats one containing it. */
    private fun score(name: String, wanted: String) = when {
        name == wanted -> 3
        name.startsWith(wanted) || name.split(' ').any { it == wanted } -> 2
        name.contains(wanted) -> 1
        else -> 0
    }
}
