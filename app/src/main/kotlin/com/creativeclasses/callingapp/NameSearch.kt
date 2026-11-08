package com.creativeclasses.callingapp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.MarkRow
import com.creativeclasses.report.NameMatcher
import com.creativeclasses.report.PhoneNumbers

/**
 * Finds a name in the student list and in the rows read from the marks image, so the teacher can tell a misspelt
 * name from a missing one and type the student's mark right here. An image row's mark can be given to a list student
 * or the row added as a new student, so it joins the Lower than / Higher than list being called.
 */
@Composable
fun NameSearchDialog(vm: CallingViewModel) {
    var query by remember { mutableStateOf("") }
    val students = vm.students.indices.filter { NameMatcher.searchMatches(query, vm.students[it].name) }
    val rows = vm.imageRows.filter { NameMatcher.searchMatches(query, it.name) }
    val unmatched = remember(vm.imageRows, vm.students) { NameMatcher.match(vm.imageRows, vm.students.map { it.name }).unmatched.toSet() }
    var picking by remember { mutableStateOf<MarkRow?>(null) }
    var added by remember { mutableStateOf<String?>(null) }
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
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.name)
                                    if (row in unmatched) Text("Not matched", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                Text(row.marks, style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { picking = row }) { Text("Use") }
                            }
                        }
                    }
                    added?.let { item { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.showNameSearch = false }) { Text("Done") } },
    )
    picking?.let { row ->
        UseImageRowDialog(vm, row, onDismiss = { picking = null }) { i ->
            picking = null
            val list = vm.marksListName
            added = "${vm.students[i].name} now has ${row.marks} marks" + when {
                list == null -> "."
                vm.matchesMarks(i) -> " and is in the $list list."
                else -> ", so is not in the $list list."
            }
        }
    }
}

/** Gives the mark of an image row to a student in the list (a misspelt or misread name), or adds them as a new student. */
@Composable
private fun UseImageRowDialog(vm: CallingViewModel, row: MarkRow, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var query by remember { mutableStateOf(row.name.trim().substringBefore(' ')) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(row.name.trim()) }
    var phone by remember { mutableStateOf("") }
    val phoneOk = phone.isBlank() || PhoneNumbers.isValid(phone)
    val students = vm.students.indices.filter { NameMatcher.searchMatches(query, vm.students[it].name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${row.name}: ${row.marks} marks") },
        text = {
            Column {
                if (adding) {
                    OutlinedTextField(name, { name = it }, Modifier.holdsBreak().fillMaxWidth(), label = { Text("Student name") }, singleLine = true)
                    OutlinedTextField(
                        phone, { phone = it }, Modifier.holdsBreak().fillMaxWidth(), label = { Text("Parent phone (optional)") },
                        singleLine = true, isError = !phoneOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        supportingText = { if (!phoneOk) Text("Enter a valid phone number") },
                    )
                } else {
                    Text("Give this mark to a student in the list:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(query, { query = it }, Modifier.holdsBreak().fillMaxWidth(), label = { Text("Search the list") }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                        items(students, key = { it }) { i ->
                            TextButton(onClick = { vm.setMark(i, row.marks); onDone(i) }, Modifier.fillMaxWidth()) {
                                Text(vm.students[i].name, Modifier.weight(1f))
                                Text(vm.marks.getOrElse(i) { "" }.ifBlank { "no marks" }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (adding) {
                TextButton(enabled = name.isNotBlank() && phoneOk, onClick = { onDone(vm.addStudent(name, phone, row.marks)) }) { Text("Add") }
            } else {
                TextButton(onClick = { adding = true }) { Text("Add as new student") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
