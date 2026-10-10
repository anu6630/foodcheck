package com.foodcheck.presentation.navigation

import androidx.compose.runtime.Composable

/** System/gesture back. No-op on platforms without a back affordance. */
@Composable
expect fun HandleBack(enabled: Boolean, onBack: () -> Unit)
