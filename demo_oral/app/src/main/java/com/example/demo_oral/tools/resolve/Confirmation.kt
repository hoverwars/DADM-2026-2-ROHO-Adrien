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

    private val YES_WORDS = setOf(
        "si", "vale", "ok", "okay", "claro", "adelante", "confirmo", "dale", "perfecto", "yes", "yeah",
        "sure", "oui", "accord",
    )
    private val NO_WORDS = setOf(
        "no", "cancela", "cancelar", "cancelalo", "nada", "para", "non", "nope", "annule", "stop",
    )
}
