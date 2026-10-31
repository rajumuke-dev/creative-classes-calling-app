package com.creativeclasses.callingapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.CallOutcome

private val Indigo = Color(0xFF4F46E5)
private val Violet = Color(0xFF7C3AED)
private val Pink = Color(0xFFDB2777)
private val Sky = Color(0xFF0EA5E9)

val BackgroundBrush = Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFF5F3FF), Color(0xFFFCE7F3)))

private val Colors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Violet,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Pink,
    surface = Color(0xFFFDFCFF),
    surfaceContainerLow = Color.White,
)

private val Type = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type, content = content)

/** Colour shown on a selected outcome button, so each result is recognisable at a glance. */
fun outcomeColor(outcome: CallOutcome): Color = when (outcome) {
    CallOutcome.CONTACTED -> Color(0xFF16A34A)
    CallOutcome.CONCERN_RAISED -> Color(0xFFDC2626)
    CallOutcome.WILL_VISIT -> Sky
    CallOutcome.NO_ANSWER -> Color(0xFFEA580C)
    CallOutcome.CALL_LATER -> Color(0xFFCA8A04)
    CallOutcome.NOT_CALLED -> Indigo
}

/** Soft top-lit sheen drawn over card backgrounds. */
val CardSheen = Brush.verticalGradient(listOf(Color(0xFFEEF2FF), Color.White, Color(0xFFFAF5FF)))

/** Text that rolls up like a counter whenever its value changes. */
@Composable
fun AnimatedCount(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
        modifier = modifier,
        label = "count",
    ) { Text(it, style = style) }
}

/** Glossy banner with a slowly moving gradient, the logo and white title text. */
@Composable
fun HeroHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val shift by rememberInfiniteTransition(label = "hero").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().enter3D().shadow(14.dp, shape, ambientColor = Violet, spotColor = Violet).clip(shape)
            .drawBehind {
                val w = size.width
                drawRect(Brush.linearGradient(listOf(Indigo, Violet, Pink), Offset(-w + 2 * w * shift, 0f), Offset(w * shift + w, size.height)))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), endY = size.height * 0.55f))
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo()
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
}
