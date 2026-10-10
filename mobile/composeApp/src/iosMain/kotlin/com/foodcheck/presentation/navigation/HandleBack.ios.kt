package com.foodcheck.presentation.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun HandleBack(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses the top-bar back control (and swipe-back on native stacks).
}
