package com.creativeclasses.callingapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Card
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
import com.creativeclasses.report.PhoneNumbers
import com.creativeclasses.report.SessionProgress

private val OUTCOME_CHOICES = listOf(
    CallOutcome.CONTACTED to "Contacted",
    CallOutcome.CONCERN_RAISED to "Concern Raised",
    CallOutcome.NO_ANSWER to "No Answer",
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
    val bar by animateFloatAsState(if (progress.total == 0) 0f else progress.attempted.toFloat() / progress.total, label = "progress")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::goToDashboard) { Text("Dashboard") }
            Text(
                "Called ${progress.attempted} / ${progress.total}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { showMarks = true }) { Text("Marks") }
        }
        LinearProgressIndicator(progress = { bar }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallFilter.values().forEach { f ->
                FilterChip(selected = vm.filter == f, onClick = { vm.filter = f }, label = { Text(f.label) })
            }
        }
        if (shown.isEmpty()) {
            val hint = if (vm.filter == CallFilter.LOW_MARKS && !vm.marksReady) {
                "Open Marks and enter the total marks (Out of) to find low scorers."
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
                Card(Modifier.fillMaxWidth().animateItem().animateContentSize()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(record.studentName, style = MaterialTheme.typography.titleSmall)
                                val student = vm.students[i]
                                listOfNotNull(student.group, student.school).takeIf { it.isNotEmpty() }?.let {
                                    Text(it.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                                }
                                ContactRow(context, "Contact 1", student.contact1)
                                ContactRow(context, "Contact 2", student.contact2)
                            }
                            OutlinedTextField(
                                value = vm.marks.getOrElse(i) { "" },
                                onValueChange = { vm.setMark(i, it) },
                                label = { Text("Marks") },
                                singleLine = true,
                                isError = vm.isLow(i),
                                modifier = Modifier.width(96.dp),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OUTCOME_CHOICES.forEach { (outcome, label) ->
                                FilterChip(
                                    selected = record.outcome == outcome,
                                    onClick = {
                                        vm.updateRecord(i) {
                                            it.copy(outcome = if (it.outcome == outcome) CallOutcome.NOT_CALLED else outcome)
                                        }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        AnimatedVisibility(record.outcome == CallOutcome.CONCERN_RAISED) {
                            ConcernChips(record) { c -> vm.updateRecord(i) { c(it) } }
                        }
                        OutlinedTextField(
                            value = record.remark.orEmpty(),
                            onValueChange = { v -> vm.updateRecord(i) { it.copy(remark = v) } },
                            label = { Text("Remark") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = record.followUpRequired,
                                onCheckedChange = { v -> vm.updateRecord(i) { it.copy(followUpRequired = v) } },
                            )
                            Text("Follow-up required")
                        }
                    }
                }
            }
        }
        Button(
            onClick = vm::prepareReport,
            Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
        ) { Text("Share Report") }
    }
    if (showMarks) MarksDialog(vm) { showMarks = false }
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
                OutlinedTextField(
                    value = vm.maxMarks,
                    onValueChange = vm::updateMaxMarks,
                    label = { Text("Out of (total marks)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.cutoff,
                    onValueChange = vm::updateCutoff,
                    label = { Text("Low marks: below % of total") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Read marks from image")
                }
                Text(
                    "Type AB for absent. Absent and blank students are not counted as low marks. Marks are not added to the report.",
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
