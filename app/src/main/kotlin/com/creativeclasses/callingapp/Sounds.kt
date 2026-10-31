package com.creativeclasses.callingapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.provider.Settings
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Short, soft, harmonious interface sounds, synthesised in code (no audio files). Played only for key moments and
 * silenced when the phone is on silent/vibrate or the system "Touch sounds" setting is off.
 */
object Sounds {
    enum class Cue(val notes: DoubleArray, val gap: Double = 0.09, val decay: Double = 0.07) {
        /** Single mellow note: recorded, needs attention later. Deliberately not an "error" sound. */
        SOFT(doubleArrayOf(880.0)),

        /** Major arpeggio: report ready. */
        COMPLETE(doubleArrayOf(1046.50, 1318.51, 1567.98)),

        /** Quick bright "ta-da" arpeggio up to the octave: parent contacted. */
        CELEBRATE(doubleArrayOf(1046.50, 1318.51, 1567.98, 2093.0), gap = 0.07, decay = 0.12),

        /** "Ding-dong" doorbell (falling major third): parent will come to visit. */
        DOORBELL(doubleArrayOf(1318.51, 1046.50), gap = 0.32, decay = 0.3),

        /** Slow falling "wah-wah" steps: call not received. */
        SAD(doubleArrayOf(783.99, 739.99, 698.46, 659.25), gap = 0.24, decay = 0.2),

        /** Bouncy eight-note party tune on a steady beat: every call in the list is done. */
        DANCE(doubleArrayOf(523.25, 659.25, 783.99, 659.25, 698.46, 880.0, 783.99, 1046.50), gap = 0.18, decay = 0.1),

        /** Two slow, soft "water drop" plinks: time for a break. */
        BREAK(doubleArrayOf(1567.98, 1174.66), gap = 0.45, decay = 0.25),
    }

    private const val RATE = 44100
    private const val TAIL = 0.3
    private val tracks = mutableMapOf<Cue, AudioTrack>()

    fun play(context: Context, cue: Cue) {
        if (!enabled(context)) return
        runCatching {
            val track = tracks.getOrPut(cue) { build(cue) }
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun enabled(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0
    }

    private fun build(cue: Cue): AudioTrack {
        val pcm = synth(cue)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }

    /** Bell-like notes: 5 ms attack, exponential decay, a little second harmonic for warmth. */
    private fun synth(cue: Cue): ShortArray {
        val notes = cue.notes
        val total = ((cue.gap * (notes.size - 1) + TAIL + cue.decay * 3) * RATE).toInt()
        return ShortArray(total) { n ->
            val t = n.toDouble() / RATE
            var v = 0.0
            notes.forEachIndexed { k, f ->
                val local = t - k * cue.gap
                if (local >= 0) {
                    val env = min(1.0, local / 0.005) * exp(-local / cue.decay)
                    v += env * (sin(2 * PI * f * local) + 0.25 * sin(4 * PI * f * local))
                }
            }
            (v / notes.size.coerceAtMost(2) * 0.18 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
