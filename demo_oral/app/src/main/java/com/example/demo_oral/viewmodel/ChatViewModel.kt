package com.example.demo_oral.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo_oral.R
import com.example.demo_oral.llm.LocalLlm
import com.example.demo_oral.llm.ToolRouter
import com.example.demo_oral.llm.Turn
import com.example.demo_oral.speech.BeepPlayer
import com.example.demo_oral.speech.LiveTranscriber
import com.example.demo_oral.speech.SpeechSynthesizer
import com.example.demo_oral.tools.AgentOutcome
import com.example.demo_oral.tools.FillResult
import com.example.demo_oral.tools.PendingAction
import com.example.demo_oral.tools.Risk
import com.example.demo_oral.tools.ToolAction
import com.example.demo_oral.tools.ToolAgent
import com.example.demo_oral.tools.ToolCard
import com.example.demo_oral.tools.ToolParam
import com.example.demo_oral.tools.ToolRegistry
import com.example.demo_oral.tools.ToolStatus
import com.example.demo_oral.tools.impl.defaultTools
import com.example.demo_oral.tools.resolve.Confirmation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import java.util.concurrent.atomic.AtomicLong

enum class ModelState { LOADING, READY, ERROR }

enum class Role { USER, ASSISTANT }

/** [action] turns the message into a card: an action the assistant is doing, or asks to do. */
data class ChatMessage(val id: Long, val role: Role, val text: String, val action: ToolAction? = null)

data class ChatUiState(
    val speechModel: ModelState = ModelState.LOADING,
    val llmModel: ModelState = ModelState.LOADING,
    val isRecording: Boolean = false,
    /** Mic released, Whisper is still transcribing the end of the message */
    val isFinishing: Boolean = false,
    /** The assistant is working on / saying an answer */
    val isGenerating: Boolean = false,
    /** The voice reading the answer aloud is still going */
    val isSpeaking: Boolean = false,
    /** Setting: the answers are read aloud */
    val speechEnabled: Boolean = true,
    /** Android permissions a tool is waiting for: the screen must ask for them */
    val pendingPermissions: List<String> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    /** Finished sentences of the message being spoken */
    val transcript: String = "",
    /** Sentence currently being spoken, may still change */
    val partial: String = "",
) {
    val modelsReady: Boolean get() = speechModel == ModelState.READY && llmModel == ModelState.READY

    /** The mic is locked while the assistant is writing or saying its answer. */
    val assistantBusy: Boolean get() = isGenerating || isSpeaking

    /** The message being spoken is shown as a draft until the mic is released and it is sent. */
    /** An action is waiting for the user's yes or no */
    val awaitingConfirmation: Boolean
        get() = messages.any { it.action?.status == ToolStatus.AWAITING_CONFIRMATION }

    /** An action is waiting for a value the user has not given yet */
    val awaitingInfo: Boolean
        get() = messages.any { it.action?.status == ToolStatus.AWAITING_INFO }

    val hasDraft: Boolean get() = isRecording || isFinishing || transcript.isNotEmpty() || partial.isNotEmpty()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        ChatUiState(speechEnabled = prefs.getBoolean(KEY_SPEECH_ENABLED, true))
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var transcriber: LiveTranscriber? = null
    private var llm: LocalLlm? = null
    private var router: ToolRouter? = null
    private val speaker = SpeechSynthesizer(application)
    private val beeps = BeepPlayer()
    private val speechLoadingJob: Job
    private val llmLoadingJob: Job
    private val routerLoadingJob: Job
    private val agent = ToolAgent(
        registry = ToolRegistry(defaultTools(application)),
        route = { utterance, candidates -> router?.route(utterance, candidates) },
        hasPermission = { ContextCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED },
    )

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
    private var readerJob: Job? = null
    private var processingJob: Job? = null
    private var generationJob: Job? = null
    private var recording: Recording? = null
    private var nextMessageId = 0L

    init {
        viewModelScope.launch {
            speaker.isSpeaking.collect { speaking -> _uiState.update { it.copy(isSpeaking = speaking) } }
        }
        speechLoadingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val start = System.currentTimeMillis()
                transcriber = LiveTranscriber(application.assets)
                Log.i(TAG, "Speech model loaded in ${System.currentTimeMillis() - start} ms")
                _uiState.update { it.copy(speechModel = ModelState.READY) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the speech model", e)
                _uiState.update { it.copy(speechModel = ModelState.ERROR) }
            }
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
        // Optional: without the router model the assistant still chats, it just cannot act
        routerLoadingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                router = ToolRouter(application)
            } catch (e: Exception) {
                Log.w(TAG, "Tools are disabled: could not load the router", e)
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

    /** Push-to-talk, pressed. The caller must have been granted RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun startRecording() {
        val transcriber = transcriber ?: return
        // Also ignore presses while the previous message's mic is still open, waiting for its end
        if (_uiState.value.isRecording || readerJob?.isActive == true) return

        // Talking over the assistant interrupts it
        stopAssistant()

        val chunkSize = LiveTranscriber.SAMPLE_RATE / 10 // 100 ms
        val minBufferSize = AudioRecord.getMinBufferSize(
            LiveTranscriber.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            LiveTranscriber.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, chunkSize * 2 * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord could not be initialized")
            record.release()
            return
        }

        _uiState.update { it.copy(isRecording = true) }

        // Reading the mic and running Whisper are decoupled so no audio is lost while decoding
        val audio = Channel<FloatArray>(Channel.UNLIMITED)
        val recording = Recording()
        this.recording = recording

        val reader = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(chunkSize)
            try {
                // Let the start sound finish first so the mic does not pick it up
                beeps.playStart()
                delay(BeepPlayer.START_SOUND_DURATION_MS)
                record.startRecording()
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    // trySend, never suspends (unlimited channel): the last chunk is not dropped on cancel
                    if (read > 0) {
                        audio.trySend(FloatArray(read) { buffer[it] / 32768f })
                        recording.samplesRead.addAndGet(read.toLong())
                    }
                }
            } finally {
                record.stop()
                record.release()
                audio.close()
            }
        }
        readerJob = reader

        val previousProcessing = processingJob
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            // A previous message may still be transcribing its last sentence
            previousProcessing?.join()
            transcriber.reset()
            try {
                // Counted in audio samples rather than wall-clock time so a slow decoding can't skew it
                var processed = 0L
                for (chunk in audio) {
                    // If decoding was slow, catch up with everything recorded meanwhile at once
                    var samples = chunk
                    while (true) samples += audio.tryReceive().getOrNull() ?: break
                    handle(transcriber.accept(samples))
                    processed += samples.size

                    // The button is released: the mic stays open until the VAD itself closes the
                    // sentence being spoken (a natural pause), or until the safety limit
                    val releasedAt = recording.releasedAt.get()
                    if (releasedAt >= 0 && processed >= releasedAt && reader.isActive &&
                        (!transcriber.isInSentence || processed - releasedAt >= MAX_RELEASE_WAIT_SAMPLES)
                    ) {
                        Log.i(TAG, "Closing the mic ${(processed - releasedAt) * 1000 / LiveTranscriber.SAMPLE_RATE} ms after release")
                        // The channel closes, this loop still drains what was recorded meanwhile
                        reader.cancel()
                    }
                }
                // The mic is off; if a sentence is still open (noise, safety limit) it is closed here
                beeps.playStop()
                handle(transcriber.flush())
                sendDraft()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _uiState.update { it.copy(isFinishing = false, transcript = "", partial = "") }
            }
        }
    }

    /**
     * Push-to-talk, released. Nothing is cut here: the transcription job (see [startRecording])
     * keeps listening until the VAD ends the sentence, transcribes it, then sends the message.
     */
    fun stopRecording() {
        if (!_uiState.getAndUpdate { it.copy(isRecording = false, isFinishing = true) }.isRecording) return
        recording?.let { it.releasedAt.set(it.samplesRead.get()) }
    }

    /** Position in the audio stream of a push-to-talk session. */
    private class Recording {
        val samplesRead = AtomicLong()

        /** Samples read when the button was released, -1 while it is still held */
        val releasedAt = AtomicLong(-1)
    }

    private fun handle(events: List<LiveTranscriber.Event>) {
        for (event in events) {
            when (event) {
                is LiveTranscriber.Event.Partial -> _uiState.update { it.copy(partial = event.text) }
                is LiveTranscriber.Event.Final -> _uiState.update {
                    it.copy(transcript = listOf(it.transcript, event.text).filter(String::isNotBlank).joinToString(" "))
                }
            }
        }
    }

    /** Turns what was just said into a message and asks the LLM for an answer. */
    private fun sendDraft() = submit(_uiState.value.transcript.trim())

    /** DEBUG: sends a typed message as if it had been spoken (with DebugTextInput.kt, remove together). */
    fun debugSendText(text: String) {
        val state = _uiState.value
        if (!state.modelsReady || state.assistantBusy || state.isRecording || state.isFinishing) return
        submit(text.trim())
    }

    private fun submit(text: String) {
        val userMessage = ChatMessage(nextMessageId++, Role.USER, text)
        val answer = ChatMessage(nextMessageId++, Role.ASSISTANT, "")
        _uiState.update {
            if (text.isEmpty()) it.copy(isFinishing = false, transcript = "", partial = "")
            else it.copy(
                messages = it.messages + userMessage + answer,
                isFinishing = false,
                transcript = "",
                partial = "",
            )
        }
        if (text.isEmpty()) return

        val history = _uiState.value.messages.dropLast(1).map { Turn(it.role == Role.USER, it.text) }
        launchAnswer(answer) {
            when (val decision = decide(text, answer.id)) {
                Decision.Chat -> chat(history, answer.id)
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

        /** Dealt with by updating an existing card: nothing else to say */
        data object Handled : Decision
        data class Action(val outcome: AgentOutcome) : Decision
    }

    /** [answerId] is the empty assistant message created for this turn. */
    private suspend fun decide(text: String, answerId: Long): Decision {
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
        routerLoadingJob.join()
        awaitingInfo?.let { pending -> answerInfo(pending, text, answerId)?.let { return it } }
        val outcome = agent.handle(text)
        return if (outcome == AgentOutcome.NotATool) Decision.Chat else Decision.Action(outcome)
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
        // "Call Marie" while an event waits for its title: the user moved on
        if (agent.asksForAnother(text, pending.action.tool)) {
            cancelInfo(string(R.string.tool_cancelled))
            return null
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

    /** Streams the answer of the conversational LLM, reading it aloud as it comes. */
    private suspend fun chat(history: List<Turn>, answerId: Long) {
        llmLoadingJob.join()
        val llm = checkNotNull(llm) { "The LLM is not available" }
        speaker.begin()
        val job = currentCoroutineContext().job
        val reply = llm.reply(history) { partial ->
            // Called from the LLM thread: once interrupted, nothing more may be said
            if (!job.isActive) return@reply
            setAnswerText(answerId, partial)
            if (_uiState.value.speechEnabled) speaker.feed(partial)
        }
        setAnswerText(answerId, reply)
        if (_uiState.value.speechEnabled) speaker.finish(reply)
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
        readerJob?.cancel()
        beeps.close()
        speaker.close()
        // Native resources must only be freed once nothing is using them anymore
        closeWhenIdle(speechLoadingJob, processingJob) { transcriber?.close() }
        closeWhenIdle(llmLoadingJob, generationJob) { llm?.close() }
        closeWhenIdle(routerLoadingJob, generationJob) { router?.close() }
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

        // After release, the mic stays open at most this long waiting for the VAD to end the sentence
        private const val MAX_RELEASE_WAIT_SAMPLES = LiveTranscriber.SAMPLE_RATE * 3L
    }
}
