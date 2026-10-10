package com.foodcheck.presentation.theme

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.platformStatusBarsPadding(): Modifier = this.statusBarsPadding()
