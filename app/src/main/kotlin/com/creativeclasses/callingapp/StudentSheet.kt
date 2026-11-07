package com.creativeclasses.callingapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.CallOutcome
import com.creativeclasses.report.CallRecord
import com.creativeclasses.report.Marks
import com.creativeclasses.report.MarksRule
import com.creativeclasses.report.needsFollowUp

private class StudentGroup(val label: String, val color: Color, val match: (CallRecord) -> Boolean)

/** Labels match the dashboard counts, so a tapped count opens its own list. */
@Composable
private fun studentGroups(): List<StudentGroup> {
    val scheme = MaterialTheme.colorScheme
    fun of(label: String, outcome: CallOutcome) = StudentGroup(label, outcomeColor(outcome)) { it.outcome == outcome }
    return listOf(
        StudentGroup("Called", scheme.primary) { it.outcome != CallOutcome.NOT_CALLED },
        of("Contacted", CallOutcome.CONTACTED),
        of("Concern", CallOutcome.CONCERN_RAISED),
        of("Will visit", CallOutcome.WILL_VISIT),
        of("Not received", CallOutcome.NO_ANSWER),
        of("Call later", CallOutcome.CALL_LATER),
        of("Not called", CallOutcome.NOT_CALLED),
        StudentGroup("Follow-ups", scheme.tertiary) { it.needsFollowUp() },
    )
}

/**
 * Swipe-up sheet listing the students behind a dashboard count with their marks, like the drill-down sheets of
 * Google Maps or Prime Video: chips switch between counts without closing it, and tapping a student shows their call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSheet(session: SavedSession, startGroup: String, onClose: () -> Unit) {
    val groups = studentGroups()
    var selected by rememberSaveable { mutableStateOf(startGroup) }
    var byMarks by rememberSaveable { mutableStateOf(false) }
    val group = groups.firstOrNull { it.label == selected } ?: groups.first()
    val hasMarks = session.marks.any { it.isNotBlank() }
    val haptics = LocalHapticFeedback.current
    val threshold = session.threshold.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(session.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(group.color, group.color.copy(alpha = 0.7f)))).liveGlare()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(group.label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White)
                AnimatedCount(
                    "${session.records.count(group.match)}",
                    style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                )
            }
            val chips = rememberLazyListState()
            // Keeps the selected chip in view, e.g. "Not received" opened from its dashboard tile.
            LaunchedEffect(selected) { chips.animateScrollToItem((groups.indexOfFirst { it.label == selected } - 1).coerceAtLeast(0)) }
            LazyRow(state = chips, modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it.label }) { g ->
                    FilterChip(
                        selected = g.label == selected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selected = g.label
                        },
                        label = { Text("${g.label} ${session.records.count(g.match)}") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = g.color, selectedLabelColor = Color.White),
                    )
                }
            }
            if (hasMarks) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sort", style = MaterialTheme.typography.labelMedium)
                    FilterChip(selected = !byMarks, onClick = { byMarks = false }, label = { Text("Roll no.") })
                    FilterChip(
                        selected = byMarks,
                        onClick = { byMarks = true },
                        label = { Text(if (session.marksRule == MarksRule.ABOVE) "Highest marks" else "Lowest marks") },
                    )
                }
            }
        }
        AnimatedContent(group.label to byMarks, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "group") { (label, sorted) ->
            val shownGroup = groups.first { it.label == label }
            val indices = session.records.indices.filter { shownGroup.match(session.records[it]) }.let { list ->
                if (!sorted) list
                else list.sortedWith(
                    compareBy<Int> { Marks.parse(session.marks.getOrNull(it)).score == null }.thenBy {
                        val score = Marks.parse(session.marks.getOrNull(it)).score ?: 0.0
                        if (session.marksRule == MarksRule.ABOVE) -score else score
                    },
                )
            }
            if (indices.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("\uD83C\uDF89  No students here yet.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(indices, key = { _, i -> i }) { pos, i ->
                        StudentRow(session, i, mixed = label == "Called" || label == "Follow-ups", threshold, Modifier.enter3D((pos * 30).coerceAtMost(300)))
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentRow(session: SavedSession, index: Int, mixed: Boolean, threshold: Double?, modifier: Modifier) {
    val record = session.records[index]
    val student = session.students.getOrNull(index)
    val mark = session.marks.getOrNull(index).orEmpty()
    val color = outcomeColor(record.outcome)
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    // The marks pill is tinted when the student is in the session's marks filter, or absent.
    val markColor = when {
        Marks.isAbsent(mark) -> outcomeColor(CallOutcome.NO_ANSWER)
        Marks.matches(mark, session.marksRule, threshold) ->
            if (session.marksRule == MarksRule.BELOW) outcomeColor(CallOutcome.CONCERN_RAISED) else outcomeColor(CallOutcome.CONTACTED)
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).tiltOnTouch(3f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                open = !open
            }.animateContentSize(),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", Modifier.width(34.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(record.studentName, style = MaterialTheme.typography.titleSmall)
                    if (mixed) Text(OUTCOME_CHOICES.toMap()[record.outcome] ?: "Not called", style = MaterialTheme.typography.labelSmall, color = color)
                }
                if (mark.isNotBlank()) {
                    Text(
                        mark,
                        Modifier.clip(RoundedCornerShape(50)).background(markColor.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = markColor,
                    )
                }
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                Detail("Status", OUTCOME_CHOICES.toMap()[record.outcome] ?: "Not called")
                if (record.concerns.isNotEmpty()) Detail("Concerns", record.concerns.joinToString { it.phrase.replaceFirstChar { c -> c.uppercase() } })
                record.remark?.takeIf { it.isNotBlank() }?.let { Detail("Remark", it) }
                if (record.needsFollowUp()) Detail("Follow-up", "Yes")
                listOfNotNull(student?.contact1, student?.contact2).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                    ?.let { Detail("Contact", it.joinToString(" / ")) }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.padding(top = 2.dp)) {
        Text(label, Modifier.width(84.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
