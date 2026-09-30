package com.example.demo_oral.speech

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Pseudo-streaming speech recognition with Whisper.
 *
 * Whisper is not a streaming model, so a voice activity detector (Silero VAD) cuts the audio
 * into sentences: while someone is speaking the current sentence is re-transcribed regularly
 * (partial result), and once a pause is detected the sentence is transcribed one last time
 * (final result).
 *
 * Not thread-safe: feed it from a single coroutine at a time.
 */
class LiveTranscriber(assets: AssetManager) : AutoCloseable {

    sealed interface Event {
        data class Partial(val text: String) : Event
        data class Final(val text: String) : Event
    }

    private val recognizer = OfflineRecognizer(
        assetManager = assets,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "models/small-encoder.int8.onnx",
                    decoder = "models/small-decoder.int8.onnx",
                    language = "es",
                    task = "transcribe",
                ),
                tokens = "models/small-tokens.txt",
                modelType = "whisper",
                // The small model is much heavier than base
                numThreads = 4,
            ),
        ),
    )

    private val vad = if (!USE_VAD) null else Vad(
        assetManager = assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "models/silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.6f,
                minSpeechDuration = 0.25f,
                windowSize = VAD_WINDOW_SIZE,
                // Whisper handles at most 30 s, force a cut well before that
                maxSpeechDuration = 15f,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    // Samples not yet fed to the VAD (it expects fixed-size windows)
    private var vadPending = FloatArray(0)

    // Audio of the sentence being spoken, used for partial results
    private var speechBuffer = FloatArray(0)
    private var isInSpeech = false
    private var samplesSinceLastPartial = 0

    /** Whether someone is speaking right now. Always true without VAD, as silence can't be told. */
    val isSpeaking: Boolean
        get() = vad?.isSpeechDetected() ?: true

    /** Whether a sentence has started and the VAD has not closed it yet (a final result is due). */
    val isInSentence: Boolean
        get() = isInSpeech

    fun reset() {
        vad?.reset()
        vadPending = FloatArray(0)
        speechBuffer = FloatArray(0)
        isInSpeech = false
        samplesSinceLastPartial = 0
    }

    /** Feeds 16 kHz mono samples in [-1, 1] and returns what changed in the transcription. */
    fun accept(samples: FloatArray): List<Event> {
        if (vad == null) return acceptWithoutVad(samples)
        val events = mutableListOf<Event>()

        vadPending += samples
        var offset = 0
        while (vadPending.size - offset >= VAD_WINDOW_SIZE) {
            vad.acceptWaveform(vadPending.copyOfRange(offset, offset + VAD_WINDOW_SIZE))
            offset += VAD_WINDOW_SIZE
        }
        vadPending = vadPending.copyOfRange(offset, vadPending.size)

        speechBuffer += samples
        samplesSinceLastPartial += samples.size
        if (!isInSpeech && vad.isSpeechDetected()) {
            isInSpeech = true
            samplesSinceLastPartial = 0
            // Keep a bit of audio from before the detection so the first word is not cut
            speechBuffer = speechBuffer.takeLast(PRE_ROLL_SAMPLES)
        }

        val finals = popFinalSegments(vad)
        if (finals.isNotEmpty()) {
            events += finals
            events += Event.Partial("")
            speechBuffer = FloatArray(0)
            isInSpeech = vad.isSpeechDetected()
            samplesSinceLastPartial = 0
        } else if (isInSpeech && samplesSinceLastPartial >= PARTIAL_INTERVAL_SAMPLES) {
            samplesSinceLastPartial = 0
            events += Event.Partial(transcribe(speechBuffer))
        }

        if (!isInSpeech) {
            speechBuffer = speechBuffer.takeLast(PRE_ROLL_SAMPLES)
        }
        return events
    }

    /** Transcribes whatever is left once the recording stops. */
    fun flush(): List<Event> {
        if (vad == null) {
            val text = if (speechBuffer.isEmpty()) "" else transcribe(speechBuffer)
            reset()
            return listOfNotNull(text.takeIf(String::isNotBlank)?.let(Event::Final)) + Event.Partial("")
        }
        vad.flush()
        val events = popFinalSegments(vad) + Event.Partial("")
        reset()
        return events
    }

    /** Debug mode: no sentence detection, the audio is simply cut every [NO_VAD_CHUNK_SAMPLES]. */
    private fun acceptWithoutVad(samples: FloatArray): List<Event> {
        speechBuffer += samples
        samplesSinceLastPartial += samples.size
        return when {
            speechBuffer.size >= NO_VAD_CHUNK_SAMPLES -> {
                val text = transcribe(speechBuffer)
                speechBuffer = FloatArray(0)
                samplesSinceLastPartial = 0
                listOfNotNull(text.takeIf(String::isNotBlank)?.let(Event::Final)) + Event.Partial("")
            }
            samplesSinceLastPartial >= NO_VAD_PARTIAL_INTERVAL_SAMPLES -> {
                samplesSinceLastPartial = 0
                listOf(Event.Partial(transcribe(speechBuffer)))
            }
            else -> emptyList()
        }
    }

    private fun popFinalSegments(vad: Vad): List<Event.Final> {
        val finals = mutableListOf<Event.Final>()
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            val text = transcribe(segment.samples)
            if (text.isNotBlank()) finals += Event.Final(text)
        }
        return finals
    }

    private fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        vad?.release()
        recognizer.release()
    }

    private fun FloatArray.takeLast(n: Int): FloatArray =
        if (size <= n) this else copyOfRange(size - n, size)

    companion object {
        const val SAMPLE_RATE = 16_000

        // Set to false to debug Whisper alone, without Silero VAD sentence detection
        private const val USE_VAD = true
        private const val NO_VAD_CHUNK_SAMPLES = SAMPLE_RATE * 10
        private const val NO_VAD_PARTIAL_INTERVAL_SAMPLES = SAMPLE_RATE * 2
        private const val VAD_WINDOW_SIZE = 512
        private const val PRE_ROLL_SAMPLES = SAMPLE_RATE / 4 // 250 ms

        // Re-transcribe the current sentence at most once per second of new audio
        private const val PARTIAL_INTERVAL_SAMPLES = SAMPLE_RATE
    }
}
