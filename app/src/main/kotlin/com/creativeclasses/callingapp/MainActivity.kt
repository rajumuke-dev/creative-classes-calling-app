package com.creativeclasses.callingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: CallingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme { ProvideDeviceTilt {
                Surface(
                    Modifier.fillMaxSize().background(BackgroundBrush),
                    color = Color.Transparent,
                ) {
                    BackHandler(enabled = vm.screen != Screen.DASHBOARD) {
                        if (vm.screen == Screen.REPORT) vm.screen = Screen.CALLING else vm.goToDashboard()
                    }
                    AnimatedContent(
                        targetState = vm.screen,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            (fadeIn() + scaleIn(initialScale = 0.92f) + slideInHorizontally { if (forward) it / 3 else -it / 3 }) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.92f) + slideOutHorizontally { if (forward) -it / 3 else it / 3 })
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
                    BreakReminder(vm.breakTimer)
                }
            } }
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.breakTimer.run() } }
    }

    override fun onStart() {
        super.onStart()
        vm.breakTimer.onAppStart()
    }

    override fun onStop() {
        vm.saveNow()
        vm.breakTimer.onAppStop()
        super.onStop()
    }
}
