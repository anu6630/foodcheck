package com.foodcheck.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Anchored banner for the text-search screen. No-op on non-Android. */
@Composable
expect fun BannerAd(modifier: Modifier = Modifier)
