package com.creativeclasses.callingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    private val vm: CallingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    BackHandler(enabled = vm.screen != Screen.DASHBOARD) {
                        if (vm.screen == Screen.REPORT) vm.screen = Screen.CALLING else vm.goToDashboard()
                    }
                    AnimatedContent(
                        targetState = vm.screen,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            (fadeIn() + slideInHorizontally { if (forward) it / 4 else -it / 4 }) togetherWith
                                (fadeOut() + slideOutHorizontally { if (forward) -it / 4 else it / 4 })
                        },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            Screen.DASHBOARD -> DashboardScreen(vm)
                            Screen.SETUP -> SetupScreen(vm)
                            Screen.CALLING -> CallingScreen(vm)
                            Screen.REPORT -> ReportScreen(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        vm.saveNow()
        super.onStop()
    }
}
