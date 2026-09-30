package com.example.demo_oral.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
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
            ready = true
        } else {
            Log.e(TAG, "TextToSpeech init failed ($status)")
        }
    }

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
    }

    private fun speak(text: String) {
        val speakable = text.replace(NOT_SPEAKABLE, "").trim()
        if (speakable.isEmpty() || !ready) return
        tts.speak(speakable, TextToSpeech.QUEUE_ADD, null, null)
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
