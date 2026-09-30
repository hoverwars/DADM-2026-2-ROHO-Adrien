package com.example.demo_oral.llm

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import kotlin.coroutines.resume

/** One message of the conversation, as the LLM sees it. */
data class Turn(val fromUser: Boolean, val text: String)

/**
 * On-device chat LLM (Qwen 2.5 1.5B through MediaPipe LLM Inference).
 *
 * Every reply is generated from the whole conversation in a fresh session: this keeps the model
 * stateless, and lets the oldest turns be dropped when the context window is full.
 *
 * Only one [reply] may run at a time.
 */
class LocalLlm(context: Context) : AutoCloseable {

    private val engine: LlmInference

    init {
        val model = modelFile(context)
        if (!model.exists()) throw FileNotFoundException("LLM not found: $model (run run.bat)")
        // GPU first (much faster to read the conversation), CPU if this phone cannot do it
        engine = try {
            createEngine(context, model, LlmInference.Backend.GPU)
        } catch (e: Exception) {
            Log.w(TAG, "GPU backend unavailable, falling back to CPU", e)
            createEngine(context, model, LlmInference.Backend.CPU)
        }
    }

    private fun createEngine(context: Context, model: File, backend: LlmInference.Backend): LlmInference {
        val engine = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(backend)
                .build(),
        )
        Log.i(TAG, "LLM running on $backend")
        return engine
    }

    /**
     * Streams the answer to the last message of [history] (which must be from the user): [onText]
     * receives the whole answer so far each time it grows. Returns the full answer.
     */
    suspend fun reply(history: List<Turn>, onText: (String) -> Unit): String {
        val session = LlmInferenceSession.createFromOptions(
            engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.9f)
                .setTemperature(0.7f)
                .build(),
        )
        try {
            session.addQueryChunk(buildPrompt(history))
            val answer = StringBuilder()
            val future = session.generateResponseAsync { chunk, _ ->
                answer.append(chunk)
                onText(clean(answer))
            }
            try {
                future.awaitCompletion()
            } finally {
                if (!future.isDone) {
                    // Cancelled: the session must not be closed while it is still generating
                    session.cancelGenerateResponseAsync()
                    withContext(NonCancellable) { future.awaitCompletion() }
                }
            }
            future.get() // Rethrows a generation failure
            return clean(answer)
        } finally {
            session.close()
        }
    }

    /** Qwen (ChatML) prompt, without the oldest turns if the conversation does not fit. */
    private fun buildPrompt(history: List<Turn>): String {
        var first = 0
        while (true) {
            val prompt = format(history.subList(first, history.size))
            // Always keep at least the last message
            if (first >= history.lastIndex || engine.sizeInTokens(prompt) <= PROMPT_BUDGET_TOKENS) {
                return prompt
            }
            first++
        }
    }

    private fun format(turns: List<Turn>) = buildString {
        append("<|im_start|>system\n").append(SYSTEM_PROMPT).append("<|im_end|>\n")
        for (turn in turns) {
            append(if (turn.fromUser) "<|im_start|>user\n" else "<|im_start|>assistant\n")
            append(turn.text).append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }

    private fun clean(answer: CharSequence) =
        fixMojibake(answer.toString().substringBefore("<|im_")).trim()

    /**
     * MediaPipe decodes some byte-level tokens as Latin-1 instead of UTF-8 ("¡" comes out as "Â¡",
     * "é" as "Ã©") while others are fine. Each UTF-8 sequence that shows up as Latin-1 characters
     * is decoded again; a sequence still incomplete at the end (next token pending) is hidden.
     */
    private fun fixMojibake(text: String): String =
        text.replace(INCOMPLETE_SEQUENCE_AT_END, "").replace(MOJIBAKE_SEQUENCE) { match ->
            val bytes = match.value.toByteArray(Charsets.ISO_8859_1)
            try {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                match.value // Really was those characters
            }
        }

    override fun close() = engine.close()

    private suspend fun ListenableFuture<*>.awaitCompletion() =
        suspendCancellableCoroutine { continuation ->
            addListener({ continuation.resume(Unit) }, Runnable::run)
        }

    companion object {
        private const val TAG = "LocalLlm"

        // Lead byte (2, 3 or 4 bytes long) followed by its continuation bytes, all seen as Latin-1
        private val MOJIBAKE_SEQUENCE =
            Regex("[Â-ß][\u0080-¿]|[à-ï][\u0080-¿]{2}|[ð-ô][\u0080-¿]{3}")
        private val INCOMPLETE_SEQUENCE_AT_END =
            Regex("(?:[Â-ß]|[à-ï][\u0080-¿]?|[ð-ô][\u0080-¿]{0,2})$")

        /** Where run.bat pushes the model (the APK is far too small for it). */
        fun modelFile(context: Context) =
            File(context.getExternalFilesDir(null), "qwen2.5-1.5b-instruct.task")

        // Prompt + answer must fit in this many tokens
        private const val MAX_TOKENS = 2048

        // What is left for the answer once the conversation is in
        private const val PROMPT_BUDGET_TOKENS = 1300

        private const val SYSTEM_PROMPT =
            "You are a friendly voice assistant having a spoken conversation with the user. " +
                "ALWAYS answer in Spanish, whatever language the user's message seems to be in. " +
                "The user's messages are automatic transcriptions of speech and may contain " +
                "small recognition mistakes: interpret them charitably. " +
                "Your answers are read aloud, so keep them short (one to three sentences), " +
                "natural and conversational, in plain text only: no markdown, no lists, " +
                "no emojis, no special symbols."
    }
}
