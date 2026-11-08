package com.creativeclasses.callingapp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.NameMatcher

/**
 * Finds a name in the student list and in the rows read from the marks image, so the teacher can tell a misspelt
 * name from a missing one and type the student's mark right here.
 */
@Composable
fun NameSearchDialog(vm: CallingViewModel) {
    var query by remember { mutableStateOf("") }
    val students = vm.students.indices.filter { NameMatcher.searchMatches(query, vm.students[it].name) }
    val rows = vm.imageRows.filter { NameMatcher.searchMatches(query, it.name) }
    AlertDialog(
        onDismissRequest = { vm.showNameSearch = false },
        title = { Text("Search names") },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it }, Modifier.holdsBreak().fillMaxWidth(),
                    label = { Text("Name or part of it, e.g. a surname") }, singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    if (query.isNotBlank()) {
                        item { Heading("Student list (${students.size})") }
                        items(students, key = { "s$it" }) { i ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.students[i].name, Modifier.weight(1f))
                                OutlinedTextField(
                                    vm.marks.getOrElse(i) { "" }, { vm.setMark(i, it) }, Modifier.holdsBreak().width(88.dp),
                                    label = { Text("Marks") }, singleLine = true,
                                )
                            }
                        }
                        item { Heading("Marks image (${rows.size})") }
                        if (vm.imageRows.isEmpty()) {
                            item { Text("No marks image read in this session yet.", style = MaterialTheme.typography.bodySmall) }
                        }
                        items(rows) { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(row.name, Modifier.weight(1f))
                                Text(row.marks, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.showNameSearch = false }) { Text("Done") } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
