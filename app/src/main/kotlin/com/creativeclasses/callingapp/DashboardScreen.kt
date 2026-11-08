package com.creativeclasses.callingapp

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.CallOutcome
import com.creativeclasses.report.SessionProgress
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

@Composable
fun DashboardScreen(vm: CallingViewModel) {
    var pendingDelete by remember { mutableStateOf<SavedSession?>(null) }
    var breakSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        HeroHeader("Creative Classes", "Calling Sessions")
        if (vm.sessions.isEmpty()) {
            Text("No calling sessions yet. Import an Excel file to start.", Modifier.weight(1f).padding(top = 16.dp))
        } else {
            LazyColumn(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.sessions, key = { _, s -> s.id }) { i, session ->
                    SessionCard(
                        session,
                        Modifier.animateItem().enter3D(80 * i.coerceAtMost(6)).tiltOnTouch(),
                        onOpen = { vm.openSession(session) },
                        onDelete = { pendingDelete = session },
                        onShare = { vm.reportFor(session)?.let { shareReport(context, it) } },
                        onShareExcel = { shareExcel(context) { vm.exportExcel(session) } },
                    )
                }
            }
        }
        Button(onClick = vm::newSession, Modifier.fillMaxWidth().tiltOnTouch(8f), elevation = raisedButton()) { Text("New Calling Session") }
        val every = vm.breakTimer.minutes
        TextButton(onClick = { breakSettings = true }, Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (every > 0) "\u2615 Break reminder: every $every min" else "\u2615 Break reminder: off")
        }
    }

    if (breakSettings) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { breakSettings = false },
            title = { Text("Take a break") },
            text = { BreakSettings(vm.breakTimer) },
            confirmButton = { TextButton(onClick = { breakSettings = false }) { Text("Done") } },
        )
    }

    pendingDelete?.let { session ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("${session.title} and all its recorded calls will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(session.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionCard(
    session: SavedSession,
    modifier: Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onShareExcel: () -> Unit,
) {
    val p = SessionProgress.of(session.records)
    val next = session.nextInList()
    var sheet by remember { mutableStateOf<String?>(null) }
    sheet?.let { StudentSheet(session, it) { sheet = null } }
    ElevatedCard(
        modifier.fillMaxWidth().animateContentSize(),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().background(CardSheen).liveGlare().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(if (p.total == 0) 0f else p.attempted.toFloat() / p.total, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.titleSmall)
                    Text(session.date.format(DATE_FORMAT), style = MaterialTheme.typography.bodySmall)
                }
                AnimatedCount(
                    "${p.attempted} / ${p.total} called",
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { sheet = "Called" }.padding(4.dp),
                )
            }
            val onTile = { t: StatTile -> sheet = t.label }
            StatRow(
                onTile,
                StatTile(p.contacted, "Contacted", CallOutcome.CONTACTED),
                StatTile(p.concerns, "Concern", CallOutcome.CONCERN_RAISED),
                StatTile(p.willVisit, "Will visit", CallOutcome.WILL_VISIT),
            )
            StatRow(
                onTile,
                StatTile(p.noAnswer, "Not received", CallOutcome.NO_ANSWER),
                StatTile(p.callLater, "Call later", CallOutcome.CALL_LATER),
                StatTile(p.notCalled, "Not called", CallOutcome.NOT_CALLED),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        next != null -> "Next: ${session.records[next].studentName}"
                        p.resumeIndex == null -> "All students have been called."
                        else -> "All students in this list have been called."
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // A pending follow-up gently pulses so it is not missed.
                val pulse = if (p.followUps > 0) {
                    rememberInfiniteTransition(label = "followUps").animateFloat(
                        1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
                    ).value
                } else 1f
                Text(
                    "Follow-ups ${p.followUps}",
                    Modifier.scale(pulse).clip(RoundedCornerShape(8.dp)).clickable { sheet = "Follow-ups" }.padding(4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (session.savedReport != null) {
                Text("\uD83D\uDCC4 Report saved", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = onOpen, Modifier.fillMaxWidth().padding(top = 10.dp), elevation = raisedButton()) {
                Text(if (p.resumeIndex == null) "Open" else "Continue calling")
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare, Modifier.weight(1f)) { Text("Share Report") }
                OutlinedButton(onClick = onShareExcel) { Text("Excel") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private class StatTile(val value: Int, val label: String, val outcome: CallOutcome)

/** Three equal-width count tiles, tinted with the outcome colour used on the calling screen. Tapping one opens its students in [StudentSheet]. */
@Composable
private fun StatRow(onClick: (StatTile) -> Unit, vararg tiles: StatTile) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.forEach { t ->
            val color = outcomeColor(t.outcome)
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val bounce by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.35f), label = "bounce")
            Column(
                Modifier.weight(1f).scale(bounce).tiltOnTouch().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f))
                    .clickable(press, LocalIndication.current) { onClick(t) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${countUp(t.value)}", style = MaterialTheme.typography.titleMedium, color = color)
                Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** [target] counted up from 0 when first shown, then animated on every change. */
@Composable
private fun countUp(target: Int): Int {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    return animateIntAsState(if (shown) target else 0, tween(900, easing = FastOutSlowInEasing), label = "countUp").value
}

/** Called share as a ring that fills from empty when the card appears, with the percentage in the middle. */
@Composable
private fun ProgressRing(fraction: Float, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sweep by animateFloatAsState(if (shown) fraction else 0f, tween(1100, easing = FastOutSlowInEasing), label = "ring")
    val track = MaterialTheme.colorScheme.primaryContainer
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(fill, -90f, 360f * sweep, false, style = stroke)
        }
        Text("${(sweep * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = fill)
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier, size: Int = 56) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f)) }
    Box(
        modifier.size(size.dp).scale(pop.value).clip(CircleShape).background(Color(0xFF1A56A0)).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_logo_foreground), contentDescription = "Creative Classes logo", modifier = Modifier.requiredSize((size * 1.6f).dp))
    }
}
