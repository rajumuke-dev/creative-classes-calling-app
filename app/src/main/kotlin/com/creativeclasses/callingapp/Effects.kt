package com.creativeclasses.callingapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class ReactionKind { CELEBRATE, SAD, VISIT, DANCE, CONCERN }

/** A one-off full-screen reaction; a new [id] restarts it. */
data class Reaction(val kind: ReactionKind, val id: Long = System.nanoTime()) {
    val happy get() = kind == ReactionKind.CELEBRATE
}

/**
 * Cinema "camera shake": the screen jolts on the impact of a big moment (contacted, visit, all done) and settles
 * within half a second. Follows the system animation scale, so it is skipped when animations are off.
 */
fun Modifier.cameraShake(reaction: Reaction?): Modifier = composed {
    val shake = remember { Animatable(1f) }
    LaunchedEffect(reaction?.id) {
        if (reaction != null && reaction.kind != ReactionKind.SAD) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(450, easing = LinearEasing))
        }
    }
    graphicsLayer {
        val p = shake.value
        if (p < 1f) {
            val fall = (1 - p) * (1 - p)
            translationX = sin(p * 46) * fall * 9.dp.toPx()
            translationY = sin(p * 37 + 1) * fall * 5.dp.toPx()
            rotationZ = sin(p * 29) * fall * 0.8f
        }
    }
}

private class Bit(val x: Float, val y: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color)

private val CONFETTI = listOf(Color(0xFFF43F5E), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEC4899))

/**
 * Confetti + 🎉 for a reached parent, gentle rain + 😞 for an unanswered call. Draw-only: it never takes touches,
 * so the teacher can keep working while it plays.
 */
@Composable
fun ReactionOverlay(reaction: Reaction?, onDone: () -> Unit) {
    if (reaction == null) return
    if (reaction.kind == ReactionKind.VISIT) {
        key(reaction.id) { VisitConfirmed(onDone) }
        return
    }
    if (reaction.kind == ReactionKind.DANCE) {
        key(reaction.id) { DanceParty(reaction.id, onDone) }
        return
    }
    if (reaction.kind == ReactionKind.CONCERN) {
        key(reaction.id) { Faaah(onDone) }
        return
    }
    key(reaction.id) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            progress.animateTo(1f, tween(if (reaction.happy) 1800 else 2200, easing = LinearEasing))
            onDone()
        }
        val bits = remember {
            val r = Random(reaction.id)
            List(if (reaction.happy) 90 else 45) {
                if (reaction.happy) {
                    Bit(0.5f, 0.45f, r.nextFloat() * 2 - 1, -(0.6f + r.nextFloat() * 0.9f), r.nextFloat() * 720 - 360, CONFETTI[r.nextInt(CONFETTI.size)])
                } else {
                    Bit(r.nextFloat(), -r.nextFloat() * 0.6f, 0f, 0.9f + r.nextFloat() * 0.6f, 0f, Color(0xFF60A5FA))
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val p = progress.value
                val fade = (1 - p * p * p).coerceIn(0f, 1f)
                if (reaction.happy) {
                    val flash = (1 - p / 0.18f).coerceIn(0f, 1f)
                    if (flash > 0f) drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * flash), Color(0x00FFF7D6)), center, size.maxDimension * 0.7f))
                } else {
                    val dim = (p / 0.15f).coerceAtMost(1f) * fade
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF0F172A).copy(alpha = 0.45f * dim)), center, size.maxDimension * 0.75f))
                }
                bits.forEach { b ->
                    if (reaction.happy) {
                        val t = p * 1.8f
                        val pos = Offset(size.width * (b.x + b.vx * 0.45f * t), size.height * (b.y + b.vy * 0.55f * t + 0.6f * t * t))
                        rotate(b.spin * t, pos) {
                            drawRect(b.color.copy(alpha = fade), pos - Offset(8f, 5f), Size(16f, 10f))
                        }
                    } else {
                        val y = size.height * (b.y + b.vy * p * 1.2f)
                        val x = size.width * b.x
                        drawLine(b.color.copy(alpha = 0.7f * fade), Offset(x, y), Offset(x, y + 36f), strokeWidth = 5f, cap = StrokeCap.Round)
                    }
                }
            }
            Text(
                if (reaction.happy) "\uD83C\uDF89" else "\uD83D\uDE1E",
                Modifier.graphicsLayer {
                    val p = progress.value
                    alpha = (if (p < 0.75f) 1f else (1 - p) * 4).coerceIn(0f, 1f)
                    if (reaction.happy) {
                        val pop = (p / 0.15f).coerceAtMost(1f)
                        scaleX = 0.3f + pop * 0.9f + sin(p * 20) * 0.04f
                        scaleY = scaleX
                        rotationZ = sin(p * 14) * 12
                    } else {
                        scaleX = 0.6f + (p / 0.2f).coerceAtMost(1f) * 0.5f
                        scaleY = scaleX
                        translationY = p * 120f
                        rotationZ = sin(p * 9) * 6
                    }
                },
                fontSize = 96.sp,
            )
        }
    }
}

/** Every call is done: dancers bounce on the beat of [Sounds.Cue.DANCE] while confetti rains down. */
@Composable
private fun DanceParty(seed: Long, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(DANCE_MS, easing = LinearEasing))
        onDone()
    }
    val bits = remember {
        val r = Random(seed)
        List(80) { Bit(r.nextFloat(), -r.nextFloat(), 0f, 0.5f + r.nextFloat() * 0.5f, r.nextFloat() * 720 - 360, CONFETTI[r.nextInt(CONFETTI.size)]) }
    }
    val fade = { p: Float -> (if (p < 0.85f) 1f else (1 - p) / 0.15f).coerceIn(0f, 1f) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val a = fade(p)
            drawRect(Color.White.copy(alpha = 0.75f * a * (p / 0.08f).coerceAtMost(1f)))
            bits.forEach { b ->
                val pos = Offset(size.width * (b.x + 0.03f * sin(p * 12 + b.spin)), size.height * (b.y + b.vy * p * 2.2f))
                rotate(b.spin * p * 3, pos) { drawRect(b.color.copy(alpha = a), pos - Offset(8f, 5f), Size(16f, 10f)) }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = fade(progress.value) }) {
            Row {
                listOf("\uD83D\uDC83", "\uD83D\uDD7A", "\uD83D\uDC83").forEachIndexed { i, dancer ->
                    Text(
                        dancer,
                        Modifier.graphicsLayer {
                            val beats = progress.value * DANCE_MS / BEAT_MS
                            val bounce = abs(sin(PI.toFloat() * (beats + i * 0.5f)))
                            translationY = -bounce * 36.dp.toPx()
                            scaleY = 0.92f + bounce * 0.12f
                            scaleX = if ((beats.toInt() + i) % 2 == 0) 1f else -1f
                            rotationZ = sin(PI.toFloat() * beats / 2 + i) * 14
                        },
                        fontSize = 72.sp,
                    )
                }
            }
            Text(
                "All calls done!",
                Modifier.padding(top = 16.dp).graphicsLayer {
                    val pop = (progress.value / 0.12f).coerceAtMost(1f)
                    scaleX = 0.4f + pop * 0.6f + abs(sin(PI.toFloat() * progress.value * DANCE_MS / BEAT_MS)) * 0.05f
                    scaleY = scaleX
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Meme-style dramatic zoom for a raised concern, timed to the long [Sounds.Cue.FAAAH] shout: 😱 punches in, the screen
 * edges flush red and "FAAAHHH!" stretches out as the shout falls.
 */
@Composable
private fun Faaah(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(FAAAH_MS, easing = LinearEasing))
        onDone()
    }
    val shout = { p: Float -> (1 - p * FAAAH_MS / SHOUT_MS).coerceIn(0f, 1f) }
    val fade = { p: Float -> (if (p < 0.85f) 1f else (1 - p) / 0.15f).coerceIn(0f, 1f) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFFDC2626).copy(alpha = 0.5f * shout(p) * fade(p))), center, size.maxDimension * 0.7f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = fade(progress.value) }) {
            Text(
                "\uD83D\uDE31",
                Modifier.graphicsLayer {
                    val s = shout(progress.value)
                    scaleX = 0.8f + (1 - s) * 0.2f + s * 0.6f
                    scaleY = scaleX
                    rotationZ = sin(progress.value * 60) * 8 * s
                },
                fontSize = 96.sp,
            )
            Text(
                "FAAAHHH!",
                Modifier.padding(top = 8.dp).graphicsLayer { scaleX = 0.7f + (1 - shout(progress.value)) * 0.6f },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFFDC2626),
            )
        }
    }
}

private const val FAAAH_MS = 2000
private const val SHOUT_MS = 1300f
private const val DANCE_MS = 2600
private const val BEAT_MS = 360f

/**
 * "Visit booked" confirmation in the style of booking/calendar apps: a circle pops in, a checkmark draws itself,
 * rings ripple outward and a school icon floats up.
 */
@Composable
private fun VisitConfirmed(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1700, easing = LinearEasing))
        onDone()
    }
    val sky = outcomeColor(com.creativeclasses.report.CallOutcome.WILL_VISIT)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val fade = (if (p < 0.75f) 1f else (1 - p) * 4).coerceIn(0f, 1f)
            val r = 70.dp.toPx()
            listOf(0f, 0.15f, 0.3f).forEach { start ->
                val q = ((p - start) / 0.6f).coerceIn(0f, 1f)
                if (q > 0f && q < 1f) drawCircle(sky.copy(alpha = (1 - q) * 0.5f), r * (1 + q * 1.6f), center, style = Stroke(4.dp.toPx()))
            }
            val pop = (p / 0.18f).coerceAtMost(1f)
            val scale = pop * (1 + 0.15f * sin(pop * PI.toFloat()))
            drawCircle(sky.copy(alpha = fade), r * scale, center)
            val tick = Path().apply {
                moveTo(center.x - r * 0.42f, center.y + r * 0.02f)
                lineTo(center.x - r * 0.1f, center.y + r * 0.34f)
                lineTo(center.x + r * 0.45f, center.y - r * 0.3f)
            }
            val drawn = Path()
            val measure = PathMeasure().apply { setPath(tick, false) }
            measure.getSegment(0f, measure.length * ((p - 0.18f) / 0.3f).coerceIn(0f, 1f), drawn, true)
            drawPath(drawn, Color.White.copy(alpha = fade), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text(
            "\uD83C\uDFEB",
            Modifier.graphicsLayer {
                val p = progress.value
                val q = ((p - 0.3f) / 0.4f).coerceIn(0f, 1f)
                alpha = (if (p < 0.8f) q else (1 - p) * 5).coerceIn(0f, 1f)
                translationY = -110.dp.toPx() - q * 40.dp.toPx()
                rotationZ = sin(p * 12) * 6
            },
            fontSize = 56.sp,
        )
    }
}
