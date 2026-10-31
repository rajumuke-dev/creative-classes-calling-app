package com.creativeclasses.callingapp

import androidx.compose.animation.AnimatedVisibility
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
    val bar by animateFloatAsState(if (progress.total == 0) 0f else progress.attempted.toFloat() / progress.total, label = "progress")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::goToDashboard) { Text("Dashboard") }
            Text(
                "Called ${progress.attempted} / ${progress.total}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LinearProgressIndicator(progress = { bar }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallFilter.values().forEach { f ->
                FilterChip(selected = vm.filter == f, onClick = { vm.filter = f }, label = { Text(f.label) })
            }
        }
        if (shown.isEmpty()) Text("No students in this view.", Modifier.padding(16.dp))
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
