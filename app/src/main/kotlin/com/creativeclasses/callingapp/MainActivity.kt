package com.creativeclasses.callingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    private val vm: CallingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    BackHandler(enabled = vm.screen != Screen.SETUP) {
                        vm.screen = if (vm.screen == Screen.REPORT) Screen.CALLING else Screen.SETUP
                    }
                    when (vm.screen) {
                        Screen.SETUP -> SetupScreen(vm)
                        Screen.CALLING -> CallingScreen(vm)
                        Screen.REPORT -> ReportScreen(vm)
                    }
                }
            }
        }
    }
}
