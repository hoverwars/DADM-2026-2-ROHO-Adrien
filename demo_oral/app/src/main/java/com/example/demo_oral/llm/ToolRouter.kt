package com.example.demo_oral.llm

import android.content.Context
import android.util.Log
import com.example.demo_oral.tools.ParamType
import com.example.demo_oral.tools.ToolSpec
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/** A tool call chosen by the router, not yet validated. */
data class RoutedCall(val name: String, val arguments: Map<String, Any?>)

/**
 * Decides whether a request is a tool call, and which one, with FunctionGemma 270M (fine-tuned
 * for phone actions) running through LiteRT-LM. The model is trained to answer with a strict
 * function-call grammar, so its output is parsed by the runtime, never by us.
 *
 * It only chooses: the tools are executed by [com.example.demo_oral.tools.ToolAgent].
 */
class ToolRouter(context: Context) : AutoCloseable {

    private val engine: Engine
    private val mutex = Mutex() // One conversation at a time

    init {
        val model = modelFile(context)
        if (!model.exists()) throw FileNotFoundException("Router model not found: $model (run run.bat)")
        val start = System.currentTimeMillis()
        engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = MAX_TOKENS,
                cacheDir = context.cacheDir.absolutePath,
            )
        )
        engine.initialize()
        Log.i(TAG, "Router loaded in ${System.currentTimeMillis() - start} ms")
    }

    /** The call the model makes for [utterance] among [candidates], or null if it makes none. */
    suspend fun route(utterance: String, candidates: List<ToolSpec>): RoutedCall? = mutex.withLock {
        withContext(Dispatchers.Default) {
            val config = ConversationConfig(
                systemInstruction = Contents.of(SYSTEM_PROMPT),
                tools = candidates.map { tool(DeclaredTool(it)) },
                // We execute the tools ourselves: validation, permissions and confirmation come first
                automaticToolCalling = false,
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
            )
            val start = System.currentTimeMillis()
            val call = engine.createConversation(config).use { conversation ->
                conversation.sendMessage(utterance).toolCalls.firstOrNull()
            }
            Log.i(TAG, "\"$utterance\" -> ${call ?: "no call"} in ${System.currentTimeMillis() - start} ms " +
                "(candidates: ${candidates.joinToString { it.name }})")
            call?.let { RoutedCall(it.name, it.arguments) }
        }
    }

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

        override fun execute(paramsJson: String): String =
            error("${spec.name} must be executed by the ToolAgent")
    }

    companion object {
        private const val TAG = "ToolRouter"
        private const val MAX_TOKENS = 1024

        // FunctionGemma only switches to function calling with this exact developer prompt
        private const val SYSTEM_PROMPT = "You are a model that can do function calling with the following functions"

        /** Where run.bat pushes the model. */
        fun modelFile(context: Context) =
            File(context.getExternalFilesDir(null), "functiongemma-270m-mobile-actions.litertlm")
    }
}
