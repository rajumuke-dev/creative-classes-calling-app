package com.creativeclasses.callingapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import com.creativeclasses.report.CallOutcome
import com.creativeclasses.report.CallRecord
import com.creativeclasses.report.Concern
import com.creativeclasses.report.MarksRule
import com.creativeclasses.report.ParentMessage
import com.creativeclasses.report.PhoneNumbers
import com.creativeclasses.report.SessionProgress

/** Outcomes that need nothing more, so the card minimises itself once chosen. */
private val AUTO_COLLAPSE = setOf(CallOutcome.CONTACTED, CallOutcome.WILL_VISIT, CallOutcome.CALL_LATER)

private val OUTCOME_CHOICES = listOf(
    CallOutcome.CONTACTED to "Contacted",
    CallOutcome.CONCERN_RAISED to "Concern Raised",
    CallOutcome.WILL_VISIT to "Will Come to Visit",
    CallOutcome.NO_ANSWER to "Call Not Received",
    CallOutcome.CALL_LATER to "Call Later",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallingScreen(vm: CallingViewModel) {
    val context = LocalContext.current
    val progress = SessionProgress.of(vm.records)
    val shown = vm.visibleIndices
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.resumeIndex)
    var showMarks by remember { mutableStateOf(false) }
    var speakIndex by remember { mutableStateOf<Int?>(null) }
    val opened = remember { mutableStateMapOf<Int, Boolean>() }
    var reaction by remember { mutableStateOf<Reaction?>(null) }
    /** Teacher details dialog; runs the stored action (e.g. open the text message) after saving. */
    var teacherDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val startVoice = rememberVoiceInput { spoken ->
        speakIndex?.let { i -> vm.updateRecord(i) { it.copy(remark = appendSpoken(it.remark, spoken)) } }
        speakIndex = null
    }
    val bar by animateFloatAsState(if (progress.total == 0) 0f else progress.attempted.toFloat() / progress.total, label = "progress")
    var danced by remember { mutableStateOf(false) }
    var reportAfterDance by remember { mutableStateOf(false) }
    val dance = { thenReport: Boolean ->
        Sounds.play(context, Sounds.Cue.DANCE)
        reaction = Reaction(ReactionKind.DANCE)
        danced = true
        reportAfterDance = thenReport
    }
    val openReport = {
        when {
            reaction?.kind == ReactionKind.DANCE -> reportAfterDance = true
            vm.listDone && !danced -> dance(true)
            else -> vm.prepareReport()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().cameraShake(reaction)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::goToDashboard) { Text("\u2190 Back") }
            AnimatedCount("Called ${progress.attempted} / ${progress.total}", Modifier.weight(1f))
            TextButton(onClick = { showMarks = true }) { Text("Marks") }
            TextButton(onClick = openReport) { Text("Next \u2192") }
        }
        LinearProgressIndicator(
            progress = { bar },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(8.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            strokeCap = StrokeCap.Round,
        )
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallFilter.values().forEach { f ->
                FilterChip(selected = vm.filter == f, onClick = { vm.filter = f }, label = { Text(f.label) })
            }
        }
        if (vm.filter == CallFilter.BY_MARKS) {
            var ruleMenu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    OutlinedButton(onClick = { ruleMenu = true }) { Text("${vm.marksRule.label} \u25BE") }
                    DropdownMenu(expanded = ruleMenu, onDismissRequest = { ruleMenu = false }) {
                        MarksRule.values().forEach { r ->
                            DropdownMenuItem(text = { Text(r.label) }, onClick = {
                                vm.updateMarksRule(r)
                                ruleMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = vm.threshold,
                    onValueChange = vm::updateThreshold,
                    label = { Text("Marks") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.holdsBreak().weight(1f).padding(start = 8.dp),
                )
            }
        }
        if (vm.filter == CallFilter.BY_MARKS && vm.hasMarks && vm.studentsWithoutMarks.isNotEmpty()) {
            Text(
                "Not listed, no marks entered: " + vm.studentsWithoutMarks.joinToString(", ") +
                    ". Enter their marks on the cards (All filter) to include them.",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (shown.isEmpty()) {
            val hint = if (vm.filter == CallFilter.BY_MARKS && !vm.marksReady) {
                "Choose Lower than or Higher than and enter the marks above."
            } else {
                "No students in this view."
            }
            Text(hint, Modifier.padding(16.dp))
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it }) { i ->
                val record = vm.records[i]
                val accent by animateColorAsState(
                    if (record.outcome == CallOutcome.NOT_CALLED) MaterialTheme.colorScheme.primaryContainer else outcomeColor(record.outcome),
                    label = "accent",
                )
                ElevatedCard(
                    Modifier.fillMaxWidth().animateItem().enter3D().tiltOnTouch(2f).animateContentSize(),
                    shape = CardShape,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().background(CardSheen).liveGlare().drawBehind { drawRect(accent, size = Size(6.dp.toPx(), size.height)) }.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        val expanded = opened[i] ?: (record.outcome == CallOutcome.NOT_CALLED)
                        val caret by animateFloatAsState(if (expanded) 180f else 0f, label = "caret")
                        val student = vm.students[i]
                        Row(
                            Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { opened[i] = !expanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(record.studentName, style = MaterialTheme.typography.titleSmall)
                                listOfNotNull(student.group, student.school).takeIf { it.isNotEmpty() }?.let {
                                    Text(it.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                                }
                                if (!expanded) CollapsedSummary(record)
                            }
                            Text("\u25BE", Modifier.padding(start = 8.dp).rotate(caret), style = MaterialTheme.typography.titleLarge)
                        }
                        AnimatedVisibility(expanded) { Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                ContactRow(context, "Contact 1", student.contact1)
                                ContactRow(context, "Contact 2", student.contact2)
                            }
                            OutlinedTextField(
                                value = vm.marks.getOrElse(i) { "" },
                                onValueChange = { vm.setMark(i, it) },
                                label = { Text("Marks") },
                                singleLine = true,
                                isError = vm.marksRule == MarksRule.BELOW && vm.matchesMarks(i),
                                modifier = Modifier.holdsBreak().width(96.dp),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OUTCOME_CHOICES.forEach { (outcome, label) ->
                                OutcomeChip(outcome, label, record.outcome == outcome) {
                                    val wasDone = vm.listDone
                                    val chosen = if (record.outcome == outcome) CallOutcome.NOT_CALLED else outcome
                                    vm.updateRecord(i) { it.copy(outcome = chosen) }
                                    if (chosen != CallOutcome.NOT_CALLED && !wasDone && vm.listDone) {
                                        dance(false)
                                    } else if (chosen != CallOutcome.NOT_CALLED) {
                                        Sounds.play(
                                            context,
                                            when (chosen) {
                                                CallOutcome.CONTACTED -> Sounds.Cue.CELEBRATE
                                                CallOutcome.NO_ANSWER -> Sounds.Cue.SAD
                                                CallOutcome.WILL_VISIT -> Sounds.Cue.DOORBELL
                                                CallOutcome.CONCERN_RAISED -> Sounds.Cue.FAAAH
                                                CallOutcome.CALL_LATER -> Sounds.Cue.TICK_TOCK
                                                else -> Sounds.Cue.SOFT
                                            },
                                        )
                                        when (chosen) {
                                            CallOutcome.CONTACTED -> reaction = Reaction(ReactionKind.CELEBRATE)
                                            CallOutcome.NO_ANSWER -> reaction = Reaction(ReactionKind.SAD)
                                            CallOutcome.WILL_VISIT -> reaction = Reaction(ReactionKind.VISIT)
                                            CallOutcome.CONCERN_RAISED -> reaction = Reaction(ReactionKind.CONCERN)
                                            else -> Unit
                                        }
                                    }
                                    if (chosen in AUTO_COLLAPSE) {
                                        scope.launch {
                                            delay(700)
                                            if (vm.records[i].outcome == chosen) opened[i] = false
                                        }
                                    } else {
                                        if (chosen == CallOutcome.NOT_CALLED) opened.remove(i) else opened[i] = true
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(record.outcome == CallOutcome.NO_ANSWER) {
                            val reachable = listOfNotNull(vm.students[i].contact1, vm.students[i].contact2)
                                .firstOrNull { PhoneNumbers.isValid(it) }
                            if (reachable != null) {
                                val sendText = {
                                    textParent(context, reachable, ParentMessage.notReached(record.studentName, vm.form.teacher, vm.form.availability))
                                }
                                Column {
                                    Button(
                                        onClick = { if (vm.form.availability.isBlank()) teacherDialog = sendText else sendText() },
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        elevation = raisedButton(),
                                    ) { Text("Send text message to parent") }
                                    if (vm.form.availability.isNotBlank()) {
                                        TextButton(onClick = { teacherDialog = {} }) {
                                            Text("${vm.form.teacher.ifBlank { "Teacher" }} available ${vm.form.availability} \u270E")
                                        }
                                    }
                                }
                            } else {
                                Text("No valid phone number, so no text message can be sent.", Modifier.padding(top = 8.dp))
                            }
                        }
                        AnimatedVisibility(record.outcome == CallOutcome.CONCERN_RAISED) {
                            ConcernChips(record) { c -> vm.updateRecord(i) { c(it) } }
                        }
                        OutlinedTextField(
                            value = record.remark.orEmpty(),
                            onValueChange = { v -> vm.updateRecord(i) { it.copy(remark = v) } },
                            label = { Text("Remark") },
                            modifier = Modifier.holdsBreak().fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                speakIndex = i
                                startVoice()
                            }) { Text("\uD83C\uDF99 Speak") }
                            Checkbox(
                                checked = record.followUpRequired,
                                onCheckedChange = { v -> vm.updateRecord(i) { it.copy(followUpRequired = v) } },
                            )
                            Text("Follow-up required", Modifier.weight(1f))
                            if (record.outcome != CallOutcome.NOT_CALLED) {
                                TextButton(onClick = { opened[i] = false }) { Text("Done \u2713") }
                            }
                        }
                        } }
                    }
                }
            }
        }
        Button(
            onClick = openReport,
            Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().tiltOnTouch(8f),
            elevation = raisedButton(),
        ) { Text("Share Report") }
    }
    teacherDialog?.let { after ->
        TeacherAvailabilityDialog(vm.form, onDismiss = { teacherDialog = null }) { teacher, availability ->
            vm.updateTeacher(teacher, availability)
            teacherDialog = null
            after()
        }
    }
    ReactionOverlay(reaction) {
        val thenReport = reaction?.kind == ReactionKind.DANCE && reportAfterDance
        reaction = null
        reportAfterDance = false
        if (thenReport) vm.prepareReport()
    }
    if (showMarks) MarksDialog(vm) { showMarks = false }
    vm.filterNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { vm.filterNotice = null },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { vm.filterNotice = null }) { Text("OK") } },
        )
    }
}

/** One-line preview shown on a minimised card: coloured outcome badge, follow-up flag and the remark. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsedSummary(record: CallRecord) {
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = OUTCOME_CHOICES.firstOrNull { it.first == record.outcome }?.second
        if (label != null) Badge(label, outcomeColor(record.outcome))
        if (record.followUpRequired) Badge("Follow-up", MaterialTheme.colorScheme.tertiary)
    }
    record.remark?.takeIf { it.isNotBlank() }?.let {
        Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        Modifier.background(color, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** Outcome chip that fills with its outcome colour and gives a springy bounce when chosen. */
@Composable
private fun OutcomeChip(outcome: CallOutcome, label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) outcomeColor(outcome) else Color.Transparent, label = "chipColor")
    val bounce = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(selected) {
        if (selected) {
            bounce.snapTo(0.85f)
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    FilterChip(
        selected = selected,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        label = { Text(label) },
        modifier = Modifier.scale(bounce.value),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = Color.White),
    )
}

@Composable
private fun ContactRow(context: Context, label: String, phone: String?) {
    if (phone == null) return
    val valid = PhoneNumbers.isValid(phone)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (valid) "$label: $phone" else "$label: $phone (invalid number)",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (valid) Color.Unspecified else MaterialTheme.colorScheme.error,
        )
        if (valid) TextButton(onClick = { dialPhone(context, phone) }) { Text("Call") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConcernChips(record: CallRecord, update: ((CallRecord) -> CallRecord) -> Unit) {
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Concern.values().forEach { concern ->
            FilterChip(
                selected = concern in record.concerns,
                onClick = {
                    update { it.copy(concerns = if (concern in it.concerns) it.concerns - concern else it.concerns + concern) }
                },
                label = { Text(concern.phrase.replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@Composable
private fun MarksDialog(vm: CallingViewModel, onClose: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importMarksImage(uri)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Marks") },
        text = {
            Column {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth()) {
                    Text("Read marks from image")
                }
                Text(
                    "Type AB for absent. Absent and blank students never match Lower than / Higher than. Marks are not added to the report.",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
    vm.marksNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { vm.marksNotice = null },
            title = { Text("Marks from image") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { vm.marksNotice = null }) { Text("OK") } },
        )
    }
}

/** Teacher name and when the teacher is available, told to parents who were not reached; saved with the session. */
@Composable
private fun TeacherAvailabilityDialog(form: SessionForm, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var teacher by remember { mutableStateOf(form.teacher) }
    var availability by remember { mutableStateOf(form.availability) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teacher availability") },
        text = {
            Column {
                Text("Included in the text message so the parent knows when to visit the class.")
                OutlinedTextField(
                    teacher, { teacher = it }, Modifier.holdsBreak().fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Teacher") }, singleLine = true,
                )
                OutlinedTextField(
                    availability, { availability = it }, Modifier.holdsBreak().fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Available (e.g. on Monday from 4 PM to 7 PM)") }, singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(teacher, availability) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
