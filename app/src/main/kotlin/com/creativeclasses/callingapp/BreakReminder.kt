package com.creativeclasses.callingapp

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

/**
 * "Take a break" reminder modelled on YouTube's: after [minutes] of continuous use a reminder appears that can be
 * dismissed or have its frequency changed. Time on parent calls counts as work; being away from the app for
 * [AWAY_RESET_MS] (or closing it) counts as a break and restarts the timer.
 */
class BreakTimer(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var minutes by mutableIntStateOf(prefs.getInt(KEY_MINUTES, 15))
        private set
    var due by mutableStateOf(false)
        private set
    private var workStart = SystemClock.elapsedRealtime()
    private var awayAt = 0L

    fun onAppStart() {
        if (awayAt > 0 && SystemClock.elapsedRealtime() - awayAt >= AWAY_RESET_MS) workStart = SystemClock.elapsedRealtime()
        awayAt = 0
    }

    fun onAppStop() {
        awayAt = SystemClock.elapsedRealtime()
    }

    /** Runs while the app is on screen; a reminder that fell due during a call shows on return. */
    suspend fun run() {
        while (true) {
            snapshotFlow { due }.first { !it }
            delay(workStart + minutes * 60_000L - SystemClock.elapsedRealtime())
            due = true
        }
    }

    fun dismiss() {
        workStart = SystemClock.elapsedRealtime()
        due = false
    }

    fun changeMinutes(value: Int) {
        minutes = value
        prefs.edit().putInt(KEY_MINUTES, value).apply()
    }

    companion object {
        val CHOICES = listOf(15, 30, 45, 60)
        private const val KEY_MINUTES = "breakMinutes"
        private const val AWAY_RESET_MS = 30 * 60_000L
    }
}

@Composable
fun BreakReminder(timer: BreakTimer) {
    if (!timer.due) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { Sounds.play(context, Sounds.Cue.BREAK) }
    var changing by rememberSaveable { mutableStateOf(false) }
    val worked = rememberSaveable { timer.minutes }
    val quote = rememberSaveable { HEALTH_QUOTES.random() }
    AlertDialog(
        onDismissRequest = timer::dismiss,
        icon = { WaterGlass() },
        title = { Text("Time for a short break") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("You've been working for $worked minutes. Drink a glass of water, stretch and rest your eyes.")
                Text("\u201C${quote.first}\u201D \u2014 ${quote.second}", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
                if (changing) {
                    Text("Remind me every (minutes)")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BreakTimer.CHOICES.forEach { m ->
                            FilterChip(selected = timer.minutes == m, onClick = { timer.changeMinutes(m) }, label = { Text("$m") })
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = timer::dismiss, elevation = raisedButton()) { Text("OK") } },
        dismissButton = { if (!changing) TextButton(onClick = { changing = true }) { Text("Change reminder") } },
    )
}

private val HEALTH_QUOTES = listOf(
    "Take care of your body. It's the only place you have to live." to "Jim Rohn",
    "The greatest wealth is health." to "Virgil",
    "Water is the driving force of all nature." to "Leonardo da Vinci",
    "Almost everything will work again if you unplug it for a few minutes, including you." to "Anne Lamott",
    "He who has health has hope, and he who has hope has everything." to "Arabian proverb",
    "Rest when you're weary. Refresh and renew yourself, your body, your mind, your spirit." to "Ralph Marston",
)

/** A glass that fills with gently waving water while drops fall into it and bubbles rise. */
@Composable
private fun WaterGlass() {
    val loop = rememberInfiniteTransition(label = "water")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val fill = remember { Animatable(0.1f) }
    LaunchedEffect(Unit) { fill.animateTo(0.75f, tween(2200)) }
    val water = Color(0xFF38BDF8)
    Canvas(Modifier.size(96.dp)) {
        val w = size.width
        val h = size.height
        val top = h * 0.22f
        val glass = Path().apply {
            moveTo(w * 0.24f, top)
            lineTo(w * 0.32f, h * 0.95f)
            lineTo(w * 0.68f, h * 0.95f)
            lineTo(w * 0.76f, top)
        }
        val inside = Path().apply { addPath(glass); close() }
        clipPath(inside) {
            val level = h * 0.95f - (h * 0.95f - top) * fill.value
            val wave = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    lineTo(x, level + sin((x / w * 2 + t * 2) * PI.toFloat()) * h * 0.025f)
                    x += w / 24
                }
                lineTo(w, h)
                close()
            }
            drawPath(wave, water.copy(alpha = 0.75f))
            listOf(0.42f to 0f, 0.55f to 0.4f, 0.48f to 0.7f).forEach { (bx, off) ->
                val q = (t + off) % 1f
                val y = h * 0.92f - (h * 0.92f - level) * q
                drawCircle(Color.White.copy(alpha = 0.8f * (1 - q)), h * 0.025f, Offset(w * bx, y))
            }
        }
        drawPath(glass, Color(0xFF64748B), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val dropY = (top + h * 0.1f) * t
        drawCircle(water.copy(alpha = 1 - t), h * 0.04f, Offset(w / 2, dropY))
    }
}
