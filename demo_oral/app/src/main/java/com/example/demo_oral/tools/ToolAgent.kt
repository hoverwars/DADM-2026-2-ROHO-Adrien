package com.example.demo_oral.tools

import android.util.Log
import com.example.demo_oral.llm.RoutedCall

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

        return when (val validation = registry.validate(call.name, call.arguments)) {
            is ToolRegistry.Validation.Invalid -> {
                Log.w(TAG, "Invalid call $call: ${validation.reason}")
                AgentOutcome.NotATool
            }
            is ToolRegistry.Validation.Valid -> prepare(validation.action)
        }
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

    private companion object {
        const val TAG = "ToolAgent"
    }
}
