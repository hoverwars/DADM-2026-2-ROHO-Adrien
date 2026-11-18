package com.example.reto_11_roho_a.tools

import java.text.Normalizer

class ToolRegistry(private val tools: List<Tool>) {

    sealed interface Validation {
        data class Valid(val action: PendingAction) : Validation
        data class Invalid(val reason: String) : Validation

        /** Right tool, but the user has not given every required value yet. */
        data class Missing(val action: PendingAction, val missing: List<ToolParam>) : Validation
    }

    val all: List<Tool> get() = tools

    /** Checks a call made by the model against the tool's declared parameters. */
    fun validate(name: String, arguments: Map<String, Any?>): Validation {
        val tool = tools.firstOrNull { it.spec.name == name } ?: return Validation.Invalid("unknown tool $name")
        val args = ToolArgs(arguments)
        val missing = tool.spec.params.filter { param ->
            param.required && when (param.type) {
                ParamType.STRING -> args.string(param.name) == null
                ParamType.INTEGER -> args.int(param.name) == null
            }
        }
        val action = PendingAction(tool, args)
        return if (missing.isEmpty()) Validation.Valid(action) else Validation.Missing(action, missing)
    }

    companion object {
        /** Lowercase, without accents: " Enciende la LINTERNA!" -> " enciende la linterna!". */
        fun fold(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        /** [fold] and only letters, digits and single spaces: "Enciende la LINTERNA!" -> "enciende la linterna". */
        fun normalize(text: String): String =
            fold(text).replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}
