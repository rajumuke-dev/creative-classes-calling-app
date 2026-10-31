package com.creativeclasses.callingapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
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

@Composable
fun ReportScreen(vm: CallingViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Text("Report Preview", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = vm.reportText,
            onValueChange = { vm.reportText = it },
            readOnly = !editing,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
        )
        OutlinedButton(onClick = { editing = !editing }, Modifier.fillMaxWidth()) {
            Text(if (editing) "Done Editing" else "Edit Report")
        }
        Button(onClick = { shareReport(context, vm.reportText) }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Share on WhatsApp")
        }
    }
}
