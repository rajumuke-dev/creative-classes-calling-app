package com.creativeclasses.callingapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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

@Composable
fun SetupScreen(vm: CallingViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile { context.contentResolver.openInputStream(uri) }
    }
    val form = vm.form
    var purposeMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Creative Classes", style = MaterialTheme.typography.headlineSmall)
        Text("Parent Calling", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Button(onClick = { picker.launch(arrayOf(XLSX_MIME)) }, Modifier.fillMaxWidth()) {
            Text("Import Excel (.xlsx)")
        }
        vm.message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
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
        Field("Teacher", form.teacher) { vm.form = form.copy(teacher = it) }
        Text(
            "Date: " + vm.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)),
            Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(16.dp))
        Button(onClick = vm::startCalling, Modifier.fillMaxWidth()) {
            Text(if (vm.students.isEmpty()) "Start Calling" else "Start Calling (${vm.students.size} students)")
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
