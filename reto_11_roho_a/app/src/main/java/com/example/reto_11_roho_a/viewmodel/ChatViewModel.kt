package com.example.reto_11_roho_a.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_11_roho_a.R
import com.example.reto_11_roho_a.llm.LocalLlm
import com.example.reto_11_roho_a.llm.LlmReply
import com.example.reto_11_roho_a.llm.RoutedCall
import com.example.reto_11_roho_a.llm.Turn
import com.example.reto_11_roho_a.speech.SpeechSynthesizer
import com.example.reto_11_roho_a.tools.AgentOutcome
import com.example.reto_11_roho_a.tools.FillResult
import com.example.reto_11_roho_a.tools.PendingAction
import com.example.reto_11_roho_a.tools.Risk
import com.example.reto_11_roho_a.tools.ToolAction
import com.example.reto_11_roho_a.tools.ToolAgent
import com.example.reto_11_roho_a.tools.ToolCard
import com.example.reto_11_roho_a.tools.ToolParam
import com.example.reto_11_roho_a.tools.ToolRegistry
import com.example.reto_11_roho_a.tools.ToolSpec
import com.example.reto_11_roho_a.tools.ToolStatus
import com.example.reto_11_roho_a.tools.impl.defaultTools
import com.example.reto_11_roho_a.tools.resolve.Confirmation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

enum class ModelState { LOADING, READY, ERROR }

enum class Role { USER, ASSISTANT }

/** [action] turns the message into a card: an action the assistant is doing, or asks to do. */
data class ChatMessage(val id: Long, val role: Role, val text: String, val action: ToolAction? = null)

data class ChatUiState(
    val llmModel: ModelState = ModelState.LOADING,
    /** The assistant is working on / saying an answer */
    val isGenerating: Boolean = false,
    /** The voice reading the answer aloud is still going */
    val isSpeaking: Boolean = false,
    /** Setting: the answers are read aloud */
    val speechEnabled: Boolean = false,
    /** Android permissions a tool is waiting for: the screen must ask for them */
    val pendingPermissions: List<String> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
) {
    val modelsReady: Boolean get() = llmModel == ModelState.READY

    /** Messages are locked while the assistant is writing or saying its answer. */
    val assistantBusy: Boolean get() = isGenerating || isSpeaking

    /** An action is waiting for the user's yes or no */
    val awaitingConfirmation: Boolean
        get() = messages.any { it.action?.status == ToolStatus.AWAITING_CONFIRMATION }

    /** An action is waiting for a value the user has not given yet */
    val awaitingInfo: Boolean
        get() = messages.any { it.action?.status == ToolStatus.AWAITING_INFO }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        ChatUiState(
            speechEnabled = prefs.getBoolean(KEY_SPEECH_ENABLED, false),
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var llm: LocalLlm? = null
    private val speaker = SpeechSynthesizer(application)
    private val llmLoadingJob: Job
    private val agent = ToolAgent(
        registry = ToolRegistry(defaultTools(application)),
        route = ::route,
        hasPermission = { ContextCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED },
    )

    // The LLM answers and picks the tool in one go: what it said while deciding is streamed into
    // this message and kept here, so a plain answer does not need a second generation
    private var streamingInto: Long? = null
    private var lastReply: LlmReply? = null

    /**
     * An action shown in the message [messageId], waiting for the user. While values are missing:
     * [request] is what the user first asked, [missing] what is still needed, [questions] how many
     * questions were already asked.
     */
    private data class Pending(
        val messageId: Long,
        val action: PendingAction,
        val request: String = "",
        val missing: List<ToolParam> = emptyList(),
        val questions: Int = 1,
    )

    // Waiting for the user's "yes" (button or voice) / for Android permissions / for a missing
    // value. Only one of each, and a new request drops the others.
    private var awaitingConfirmation: Pending? = null
    private var awaitingPermission: Pending? = null
    private var awaitingInfo: Pending? = null

    // Confirmation and missing values never wait together: they share the timeout
    private var pendingTimeout: Job? = null
    private var generationJob: Job? = null
    private var nextMessageId = 0L

    init {
        viewModelScope.launch {
            speaker.isSpeaking.collect { speaking -> _uiState.update { it.copy(isSpeaking = speaking) } }
        }
        llmLoadingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val start = System.currentTimeMillis()
                llm = LocalLlm(application)
                Log.i(TAG, "LLM loaded in ${System.currentTimeMillis() - start} ms")
                _uiState.update { it.copy(llmModel = ModelState.READY) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the LLM", e)
                _uiState.update { it.copy(llmModel = ModelState.ERROR) }
            }
        }
    }

    /** Setting: read the answers aloud or not. Turning it off also silences the current answer. */
    fun setSpeechEnabled(enabled: Boolean) {
        _uiState.update { it.copy(speechEnabled = enabled) }
        prefs.edit().putBoolean(KEY_SPEECH_ENABLED, enabled).apply()
        if (!enabled) speaker.stop()
    }

    /** Forgets the whole conversation. */
    fun resetConversation() {
        stopAssistant()
        takeConfirmation()
        takeInfo()
        awaitingPermission = null
        _uiState.update { it.copy(messages = emptyList(), pendingPermissions = emptyList()) }
    }

    /** Cuts the answer being generated and the voice reading it (the text so far is kept). */
    private fun stopAssistant() {
        generationJob?.cancel()
        speaker.stop()
    }

    /** The user sent a typed message. */
    fun sendText(text: String) {
        val state = _uiState.value
        if (!state.modelsReady || state.assistantBusy) return
        submit(text.trim())
    }

    private fun submit(text: String) {
        if (text.isEmpty()) return
        val userMessage = ChatMessage(nextMessageId++, Role.USER, text)
        val answer = ChatMessage(nextMessageId++, Role.ASSISTANT, "")
        _uiState.update { it.copy(messages = it.messages + userMessage + answer) }

        // The conversation so far, without this turn's empty answer (a card is text too)
        val history = _uiState.value.messages.dropLast(1).map { Turn(it.role == Role.USER, it.text) }
        launchAnswer(answer) {
            when (val decision = decide(text, answer.id, history)) {
                Decision.Chat -> chat(history, answer.id)
                is Decision.Streamed -> Unit
                Decision.Handled -> Unit
                is Decision.Action -> present(answer.id, decision.outcome, request = text)
            }
        }
    }

    /**
     * Runs [work] once the previous answer is over. [answer] is the message it fills, if any: an
     * empty one is dropped at the end. Whatever happens, the assistant is idle again afterwards.
     */
    private fun launchAnswer(answer: ChatMessage?, work: suspend () -> Unit) {
        _uiState.update { it.copy(isGenerating = true) }
        val previous = generationJob
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            previous?.join()
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "The assistant could not answer", e)
                answer?.let { setMessage(it.id, string(R.string.chat_error), action = null) }
            } finally {
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        // An answer interrupted before saying anything is not worth keeping
                        messages = state.messages.filterNot { it.id == answer?.id && it.text.isEmpty() },
                    )
                }
            }
        }
    }

    private sealed interface Decision {
        /** Plain conversation */
        data object Chat : Decision

        /** The LLM already answered (the text is shown) while deciding there was no tool to call */
        data class Streamed(val text: String) : Decision

        /** Dealt with by updating an existing card: nothing else to say */
        data object Handled : Decision
        data class Action(val outcome: AgentOutcome) : Decision
    }

    /** [answerId] is the empty assistant message created for this turn. */
    private suspend fun decide(text: String, answerId: Long, history: List<Turn>): Decision {
        // A request that was never followed up is over as soon as the user says something else
        cancelPermissionRequest()
        if (_uiState.value.awaitingConfirmation) {
            when (Confirmation.parse(text)) {
                Confirmation.Answer.YES -> { confirm(true); return Decision.Handled }
                Confirmation.Answer.NO -> { confirm(false); return Decision.Handled }
                // Anything else: the user moved on, the action is dropped
                Confirmation.Answer.UNKNOWN -> cancelConfirmation(string(R.string.tool_cancelled))
            }
        }
        llmLoadingJob.join()
        awaitingInfo?.let { pending -> answerInfo(pending, text, answerId)?.let { return it } }
        lastReply = null
        streamingInto = answerId
        val outcome = try {
            agent.handle(text, history)
        } finally {
            streamingInto = null
        }
        if (outcome != AgentOutcome.NotATool) return Decision.Action(outcome)
        // No tool: if the LLM already said something while deciding, that is the answer
        val said = lastReply?.takeIf { it.call == null }?.text.orEmpty()
        if (said.isEmpty()) return Decision.Chat
        setAnswerText(answerId, said)
        if (_uiState.value.speechEnabled) speaker.finish(said)
        return Decision.Streamed(said)
    }

    /**
     * [text] may be the value [pending] asked for. Returns null when it is not: the action is
     * dropped and [text] is handled as a new message.
     */
    private suspend fun answerInfo(pending: Pending, text: String, answerId: Long): Decision? {
        if (Confirmation.isCancel(text)) {
            cancelInfo(string(R.string.tool_cancelled), spoken = true)
            return Decision.Handled
        }
        return when (val result = agent.fill(pending.action, pending.missing, pending.request, text)) {
            FillResult.NoProgress -> {
                cancelInfo(string(R.string.tool_cancelled))
                null
            }
            is FillResult.Progress -> {
                if (takeInfo() == null) return Decision.Handled // Cancelled meanwhile (button, timeout)
                when (val outcome = result.outcome) {
                    AgentOutcome.NotATool -> {
                        setStatus(pending.messageId, ToolStatus.CANCELLED, string(R.string.tool_cancelled))
                        null
                    }
                    is AgentOutcome.NeedsInfo -> {
                        if (pending.questions >= MAX_INFO_QUESTIONS) {
                            val gaveUp = string(R.string.tool_info_gave_up)
                            setStatus(pending.messageId, ToolStatus.CANCELLED, gaveUp)
                            speak(gaveUp)
                        } else {
                            // The card keeps what is known so far, the next question is a new message
                            setMessage(
                                pending.messageId,
                                _uiState.value.messages.first { it.id == pending.messageId }.text,
                                ToolAction(outcome.card, ToolStatus.AWAITING_INFO, outcome.action.risky),
                            )
                            setAnswerText(answerId, outcome.question)
                            speak(outcome.question)
                            awaitInfo(
                                pending.copy(
                                    action = outcome.action,
                                    missing = outcome.missing,
                                    questions = pending.questions + 1,
                                )
                            )
                        }
                        Decision.Handled
                    }
                    // Complete: the card of the request goes on (permissions, confirmation, result)
                    else -> {
                        present(pending.messageId, outcome, pending.request)
                        Decision.Handled
                    }
                }
            }
        }
    }

    private fun awaitInfo(pending: Pending) = synchronized(this) {
        awaitingInfo = pending
        restartTimeout { cancelInfo(string(R.string.tool_timeout)) }
    }

    private fun takeInfo(): Pending? = synchronized(this) {
        if (awaitingInfo != null) {
            pendingTimeout?.cancel()
            pendingTimeout = null
        }
        awaitingInfo.also { awaitingInfo = null }
    }

    private fun cancelInfo(text: String, spoken: Boolean = false) {
        val pending = takeInfo() ?: return
        setStatus(pending.messageId, ToolStatus.CANCELLED, text)
        if (spoken) speak(text)
    }

    /** Cancels whatever waits: the question for a missing value or the confirmation. */
    private fun restartTimeout(onTimeout: () -> Unit) {
        pendingTimeout?.cancel()
        pendingTimeout = viewModelScope.launch {
            delay(PENDING_TIMEOUT_MS)
            onTimeout()
        }
    }

    /** The cancel button of a card, waiting for a confirmation or for a missing value. */
    fun onCancelButton() {
        if (awaitingInfo != null) launchAnswer(null) { cancelInfo(string(R.string.tool_cancelled), spoken = true) }
        else onConfirmationButton(false)
    }

    /** The confirmation buttons of a card. */
    fun onConfirmationButton(yes: Boolean) {
        if (!_uiState.value.awaitingConfirmation) return
        launchAnswer(null) { confirm(yes) }
    }

    /** The user's answer to a card waiting for confirmation, from a button or from the voice. */
    private suspend fun confirm(yes: Boolean) {
        // Buttons and voice may answer at the same time: only the first one counts
        val pending = takeConfirmation() ?: return
        if (!yes) {
            val text = string(R.string.tool_cancelled)
            setStatus(pending.messageId, ToolStatus.CANCELLED, text)
            speak(text)
            return
        }
        setStatus(pending.messageId, ToolStatus.RUNNING)
        present(pending.messageId, agent.execute(pending.action))
    }

    private fun takeConfirmation(): Pending? = synchronized(this) {
        if (awaitingConfirmation != null) {
            pendingTimeout?.cancel()
            pendingTimeout = null
        }
        awaitingConfirmation.also { awaitingConfirmation = null }
    }

    private fun cancelConfirmation(text: String) {
        val pending = takeConfirmation() ?: return
        setStatus(pending.messageId, ToolStatus.CANCELLED, text)
    }

    private fun cancelPermissionRequest() {
        val pending = awaitingPermission ?: return
        awaitingPermission = null
        _uiState.update { it.copy(pendingPermissions = emptyList()) }
        setStatus(pending.messageId, ToolStatus.CANCELLED, string(R.string.tool_cancelled))
    }

    /**
     * Shows what happened in the message [messageId] and remembers what the assistant waits for.
     * [request] is the user's sentence, kept when a value is missing.
     */
    private fun present(messageId: Long, outcome: AgentOutcome, request: String = "") {
        when (outcome) {
            is AgentOutcome.Done -> {
                val status = if (outcome.success) ToolStatus.DONE else ToolStatus.FAILED
                setMessage(messageId, outcome.text, ToolAction(outcome.card, status, outcome.risky))
                speak(outcome.text)
            }
            is AgentOutcome.Error -> {
                val text = string(R.string.tool_error)
                setMessage(messageId, text, ToolAction(outcome.card, ToolStatus.FAILED, outcome.risky))
                speak(text)
            }
            is AgentOutcome.Confirm -> {
                val action = ToolAction(outcome.card, ToolStatus.AWAITING_CONFIRMATION, risky = true)
                setMessage(messageId, outcome.question, action)
                speak(outcome.question)
                synchronized(this) {
                    awaitingConfirmation = Pending(messageId, outcome.action)
                    restartTimeout { cancelConfirmation(string(R.string.tool_timeout)) }
                }
            }
            is AgentOutcome.NeedsInfo -> {
                val action = ToolAction(outcome.card, ToolStatus.AWAITING_INFO, outcome.action.risky)
                setMessage(messageId, outcome.question, action)
                speak(outcome.question)
                awaitInfo(Pending(messageId, outcome.action, request, outcome.missing))
            }
            is AgentOutcome.NeedsPermission -> {
                awaitingPermission = Pending(messageId, outcome.action)
                val names = permissionNames(outcome.permissions).joinToString(", ")
                val card = outcome.card.copy(
                    fields = outcome.card.fields + (string(R.string.field_permissions) to names)
                )
                setMessage(
                    messageId,
                    string(R.string.tool_permission_needed),
                    ToolAction(card, ToolStatus.AWAITING_PERMISSION, outcome.action.risky),
                )
            }
            AgentOutcome.NotATool -> Unit
        }
    }

    private fun permissionNames(permissions: List<String>): List<String> = permissions.map {
        string(
            when (it) {
                Manifest.permission.READ_CONTACTS -> R.string.permission_contacts
                Manifest.permission.SEND_SMS -> R.string.permission_sms
                Manifest.permission.CALL_PHONE -> R.string.permission_phone
                else -> R.string.permission_calendar
            }
        )
    }.distinct()

    /** The button of a card waiting for permissions: only now does Android show its dialog. */
    fun requestPermissions() {
        val pending = awaitingPermission ?: return
        val missing = pending.action.tool.spec.permissions
        _uiState.update { it.copy(pendingPermissions = missing) }
    }

    /** The screen asked for the permissions a tool was waiting for. */
    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(pendingPermissions = emptyList()) }
        val pending = awaitingPermission ?: return
        awaitingPermission = null
        if (!granted) {
            setStatus(pending.messageId, ToolStatus.DENIED, string(R.string.tool_permission_denied))
            return
        }
        launchAnswer(null) { present(pending.messageId, agent.prepare(pending.action)) }
    }

    /** The LLM, offered every tool, decides which one to call (if any) for the last message of [history]. */
    private suspend fun route(history: List<Turn>, tools: List<ToolSpec>): RoutedCall? {
        val llm = checkNotNull(llm) { "The LLM is not available" }
        val target = streamingInto
        if (target != null) speaker.begin()
        val job = currentCoroutineContext().job
        val reply = llm.reply(history, tools) { partial ->
            if (target == null || !job.isActive) return@reply
            setAnswerText(target, partial)
            if (_uiState.value.speechEnabled) speaker.feed(partial)
        }
        if (target != null) lastReply = reply
        return reply.call
    }

    /** Streams the answer of the LLM to a plain conversation, reading it aloud as it comes. */
    private suspend fun chat(history: List<Turn>, answerId: Long) {
        llmLoadingJob.join()
        val llm = checkNotNull(llm) { "The LLM is not available" }
        speaker.begin()
        val job = currentCoroutineContext().job
        val reply = llm.reply(history, emptyList()) { partial ->
            // Called from the LLM thread: once interrupted, nothing more may be said
            if (!job.isActive) return@reply
            setAnswerText(answerId, partial)
            if (_uiState.value.speechEnabled) speaker.feed(partial)
        }
        setAnswerText(answerId, reply.text)
        if (_uiState.value.speechEnabled) speaker.finish(reply.text)
    }

    private val PendingAction.risky get() = tool.spec.risk == Risk.CONFIRM

    private fun speak(text: String) {
        speaker.begin()
        if (_uiState.value.speechEnabled) speaker.finish(text)
    }

    private fun string(id: Int) = getApplication<Application>().getString(id)

    private fun setAnswerText(id: Long, text: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) it.copy(text = text) else it })
        }
    }

    private fun setMessage(id: Long, text: String, action: ToolAction?) {
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) it.copy(text = text, action = action) else it })
        }
    }

    /** Moves the card of a message to another state, keeping its text unless [text] is given. */
    private fun setStatus(id: Long, status: ToolStatus, text: String? = null) {
        _uiState.update { state ->
            state.copy(messages = state.messages.map {
                if (it.id == id && it.action != null) {
                    it.copy(text = text ?: it.text, action = it.action.copy(status = status))
                } else it
            })
        }
    }

    override fun onCleared() {
        speaker.close()
        // Native resources must only be freed once nothing is using them anymore
        closeWhenIdle(llmLoadingJob, generationJob) { llm?.close() }
    }

    private fun closeWhenIdle(vararg jobs: Job?, close: () -> Unit) {
        val pending = jobs.filterNotNull().filter { !it.isCompleted }
        if (pending.isEmpty()) return close()
        val remaining = AtomicInteger(pending.size)
        pending.forEach { job ->
            job.invokeOnCompletion { if (remaining.decrementAndGet() == 0) close() }
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val PREFS_NAME = "settings"
        private const val KEY_SPEECH_ENABLED = "speech_enabled"

        // A confirmation or a question nobody answers is cancelled after this long
        private const val PENDING_TIMEOUT_MS = 60_000L

        // Questions asked for the values of one action before giving up
        private const val MAX_INFO_QUESTIONS = 3
    }
}
