package com.example.demo_oral.tools.resolve

import com.example.demo_oral.tools.ToolRegistry

/** Understands the answer to "are you sure?", with plain rules: the LLM is not involved. */
object Confirmation {
    enum class Answer { YES, NO, UNKNOWN }

    fun parse(text: String): Answer {
        val words = ToolRegistry.normalize(text).split(' ').filter(String::isNotEmpty).toSet()
        val yes = words.any { it in YES_WORDS }
        val no = words.any { it in NO_WORDS }
        return when {
            yes && !no -> Answer.YES
            no && !yes -> Answer.NO
            else -> Answer.UNKNOWN
        }
    }

    /**
     * "Cancel", "never mind"... while the assistant waits for a missing value. Narrower than a "no":
     * "no, on friday" is a correction, not a cancellation.
     */
    fun isCancel(text: String): Boolean {
        val normalized = ToolRegistry.normalize(text)
        val words = normalized.split(' ').toSet()
        return words.any { it in CANCEL_WORDS } || CANCEL_PHRASES.any { normalized.contains(it) }
    }

    private val CANCEL_WORDS = setOf("cancel", "cancela", "cancelar", "cancelalo", "annule", "stop", "olvidalo", "olvida", "dejalo")
    private val CANCEL_PHRASES = listOf("never mind", "nevermind", "forget it", "forget about it", "no importa", "da igual", "olvidate", "ya no")

    private val YES_WORDS = setOf(
        "si", "vale", "ok", "okay", "claro", "adelante", "confirmo", "dale", "perfecto", "yes", "yeah",
        "sure", "oui", "accord", "yep", "yup",
    )
    private val NO_WORDS = setOf(
        "no", "cancela", "cancelar", "cancelalo", "nada", "para", "non", "nope", "annule", "stop",
        "cancel",
    )
}
