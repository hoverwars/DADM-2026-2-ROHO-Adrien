package com.example.demo_oral.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo_oral.R
import com.example.demo_oral.llm.LocalLlm
import com.example.demo_oral.llm.Turn
import com.example.demo_oral.speech.BeepPlayer
import com.example.demo_oral.speech.LiveTranscriber
import com.example.demo_oral.speech.SpeechSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ModelState { LOADING, READY, ERROR }

enum class Role { USER, ASSISTANT }

data class ChatMessage(val id: Long, val role: Role, val text: String)

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
    private val speaker = SpeechSynthesizer(application)
    private val beeps = BeepPlayer()
    private val speechLoadingJob: Job
    private val llmLoadingJob: Job
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
        _uiState.update { it.copy(messages = emptyList()) }
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
                isGenerating = true,
                transcript = "",
                partial = "",
            )
        }
        if (text.isEmpty()) return

        val history = _uiState.value.messages.dropLast(1).map { Turn(it.role == Role.USER, it.text) }
        val previous = generationJob
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            previous?.join()
            try {
                llmLoadingJob.join()
                val llm = checkNotNull(llm) { "The LLM is not available" }
                speaker.begin()
                val reply = llm.reply(history) { partial ->
                    // Called from the LLM thread: once interrupted, nothing more may be said
                    if (!isActive) return@reply
                    setAnswerText(answer.id, partial)
                    if (_uiState.value.speechEnabled) speaker.feed(partial)
                }
                setAnswerText(answer.id, reply)
                if (_uiState.value.speechEnabled) speaker.finish(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "The LLM could not answer", e)
                setAnswerText(answer.id, getApplication<Application>().getString(R.string.chat_error))
            } finally {
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        // An answer interrupted before saying anything is not worth keeping
                        messages = state.messages.filterNot { it.id == answer.id && it.text.isEmpty() },
                    )
                }
            }
        }
    }

    private fun setAnswerText(id: Long, text: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) it.copy(text = text) else it })
        }
    }

    override fun onCleared() {
        readerJob?.cancel()
        beeps.close()
        speaker.close()
        // Native resources must only be freed once nothing is using them anymore
        closeWhenIdle(speechLoadingJob, processingJob) { transcriber?.close() }
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

        // After release, the mic stays open at most this long waiting for the VAD to end the sentence
        private const val MAX_RELEASE_WAIT_SAMPLES = LiveTranscriber.SAMPLE_RATE * 3L
    }
}
