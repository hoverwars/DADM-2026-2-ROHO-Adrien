package com.example.demo_oral.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Soft, short feedback sounds played when the microphone is switched on and off.
 *
 * The tones are synthesized (no audio asset): a sine with a short attack and a smooth fade out
 * so there is no click or abrupt cut.
 */
class BeepPlayer : AutoCloseable {

    private val startTrack = createTrack(START_FREQUENCY_HZ)
    private val stopTrack = createTrack(STOP_FREQUENCY_HZ)

    fun playStart() = play(startTrack)

    fun playStop() = play(stopTrack)

    private fun play(track: AudioTrack) {
        try {
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: IllegalStateException) {
            // Feedback sound only, never worth crashing for
        }
    }

    private fun createTrack(frequencyHz: Double): AudioTrack {
        val samples = synthesize(frequencyHz)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Media, not sonification: the latter is muted when the phone is on silent
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private fun synthesize(frequencyHz: Double): ShortArray {
        val total = SAMPLE_RATE * DURATION_MS / 1000
        val attack = SAMPLE_RATE * ATTACK_MS / 1000
        return ShortArray(total) { i ->
            val wave = sin(2 * PI * frequencyHz * i / SAMPLE_RATE)
            val envelope = if (i < attack) {
                i.toDouble() / attack
            } else {
                // Cosine fade out from 1 to 0 over the rest of the sound
                val progress = (i - attack).toDouble() / (total - attack)
                (1 + cos(PI * progress)) / 2
            }
            (wave * envelope * VOLUME * Short.MAX_VALUE).toInt().toShort()
        }
    }

    override fun close() {
        startTrack.release()
        stopTrack.release()
    }

    companion object {
        /** Time the start sound needs, the mic should wait for it so it does not record it. */
        const val START_SOUND_DURATION_MS = 180L

        private const val SAMPLE_RATE = 44_100
        private const val DURATION_MS = 180
        private const val ATTACK_MS = 15
        private const val VOLUME = 0.3
        private const val START_FREQUENCY_HZ = 880.0 // A5, rising feel
        private const val STOP_FREQUENCY_HZ = 587.0 // D5, lower = closing
    }
}
