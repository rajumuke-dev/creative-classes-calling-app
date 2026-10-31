package com.creativeclasses.callingapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ReportScreen(vm: CallingViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Sounds.play(context, Sounds.Cue.COMPLETE) }
    val startVoice = rememberVoiceInput { spoken ->
        vm.reportText = vm.reportText.trimEnd() + "\n" + spoken
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.screen = Screen.CALLING }) { Text("\u2190 Back") }
            Text("Report Preview", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        vm.reportScope?.let { Text("Covers only $it.", style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(
            value = vm.reportText,
            onValueChange = { vm.reportText = it },
            readOnly = !editing,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp).enter3D()
                .shadow(10.dp, CardShape).background(CardSheen, CardShape),
            shape = CardShape,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editing = !editing }, Modifier.weight(1f)) {
                Text(if (editing) "Done Editing" else "Edit Report")
            }
            OutlinedButton(onClick = {
                editing = true
                startVoice()
            }, Modifier.weight(1f)) { Text("\uD83C\uDF99 Speak") }
        }
        Button(onClick = { shareReport(context, vm.reportText) }, Modifier.fillMaxWidth().padding(top = 8.dp).tiltOnTouch(8f), elevation = raisedButton()) {
            Text("Share on WhatsApp")
        }
    }
}
