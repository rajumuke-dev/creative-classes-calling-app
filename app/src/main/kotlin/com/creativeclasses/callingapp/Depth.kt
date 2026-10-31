package com.creativeclasses.callingapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shared "raised" look: shadows under cards, buttons and chips give the UI depth. */
val CardShape = RoundedCornerShape(18.dp)

@Composable
fun raisedButton() = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 1.dp)

/**
 * Presses the element in and tilts it toward the finger, like a physical tile. Touches are only observed, never
 * consumed, so buttons and text fields inside keep working; the tilt is released as soon as the finger scrolls.
 * It also leans gently with the phone itself, so surfaces feel like live objects.
 */
fun Modifier.tiltOnTouch(maxTilt: Float = 5f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    val bouncy = spring<Float>(dampingRatio = 0.45f, stiffness = 420f)
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, bouncy, label = "pressScale")
    val rx by animateFloatAsState(if (pressed) tiltX else 0f, bouncy, label = "tiltX")
    val ry by animateFloatAsState(if (pressed) tiltY else 0f, bouncy, label = "tiltY")
    val device = LocalDeviceTilt.current
    pointerInput(maxTilt) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun aim(p: Offset) {
                tiltY = (p.x / size.width - 0.5f) * 2 * maxTilt
                tiltX = -(p.y / size.height - 0.5f) * 2 * maxTilt
            }
            aim(down.position)
            pressed = true
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > slop) pressed = false else aim(change.position)
            }
            pressed = false
        }
    }.graphicsLayer {
        scaleX = scale
        scaleY = scale
        val lean = device.value
        rotationX = rx + lean.y * 6f
        rotationY = ry + lean.x * 6f
        cameraDistance = 14 * density
    }
}

/** Swings the element in from behind (3D flip down) when it first appears. */
fun Modifier.enter3D(delayMillis: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 180f))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        rotationX = (1 - p) * 35f
        translationY = (1 - p) * 48.dp.toPx()
        transformOrigin = TransformOrigin(0.5f, 0f)
        cameraDistance = 14 * density
    }
}
