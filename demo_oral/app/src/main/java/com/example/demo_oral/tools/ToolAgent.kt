package com.example.demo_oral.tools

import android.util.Log
import com.example.demo_oral.llm.RoutedCall
import com.example.demo_oral.tools.resolve.TimeExpressions

sealed interface AgentOutcome {
    /** Not a request for an action: answer it as a normal conversation. */
    data object NotATool : AgentOutcome

    /** The action ran (or could not run): [text] is what to say about it. */
    data class Done(val text: String, val card: ToolCard, val success: Boolean, val risky: Boolean) : AgentOutcome

    /** The user must confirm before [action] runs. */
    data class Confirm(val question: String, val action: PendingAction, val card: ToolCard) : AgentOutcome

    /** Android permissions are missing; ask for them, then call [ToolAgent.prepare] again. */
    data class NeedsPermission(
        val permissions: List<String>,
        val action: PendingAction,
        val card: ToolCard,
    ) : AgentOutcome

    /** A tool crashed. */
    data class Error(val card: ToolCard, val risky: Boolean) : AgentOutcome

    /**
     * Some required values of [action] were not said: [question] asks for the first one. Keep
     * [action] and hand the answer to [ToolAgent.fill].
     */
    data class NeedsInfo(
        val question: String,
        val action: PendingAction,
        val missing: List<ToolParam>,
        val card: ToolCard,
    ) : AgentOutcome
}

/** What the answer to a [AgentOutcome.NeedsInfo] question brought. */
sealed interface FillResult {
    /** New values: go on with [outcome] (which may ask for the next missing value). */
    data class Progress(val outcome: AgentOutcome) : FillResult

    /** Nothing usable: the user moved on to something else. */
    data object NoProgress : FillResult
}

/**
 * From a sentence to an executed action, in four steps the small model cannot get wrong:
 * 1. [ToolRegistry.candidates] keeps the few tools that could match (none: plain conversation),
 * 2. the router picks one and fills its arguments,
 * 3. the call is validated against the tool's declared parameters,
 * 4. permissions and confirmation are dealt with before the tool runs.
 */
class ToolAgent(
    private val registry: ToolRegistry,
    private val route: suspend (utterance: String, candidates: List<ToolSpec>) -> RoutedCall?,
    private val hasPermission: (String) -> Boolean,
) {

    suspend fun handle(utterance: String): AgentOutcome {
        val candidates = registry.candidates(utterance)
        if (candidates.isEmpty()) return AgentOutcome.NotATool
        val call = route(utterance, candidates.map { it.spec }) ?: return AgentOutcome.NotATool
        if (candidates.none { it.spec.name == call.name }) return AgentOutcome.NotATool

        val spec = candidates.first { it.spec.name == call.name }.spec
        return outcomeOf(spec, grounded(call.arguments, spec, utterance))
    }

    /** Whether [utterance] is about another tool than [tool]: a new request, not an answer. */
    fun asksForAnother(utterance: String, tool: Tool) = registry.asksForAnother(utterance, tool)

    /**
     * Completes [action] with [answer], the reply to its question. The router reads the whole
     * exchange ([request] then [answer]) but only values said in [answer] are taken, so it cannot
     * invent them. When it finds nothing and a single text value is missing, the answer itself is
     * that value ("Dentist" as the event title).
     */
    suspend fun fill(action: PendingAction, missing: List<ToolParam>, request: String, answer: String): FillResult {
        val spec = action.tool.spec
        val call = route("$request $answer", listOf(spec))?.takeIf { it.name == spec.name }
        val said = call?.let { grounded(it.arguments, spec, answer) }.orEmpty()
            .filter { (name, value) -> action.args.raw[name]?.toString() != value?.toString() }
        val new = said.ifEmpty { rawAnswer(missing, answer) }
        if (new.isEmpty()) return FillResult.NoProgress
        Log.i(TAG, "Answer \"$answer\" gives $new")
        return FillResult.Progress(outcomeOf(spec, action.args.raw + new))
    }

    /** The values of [arguments] the user really said in [utterance]; unknown names are dropped. */
    private fun grounded(arguments: Map<String, Any?>, spec: ToolSpec, utterance: String) =
        arguments.filter { (name, value) ->
            val param = spec.params.firstOrNull { it.name == name } ?: return@filter false
            val said = param.type != ParamType.STRING || isSaid(value.toString(), utterance)
            if (!said) Log.w(TAG, "Dropped $name=\"$value\": the user never said it")
            said
        }

    private fun rawAnswer(missing: List<ToolParam>, answer: String): Map<String, Any?> {
        val param = missing.singleOrNull()?.takeIf { it.type == ParamType.STRING } ?: return emptyMap()
        val text = answer.trim().trimEnd('.', '!', ',', ' ')
        // "What's the weather like?" is not an event title: a question means the user moved on
        val question = text.endsWith('?') ||
            ToolRegistry.normalize(text).substringBefore(' ') in QUESTION_WORDS
        return if (text.isEmpty() || question) emptyMap() else mapOf(param.name to text)
    }

    private suspend fun outcomeOf(spec: ToolSpec, arguments: Map<String, Any?>): AgentOutcome =
        when (val validation = registry.validate(spec.name, arguments)) {
            is ToolRegistry.Validation.Invalid -> {
                Log.w(TAG, "Invalid call ${spec.name} $arguments: ${validation.reason}")
                AgentOutcome.NotATool
            }
            is ToolRegistry.Validation.Missing -> {
                val action = validation.action
                val question = validation.missing.first().ask
                // A tool that cannot ask for a value is left to the conversation, as before
                if (question == null) AgentOutcome.NotATool
                else AgentOutcome.NeedsInfo(question, action, validation.missing, action.tool.card(action.args))
            }
            is ToolRegistry.Validation.Valid -> prepare(validation.action)
        }

    /** Permissions, then pre-checks, then confirmation if the tool needs it, then the tool itself. */
    suspend fun prepare(action: PendingAction): AgentOutcome {
        val card = action.tool.card(action.args)
        val missing = action.tool.spec.permissions.filterNot(hasPermission)
        if (missing.isNotEmpty()) return AgentOutcome.NeedsPermission(missing, action, card)
        action.tool.preflight(action.args)?.let {
            return AgentOutcome.Done(it.spoken, card, success = false, risky = action.risky)
        }
        if (action.tool.spec.risk == Risk.CONFIRM) {
            return AgentOutcome.Confirm(action.tool.describe(action.args), action, card)
        }
        return execute(action)
    }

    /** Runs the tool (the user confirmed, or no confirmation is needed). */
    suspend fun execute(action: PendingAction): AgentOutcome {
        val card = action.tool.card(action.args)
        return try {
            when (val result = action.tool.execute(action.args)) {
                is ToolResult.Success -> AgentOutcome.Done(result.spoken, card, success = true, risky = action.risky)
                is ToolResult.Failed -> AgentOutcome.Done(result.spoken, card, success = false, risky = action.risky)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool ${action.tool.spec.name} crashed", e)
            AgentOutcome.Error(card, action.risky)
        }
    }

    private val PendingAction.risky get() = tool.spec.risk == Risk.CONFIRM

    /**
     * The small router invents missing arguments ("Meeting", "tomorrow") instead of leaving them
     * out. A text value is kept only if it comes from the sentence: one of its words is in it, or,
     * for a value with digits ("15:30"), the sentence holds a time ("half past three").
     */
    private fun isSaid(value: String, utterance: String): Boolean {
        val said = ToolRegistry.normalize(utterance).split(' ').toSet()
        val tokens = ToolRegistry.normalize(value).split(' ').filter { it.length > 2 || it.any(Char::isDigit) }
        if (tokens.any { it in said }) return true
        return value.any(Char::isDigit) && TimeExpressions.parseTime(utterance) != null
    }

    private companion object {
        const val TAG = "ToolAgent"
        val QUESTION_WORDS = setOf(
            "what", "who", "why", "how", "when", "where", "which", "can", "could", "would", "do", "does",
            "is", "are",
            "que", "quien", "como", "cuando", "donde", "cual", "cuanto", "cuantos", "por", "puedes",
            "podrias", "sabes",
        )
    }
}
