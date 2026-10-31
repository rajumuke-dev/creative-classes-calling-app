package com.creativeclasses.callingapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * How far the phone has just been tilted, each axis in -1..1 (x = left/right, y = forward/back).
 * It drifts back to zero while the phone is held still, so any holding angle feels neutral.
 */
val LocalDeviceTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

@Composable
fun ProvideDeviceTilt(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(context) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var neutral: Offset? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val raw = Offset(-e.values[0], e.values[1]) / SensorManager.GRAVITY_EARTH
                val base = neutral?.let { it + (raw - it) * 0.02f } ?: raw
                neutral = base
                val target = Offset((raw.x - base.x).coerceIn(-1f, 1f), (raw.y - base.y).coerceIn(-1f, 1f))
                tilt.value += (target - tilt.value) * 0.2f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accel != null && animationsEnabled(context)) sensors.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensors?.unregisterListener(listener) }
    }
    CompositionLocalProvider(LocalDeviceTilt provides tilt, content = content)
}

/** Respects the system "Remove animations" accessibility setting. */
private fun animationsEnabled(context: Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/** A soft light reflection that slides across the surface as the phone is tilted. */
fun Modifier.liveGlare(): Modifier = composed {
    val tilt = LocalDeviceTilt.current
    drawWithContent {
        drawContent()
        val t = tilt.value
        val centre = Offset(size.width * (0.5f + t.x * 0.6f), size.height * (0.2f - t.y * 0.6f))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), centre, size.maxDimension * 0.6f))
    }
}
