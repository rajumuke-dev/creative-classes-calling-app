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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Short "4D cinema" cues, synthesised in code (no audio files): each note is felt as a vibration pulse at the same
 * moment it sounds, big moments get a low "boom" impact, and a short echo gives the sound some room. Sound plays at the
 * phone's media volume (like reels; turn media volume down to mute it); vibration follows the system haptic setting and
 * also plays on vibrate.
 */
object Sounds {
    /** Timbre: a struck bell, a brass horn, or a shouting voice ("aah" vowel). */
    enum class Voice { BELL, BRASS, SHOUT }

    enum class Cue(
        val notes: DoubleArray,
        val gap: Double = 0.09,
        val decay: Double = 0.07,
        val impact: Boolean = false,
        val pulseMs: Long = 20,
        val strength: Int = 120,
        val voice: Voice = Voice.BELL,
    ) {
        /** Single mellow note: recorded, needs attention later. Deliberately not an "error" sound. */
        SOFT(doubleArrayOf(880.0), pulseMs = 15, strength = 90),

        /** Major arpeggio: report ready. */
        COMPLETE(doubleArrayOf(1046.50, 1318.51, 1567.98), impact = true, pulseMs = 25, strength = 160),

        /** Quick bright "ta-da" arpeggio up to the octave: parent contacted. */
        CELEBRATE(doubleArrayOf(1046.50, 1318.51, 1567.98, 2093.0), gap = 0.07, decay = 0.12, impact = true, pulseMs = 35, strength = 255),

        /** "Ding-dong" doorbell (falling major third): parent will come to visit. */
        DOORBELL(doubleArrayOf(1318.51, 1046.50), gap = 0.32, decay = 0.3, impact = true, pulseMs = 60, strength = 200),

        /** Sad trombone "wah wah wah waaah", the last note wobbling down: call not received. */
        SAD(doubleArrayOf(233.08, 220.0, 207.65, 196.0), gap = 0.42, decay = 1.0, pulseMs = 110, strength = 70, voice = Voice.BRASS),

        /** One long dramatic falling "FAAAHHH" shout with echo, in the style of the trending meme: concern raised. */
        FAAAH(doubleArrayOf(320.0), decay = 1.3, pulseMs = 1300, strength = 255, voice = Voice.SHOUT),

        /** Clock "tick-tock": call later. */
        TICK_TOCK(doubleArrayOf(1800.0, 1350.0, 1800.0, 1350.0), gap = 0.28, decay = 0.02, pulseMs = 15, strength = 140),

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
    private const val RELEASE = 0.15
    private const val VIBRATO_HZ = 5.5

    /** Vocal-tract resonances (centre Hz, bandwidth Hz, gain) for an open "aah", and a broad bright brass body; flat to avoid boxing. */
    private val AAH = doubleArrayOf(800.0, 80.0, 1.0, 1150.0, 90.0, 0.6, 2900.0, 120.0, 0.25)
    private val HORN = doubleArrayOf(500.0, 400.0, 0.6, 1200.0, 600.0, 1.0)
    private val tracks = java.util.concurrent.ConcurrentHashMap<Cue, AudioTrack>()

    /** Synthesises every cue off the main thread at launch so the first tap never waits (voiced cues take ~0.3 s). */
    fun warmUp() = Thread { Cue.values().forEach { cue -> runCatching { tracks.computeIfAbsent(cue, ::build) } } }.start()

    fun play(context: Context, cue: Cue) {
        feel(context, cue)
        runCatching {
            val track = tracks.computeIfAbsent(cue, ::build)
            track.stop()
            track.reloadStaticData()
            track.play()
        }
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
                    .setUsage(AudioAttributes.USAGE_GAME)
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
     * Each cue is its notes in [Cue.voice] plus an optional cinema "boom" (a kick whose pitch drops from 150 to 50 Hz)
     * under the first note, then two soft echoes for room. Voiced cues are normalised so they are as loud as the bells.
     */
    private fun synth(cue: Cue): ShortArray {
        val notes = cue.notes
        val echo = (ECHO * RATE).toInt()
        val ring = if (cue.voice == Voice.BELL) cue.decay * 3 else cue.decay + RELEASE
        val dry = DoubleArray(((cue.gap * (notes.size - 1) + TAIL + ring) * RATE).toInt() + echo * 2) { n ->
            val t = n.toDouble() / RATE
            var v = 0.0
            notes.forEachIndexed { k, f ->
                val local = t - k * cue.gap
                if (local >= 0) v += note(cue, k, f, local)
            }
            v / notes.size.coerceAtMost(2) + if (cue.impact) 0.8 * exp(-t / 0.12) * sin(2 * PI * (50 * t + 3 * (1 - exp(-t / 0.03)))) else 0.0
        }
        if (cue.voice != Voice.BELL) {
            val peak = dry.maxOf { abs(it) }
            if (peak > 0) dry.indices.forEach { dry[it] *= 3.5 / peak }
        }
        return ShortArray(dry.size) { n ->
            val wet = dry[n] + (if (n >= echo) 0.3 * dry[n - echo] else 0.0) + (if (n >= echo * 2) 0.12 * dry[n - echo * 2] else 0.0)
            (wet * 0.18 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun note(cue: Cue, k: Int, f: Double, t: Double): Double {
        val last = k == cue.notes.size - 1
        return when (cue.voice) {
            Voice.BELL -> min(1.0, t / 0.005) * exp(-t / cue.decay) * (sin(2 * PI * f * t) + 0.25 * sin(4 * PI * f * t))
            Voice.BRASS -> voiced(f, t, if (last) cue.decay else cue.gap * 0.9, if (last) 0.06 else 0.0, if (last) 0.03 else 0.0, HORN)
            Voice.SHOUT -> {
                val breath = noise(t) * if (t < 0.09) 0.6 * (1 - t / 0.09) else if (t < cue.decay) 0.05 else 0.0
                voiced(f, t, cue.decay, 0.45, 0.02, AAH) + breath
            }
        }
    }

    /**
     * A sustained voiced tone that glides down by [bend] over [len] with [vibrato], harmonics shaped by [formants] so it
     * sounds like a voice or a horn; swells in over 40 ms and releases quickly.
     */
    private fun voiced(f0: Double, t: Double, len: Double, bend: Double, vibrato: Double, formants: DoubleArray): Double {
        if (t > len + RELEASE) return 0.0
        val env = min(1.0, t / 0.04) * if (t > len) exp(-(t - len) / 0.05) else 1.0
        val freq = f0 * (1 - bend * t / len)
        val phase = 2 * PI * f0 * (t - bend * t * t / (2 * len)) + vibrato * f0 * (1 - cos(2 * PI * VIBRATO_HZ * t)) / VIBRATO_HZ
        var v = 0.0
        // sin(h·phase) by recurrence: one sin/cos per sample instead of one sin per harmonic.
        val twoCos = 2 * cos(phase)
        var prev = 0.0
        var cur = sin(phase)
        for (h in 1..(5000 / freq).toInt().coerceAtMost(30)) {
            var weight = 0.0
            for (i in formants.indices step 3) {
                val d = (h * freq - formants[i]) / formants[i + 1]
                weight += formants[i + 2] / (1 + d * d)
            }
            v += weight * cur
            val next = twoCos * cur - prev
            prev = cur
            cur = next
        }
        return env * v
    }

    /** Cheap deterministic white noise for the "F" of the shout. */
    private fun noise(t: Double): Double = (sin(t * RATE * 12.9898) * 43758.5453).let { it - floor(it) } * 2 - 1
}
