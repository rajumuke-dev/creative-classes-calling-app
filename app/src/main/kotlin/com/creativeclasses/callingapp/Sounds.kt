package com.creativeclasses.callingapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Short "4D cinema" cues, synthesised in code (no audio files): each note is felt as a vibration pulse at the same
 * moment it sounds, big moments get a low "boom" impact, and a short echo gives the sound some room. Sound is silenced
 * when the phone is on silent/vibrate or "Touch sounds" is off; vibration follows the system haptic setting and also
 * plays on vibrate.
 */
object Sounds {
    enum class Cue(
        val notes: DoubleArray,
        val gap: Double = 0.09,
        val decay: Double = 0.07,
        val impact: Boolean = false,
        val pulseMs: Long = 20,
        val strength: Int = 120,
    ) {
        /** Single mellow note: recorded, needs attention later. Deliberately not an "error" sound. */
        SOFT(doubleArrayOf(880.0), pulseMs = 15, strength = 90),

        /** Major arpeggio: report ready. */
        COMPLETE(doubleArrayOf(1046.50, 1318.51, 1567.98), impact = true, pulseMs = 25, strength = 160),

        /** Quick bright "ta-da" arpeggio up to the octave: parent contacted. */
        CELEBRATE(doubleArrayOf(1046.50, 1318.51, 1567.98, 2093.0), gap = 0.07, decay = 0.12, impact = true, pulseMs = 35, strength = 255),

        /** "Ding-dong" doorbell (falling major third): parent will come to visit. */
        DOORBELL(doubleArrayOf(1318.51, 1046.50), gap = 0.32, decay = 0.3, impact = true, pulseMs = 60, strength = 200),

        /** Slow falling "wah-wah" steps: call not received. */
        SAD(doubleArrayOf(783.99, 739.99, 698.46, 659.25), gap = 0.24, decay = 0.2, pulseMs = 110, strength = 70),

        /** Bouncy eight-note party tune on a steady beat: every call in the list is done. */
        DANCE(
            doubleArrayOf(523.25, 659.25, 783.99, 659.25, 698.46, 880.0, 783.99, 1046.50),
            gap = 0.18, decay = 0.1, impact = true, pulseMs = 35, strength = 220,
        ),

        /** Two slow, soft "water drop" plinks: time for a break. */
        BREAK(doubleArrayOf(1567.98, 1174.66), gap = 0.45, decay = 0.25, pulseMs = 30, strength = 100),
    }

    private const val RATE = 44100
    private const val TAIL = 0.3
    private const val ECHO = 0.11
    private val tracks = mutableMapOf<Cue, AudioTrack>()

    fun play(context: Context, cue: Cue) {
        feel(context, cue)
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

    /** One vibration pulse per note, on the note's beat; the first pulse is longer on impact cues. */
    private fun feel(context: Context, cue: Cue) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val vibrator = context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() } ?: return
        val gapMs = (cue.gap * 1000).toLong()
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        cue.notes.indices.forEach { k ->
            val on = if (k == 0 && cue.impact) cue.pulseMs * 2 else cue.pulseMs
            timings += on
            amplitudes += if (vibrator.hasAmplitudeControl()) cue.strength else VibrationEffect.DEFAULT_AMPLITUDE
            if (k < cue.notes.size - 1) {
                timings += (gapMs - on).coerceAtLeast(10)
                amplitudes += 0
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build(),
            )
        }
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

    /**
     * Bell-like notes (5 ms attack, exponential decay, a little second harmonic for warmth), an optional cinema
     * "boom" (a kick whose pitch drops from 150 to 50 Hz) under the first note, and two soft echoes for room.
     */
    private fun synth(cue: Cue): ShortArray {
        val notes = cue.notes
        val echo = (ECHO * RATE).toInt()
        val dry = DoubleArray(((cue.gap * (notes.size - 1) + TAIL + cue.decay * 3) * RATE).toInt() + echo * 2) { n ->
            val t = n.toDouble() / RATE
            var v = 0.0
            notes.forEachIndexed { k, f ->
                val local = t - k * cue.gap
                if (local >= 0) {
                    val env = min(1.0, local / 0.005) * exp(-local / cue.decay)
                    v += env * (sin(2 * PI * f * local) + 0.25 * sin(4 * PI * f * local))
                }
            }
            v / notes.size.coerceAtMost(2) + if (cue.impact) 0.8 * exp(-t / 0.12) * sin(2 * PI * (50 * t + 3 * (1 - exp(-t / 0.03)))) else 0.0
        }
        return ShortArray(dry.size) { n ->
            val wet = dry[n] + (if (n >= echo) 0.3 * dry[n - echo] else 0.0) + (if (n >= echo * 2) 0.12 * dry[n - echo * 2] else 0.0)
            (wet * 0.18 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
