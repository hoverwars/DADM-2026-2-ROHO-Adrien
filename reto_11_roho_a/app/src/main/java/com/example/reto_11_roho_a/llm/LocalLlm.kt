package com.example.reto_11_roho_a.llm

import android.content.Context
import android.util.Log
import com.example.reto_11_roho_a.tools.ParamType
import com.example.reto_11_roho_a.tools.ToolSpec
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One message of the conversation, as the LLM sees it. */
data class Turn(val fromUser: Boolean, val text: String)

/** A tool call chosen by the LLM, not yet validated. */
data class RoutedCall(val name: String, val arguments: Map<String, Any?>)

/** What the LLM answered: [text] (streamed while it was generated), and/or a tool [call]. */
data class LlmReply(val text: String, val call: RoutedCall?)

/**
 * The one on-device LLM of the app: Gemma 4 E2B running through LiteRT-LM. It both talks with the
 * user and decides when a request is an action of the phone (native function calling).
 *
 * The model only *chooses* the tools (they are declared to it, never run by the runtime): the
 * [com.example.reto_11_roho_a.tools.ToolAgent] validates and executes them.
 *
 * Every reply is generated from the whole conversation in a fresh [com.google.ai.edge.litertlm.Conversation]:
 * the model stays stateless, and the oldest turns can be dropped when the context window is full.
 *
 * Only one [reply] runs at a time.
 */
class LocalLlm(context: Context) : AutoCloseable {

    private val engine: Engine
    private val mutex = Mutex()

    init {
        val model = modelFile(context)
        if (!model.exists()) throw FileNotFoundException("LLM not found: $model (run run.bat)")
        val start = System.currentTimeMillis()
        engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                // CPU on purpose: on the Pixel's Mali GPU, Gemma 4 corrupts numbers and some tokens
                // in tool-call arguments ("7:30" -> "7:3:0"), which the CPU backend does not do
                backend = Backend.CPU(),
                maxNumTokens = MAX_TOKENS,
                cacheDir = context.cacheDir.absolutePath,
            )
        )
        engine.initialize()
        Log.i(TAG, "LLM loaded in ${System.currentTimeMillis() - start} ms")
    }

    /**
     * Answers the last message of [history] (which must be from the user). [tools] are the actions
     * the model may call for this message, none for a plain conversation. [onText] receives the
     * whole answer so far each time it grows.
     */
    suspend fun reply(history: List<Turn>, tools: List<ToolSpec>, onText: (String) -> Unit): LlmReply =
        mutex.withLock {
            withContext(Dispatchers.Default) {
                val turns = fit(history)
                val config = ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt()),
                    initialMessages = turns.dropLast(1).map {
                        if (it.fromUser) Message.user(it.text) else Message.model(it.text)
                    },
                    tools = tools.map { tool(DeclaredTool(it)) },
                    // We execute the tools ourselves: validation, permissions and confirmation come first
                    automaticToolCalling = false,
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = if (tools.isEmpty()) 0.7 else 0.2),
                )
                val start = System.currentTimeMillis()
                val answer = StringBuilder()
                var call: RoutedCall? = null
                val conversation = engine.createConversation(config)
                try {
                    conversation.sendMessageAsync(turns.last().text).collect { message ->
                        message.toolCalls.firstOrNull()?.let { if (call == null) call = RoutedCall(it.name, it.arguments) }
                        val chunk = message.toString()
                        if (chunk.isNotEmpty()) {
                            answer.append(chunk)
                            onText(clean(answer))
                        }
                    }
                } catch (e: CancellationException) {
                    // Stop generating before the conversation is closed
                    runCatching { conversation.cancelProcess() }
                    throw e
                } finally {
                    conversation.close()
                }
                val text = clean(answer)
                Log.i(TAG, "\"${turns.last().text}\" -> ${call ?: "\"$text\""} in ${System.currentTimeMillis() - start} ms " +
                    "(tools: ${tools.joinToString { it.name }})")
                LlmReply(text, call)
            }
        }

    /** The most recent turns that fit the context window, always at least the last message. */
    private fun fit(history: List<Turn>): List<Turn> {
        var first = history.lastIndex
        var size = history.last().text.length
        while (first > 0 && size + history[first - 1].text.length <= HISTORY_BUDGET_CHARS) {
            first--
            size += history[first].text.length
        }
        // The conversation must start with a user message
        while (first < history.lastIndex && !history[first].fromUser) first++
        return history.subList(first, history.size)
    }

    private fun clean(answer: CharSequence) = answer.toString().trim()

    override fun close() = engine.close()

    /** A tool only declared to the model: it is never run by the runtime. */
    private class DeclaredTool(private val spec: ToolSpec) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = JSONObject().apply {
            put("name", spec.name)
            put("description", spec.description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    for (param in spec.params) {
                        put(param.name, JSONObject().apply {
                            put("type", if (param.type == ParamType.INTEGER) "integer" else "string")
                            put("description", param.description)
                        })
                    }
                })
                put("required", JSONArray(spec.params.filter { it.required }.map { it.name }))
            })
        }.toString()

        override fun execute(paramsJsonString: String): String =
            error("${spec.name} must be executed by the ToolAgent")
    }

    companion object {
        private const val TAG = "LocalLlm"

        /** Where run.bat pushes the model (the APK is far too small for it). */
        fun modelFile(context: Context) = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)

        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

        // Prompt (tool declarations included) + answer must fit in this many tokens
        private const val MAX_TOKENS = 4096

        // What the conversation may take of it: about 3 characters per token, the rest is for the
        // system prompt, the tools and the answer
        private const val HISTORY_BUDGET_CHARS = 6000

        private fun systemPrompt(): String {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale("es", "ES")))
            return "You are a friendly assistant living in the user's Android phone. " +
                "ALWAYS answer in Spanish, whatever language the user writes in. " +
                "Now it is $now. " +
                "Whenever the user asks you to do something on the phone, you MUST do it by calling " +
                "the corresponding function if one exists, including the opposite of an action " +
                "(turning something off, for instance): never answer in words that you cannot do " +
                "it, and never just say you did it. Only fill in the values the user actually " +
                "said: never invent a value, leave it out instead. " +
                "Only if no function matches, or if the user just chats or asks a question, " +
                "answer normally. " +
                "Never claim to have done something on the phone unless you called a function for it. " +
                "Never invent facts. Keep answers short (one to three sentences), in plain text: " +
                "no markdown, no lists, no emojis."
        }
    }
}
