package com.example.demo_oral.tools

enum class ParamType { STRING, INTEGER }

/** One argument of a tool. Descriptions are read by the router model, so they are in English. */
data class ToolParam(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    /** Asked aloud when the user did not give this required value. Not shown to the router. */
    val ask: String? = null,
)

/** How much the user must be involved before a tool runs. */
enum class Risk {
    /** Runs right away */
    SAFE,

    /** Has a cost or cannot be undone (SMS, call): the user confirms first */
    CONFIRM,
}

/**
 * What the router model sees of a tool. Same shape as an MCP tool (name, description, JSON-schema
 * inputs), so real MCP servers could be plugged in later.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<ToolParam> = emptyList(),
    val risk: Risk = Risk.SAFE,
    /** Lowercase, accent-free word starts (any language) that make this tool a candidate for a request */
    val keywords: List<String>,
    /** Android runtime permissions needed to run */
    val permissions: List<String> = emptyList(),
)

/** Arguments of a call, as produced by the model (values may be strings, numbers...). */
class ToolArgs(val raw: Map<String, Any?>) {
    fun string(name: String): String? = raw[name]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    fun int(name: String): Int? = when (val value = raw[name]) {
        is Number -> value.toInt()
        is String -> value.trim().toDoubleOrNull()?.toInt()
        else -> null
    }

    override fun toString() = raw.toString()
}

sealed interface ToolResult {
    /** [spoken] is what the assistant says to the user. */
    data class Success(val spoken: String) : ToolResult
    data class Failed(val spoken: String) : ToolResult
}

interface Tool {
    val spec: ToolSpec

    /** Checked before asking for confirmation, so the user is not asked about something impossible. */
    suspend fun preflight(args: ToolArgs): ToolResult.Failed? = null

    /**
     * What the tool is about to do, for the conversation: built from the values the tool will
     * really use (contact found, time parsed...), which is what the user checks.
     */
    fun card(args: ToolArgs): ToolCard

    /** The question asked aloud to confirm the action. Only for [Risk.CONFIRM]. */
    fun describe(args: ToolArgs): String = spec.name

    suspend fun execute(args: ToolArgs): ToolResult
}

/** A validated call, ready to run. */
data class PendingAction(val tool: Tool, val args: ToolArgs)

enum class ToolIcon { ALARM, TIMER, FLASHLIGHT, BATTERY, VOLUME, CALENDAR, SMS, CALL }

/** What an action looks like in the conversation, independent of how it is drawn. */
data class ToolCard(
    val icon: ToolIcon,
    val title: String,
    /** Label and value of each thing the user should check, e.g. ("Para", "Marie") */
    val fields: List<Pair<String, String>> = emptyList(),
)

enum class ToolStatus {
    AWAITING_INFO,
    AWAITING_PERMISSION,
    AWAITING_CONFIRMATION,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED,
    DENIED,
}

/** An action shown in the conversation, and how far it got. */
data class ToolAction(val card: ToolCard, val status: ToolStatus, val risky: Boolean)
