package com.foodcheck.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Pads below the system status bar using root window insets (works inside Scaffold topBar). */
@Composable
expect fun Modifier.platformStatusBarsPadding(): Modifier
