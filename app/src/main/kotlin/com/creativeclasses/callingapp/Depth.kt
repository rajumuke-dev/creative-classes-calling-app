package com.creativeclasses.callingapp

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Shared "raised" look: shadows under cards, buttons and chips give the UI depth. */
val CardShape = RoundedCornerShape(18.dp)

@Composable
fun raisedButton() = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 1.dp)
