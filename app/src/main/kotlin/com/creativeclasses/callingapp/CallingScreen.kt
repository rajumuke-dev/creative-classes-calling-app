package com.creativeclasses.callingapp

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.CallOutcome
import com.creativeclasses.report.CallRecord
import com.creativeclasses.report.Concern

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
    val done = vm.records.count { it.outcome != CallOutcome.NOT_CALLED }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Calls recorded: $done / ${vm.records.size}",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(vm.records) { i, record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(record.studentName, style = MaterialTheme.typography.titleSmall)
                                vm.students[i].phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            vm.students[i].phone?.let { phone ->
                                TextButton(onClick = { dialPhone(context, phone) }) { Text("Call") }
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
                        if (record.outcome == CallOutcome.CONCERN_RAISED) ConcernChips(record) { c -> vm.updateRecord(i) { c(it) } }
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
