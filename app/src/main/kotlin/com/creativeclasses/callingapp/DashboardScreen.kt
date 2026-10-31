package com.creativeclasses.callingapp

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.border
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.SessionProgress
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

@Composable
fun DashboardScreen(vm: CallingViewModel) {
    var pendingDelete by remember { mutableStateOf<SavedSession?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        HeroHeader("Creative Classes", "Calling Sessions")
        if (vm.sessions.isEmpty()) {
            Text("No calling sessions yet. Import an Excel file to start.", Modifier.weight(1f).padding(top = 16.dp))
        } else {
            LazyColumn(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.sessions, key = { _, s -> s.id }) { i, session ->
                    SessionCard(session, Modifier.animateItem().enter3D(80 * i.coerceAtMost(6)).tiltOnTouch(), onOpen = { vm.openSession(session) }, onDelete = { pendingDelete = session })
                }
            }
        }
        Button(onClick = vm::newSession, Modifier.fillMaxWidth().tiltOnTouch(8f), elevation = raisedButton()) { Text("New Calling Session") }
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
private fun SessionCard(session: SavedSession, modifier: Modifier, onOpen: () -> Unit, onDelete: () -> Unit) {
    val p = SessionProgress.of(session.records)
    ElevatedCard(
        modifier.fillMaxWidth().animateContentSize(),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().background(CardSheen).padding(14.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall)
            Text(session.date.format(DATE_FORMAT), style = MaterialTheme.typography.bodySmall)
            AnimatedCount("Called ${p.attempted} / ${p.total}", Modifier.padding(top = 8.dp))
            val progress by animateFloatAsState(if (p.total == 0) 0f else p.attempted.toFloat() / p.total, label = "progress")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                strokeCap = StrokeCap.Round,
            )
            Text("Contacted ${p.contacted} • Concern ${p.concerns} • Will visit ${p.willVisit} • Call not received ${p.noAnswer} • Call later ${p.callLater}")
            Text("Not called ${p.notCalled} • Follow-ups ${p.followUps}")
            p.resumeIndex?.let {
                Text("Next: ${session.records[it].studentName}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
            } ?: Text("All students have been called.", Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, elevation = raisedButton()) { Text(if (p.resumeIndex == null) "Open" else "Continue calling") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
fun Logo(size: Int = 56) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f)) }
    Box(
        Modifier.size(size.dp).scale(pop.value).clip(CircleShape).background(Color(0xFF1A56A0)).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_logo_foreground), contentDescription = "Creative Classes logo", modifier = Modifier.requiredSize((size * 1.6f).dp))
    }
}
