package com.example.demo_oral.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale

/**
 * Reads a streamed answer aloud, sentence by sentence, as soon as each sentence is complete.
 *
 * Call [begin] for a new answer, [feed] with the whole answer so far each time it grows and
 * [finish] with the final answer. Not thread-safe: use it from a single coroutine at a time.
 */
class SpeechSynthesizer(context: Context) : AutoCloseable {

    @Volatile
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Spanish voice unavailable ($result)")
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = utteranceEnded(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = utteranceEnded(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = utteranceEnded(utteranceId)
            })
            ready = true
        } else {
            Log.e(TAG, "TextToSpeech init failed ($status)")
        }
    }

    private val pending = mutableSetOf<String>()
    private val nextUtteranceId = AtomicLong()

    private val _isSpeaking = MutableStateFlow(false)

    /** True while some of the answer is queued or being said. */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // How much of the current answer was already handed to the TTS engine
    private var spokenLength = 0

    @Synchronized
    fun begin() {
        spokenLength = 0
    }

    @Synchronized
    fun feed(answer: String) {
        // A sentence is complete once followed by whitespace, so "3.5" is not cut in two
        var end = -1
        for (i in spokenLength until answer.length - 1) {
            if (answer[i] in SENTENCE_END && answer[i + 1].isWhitespace()) end = i + 1
        }
        if (end > spokenLength) {
            speak(answer.substring(spokenLength, end))
            spokenLength = end
        }
    }

    @Synchronized
    fun finish(answer: String) {
        if (answer.length > spokenLength) speak(answer.substring(spokenLength))
        spokenLength = answer.length
    }

    /** Cuts the voice immediately. */
    fun stop() {
        tts.stop()
        synchronized(pending) {
            pending.clear()
            _isSpeaking.value = false
        }
    }

    private fun utteranceEnded(id: String?) {
        synchronized(pending) {
            pending.remove(id)
            _isSpeaking.value = pending.isNotEmpty()
        }
    }

    private fun speak(text: String) {
        val speakable = text.replace(NOT_SPEAKABLE, "").trim()
        if (speakable.isEmpty() || !ready) return
        val id = "u${nextUtteranceId.incrementAndGet()}"
        synchronized(pending) {
            pending.add(id)
            _isSpeaking.value = true
        }
        if (tts.speak(speakable, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) utteranceEnded(id)
    }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "SpeechSynthesizer"
        private val SENTENCE_END = charArrayOf('.', '!', '?', '…', '\n')
        private val NOT_SPEAKABLE = Regex("[*#_`~]")
    }
}
