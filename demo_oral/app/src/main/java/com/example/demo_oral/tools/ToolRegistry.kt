package com.example.demo_oral.tools

import java.text.Normalizer

class ToolRegistry(private val tools: List<Tool>) {

    sealed interface Validation {
        data class Valid(val action: PendingAction) : Validation
        data class Invalid(val reason: String) : Validation
    }

    /**
     * The tools worth showing to the router for this request. A small model gets confused by a long
     * list, and no keyword match means the request is plain conversation: the router is skipped.
     */
    fun candidates(utterance: String, max: Int = MAX_CANDIDATES): List<Tool> {
        val words = normalize(utterance).split(' ').filter(String::isNotEmpty)
        return tools
            // Keywords match the start of a word: "alarm" covers alarma, alarme, alarmas
            .map { tool -> tool to tool.spec.keywords.count { key -> words.any { it.startsWith(key) } } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(max)
            .map { it.first }
    }

    /** Checks a call made by the model against the tool's declared parameters. */
    fun validate(name: String, arguments: Map<String, Any?>): Validation {
        val tool = tools.firstOrNull { it.spec.name == name } ?: return Validation.Invalid("unknown tool $name")
        val args = ToolArgs(arguments)
        for (param in tool.spec.params) {
            val present = when (param.type) {
                ParamType.STRING -> args.string(param.name) != null
                ParamType.INTEGER -> args.int(param.name) != null
            }
            if (param.required && !present) return Validation.Invalid("missing ${param.name}")
        }
        return Validation.Valid(PendingAction(tool, args))
    }

    companion object {
        const val MAX_CANDIDATES = 5

        /** Lowercase, without accents: " Enciende la LINTERNA!" -> " enciende la linterna!". */
        fun fold(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        /** [fold] and only letters, digits and single spaces: "Enciende la LINTERNA!" -> "enciende la linterna". */
        fun normalize(text: String): String =
            fold(text).replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}
