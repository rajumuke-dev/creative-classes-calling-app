package com.creativeclasses.callingapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import com.creativeclasses.report.MarksRule
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.creativeclasses.report.CallingPurpose
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: CallingViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile { context.contentResolver.openInputStream(uri) }
    }
    val marksPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importMarksImage(uri)
    }
    val form = vm.form
    var ruleMenu by remember { mutableStateOf(false) }
    var purposeMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row {
            TextButton(onClick = vm::goToDashboard) { Text("\u2190 Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::startCalling) { Text("Next \u2192") }
        }
        HeroHeader("Creative Classes", "Parent Calling")
        Spacer(Modifier.height(16.dp))

        Button(onClick = { picker.launch(arrayOf(XLSX_MIME)) }, Modifier.fillMaxWidth().tiltOnTouch(8f), elevation = raisedButton()) {
            Text("Import Excel (.xlsx)")
        }
        vm.message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
        if (vm.sheetNames.size > 1) {
            Text("Sheet", Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.sheetNames.forEachIndexed { i, name ->
                    FilterChip(selected = vm.selectedSheet == i, onClick = { vm.selectSheet(i) }, label = { Text(name) })
                }
            }
        }
        if (vm.students.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Filter by marks (optional)")
            OutlinedButton(onClick = { marksPicker.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth()) {
                Text(if (vm.hasMarks) "Marks image uploaded - upload again" else "Upload marks image")
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Text(
                "Upload the marks image and enter the marks to start with only the matching students.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        Text("Calling Purpose")
        OutlinedButton(onClick = { purposeMenu = true }, Modifier.fillMaxWidth()) { Text(form.purpose.label) }
        DropdownMenu(expanded = purposeMenu, onDismissRequest = { purposeMenu = false }) {
            CallingPurpose.values().forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = {
                    vm.form = form.copy(purpose = p)
                    purposeMenu = false
                })
            }
        }
        if (form.purpose == CallingPurpose.OTHER) {
            Field("Custom Purpose", form.customPurpose) { vm.form = form.copy(customPurpose = it) }
        }
        Field("Session Name", form.sessionName) { vm.form = form.copy(sessionName = it) }
        Field("Class (e.g. 8th)", form.className) { vm.form = form.copy(className = it) }
        Field("Section", form.section) { vm.form = form.copy(section = it) }
        Field("Board (e.g. CBSE)", form.board) { vm.form = form.copy(board = it) }
        Field("Exam", form.exam) { vm.form = form.copy(exam = it) }
        Field("Subject (e.g. History)", form.subject) { vm.form = form.copy(subject = it) }
        Field("Teacher", form.teacher) { vm.form = form.copy(teacher = it) }
        Field("Teacher available (e.g. on Monday from 4 PM to 7 PM)", form.availability) { vm.form = form.copy(availability = it) }
        Text(
            "Date: " + vm.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)),
            Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(16.dp))
        Button(onClick = vm::startCalling, Modifier.fillMaxWidth().tiltOnTouch(8f), elevation = raisedButton()) {
            Text(if (vm.students.isEmpty()) "Start Calling" else "Start Calling (${vm.students.size} students)")
        }
    }
    vm.marksNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { vm.marksNotice = null },
            title = { Text("Marks from image") },
            text = { Text(notice, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { vm.marksNotice = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { vm.showNameSearch = true }) { Text("Search names") } },
        )
    }
    if (vm.showNameSearch) NameSearchDialog(vm)
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.holdsBreak().fillMaxWidth().padding(top = 8.dp),
    )
}
