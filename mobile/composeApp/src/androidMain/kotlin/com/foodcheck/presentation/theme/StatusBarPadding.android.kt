package com.foodcheck.presentation.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
actual fun Modifier.platformStatusBarsPadding(): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    var topPx by remember { mutableStateOf(0) }

    DisposableEffect(view) {
        fun readTop(): Int {
            val insets = ViewCompat.getRootWindowInsets(view) ?: return 0
            // Prefer statusBars; fall back to systemBars if OEM reports 0.
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (status > 0) return status
            return insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
        topPx = readTop()
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            topPx = if (status > 0) status
            else insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            insets
        }
        val listener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            topPx = readTop()
        }
        view.addOnLayoutChangeListener(listener)
        view.requestApplyInsets()
        onDispose {
            view.removeOnLayoutChangeListener(listener)
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    val topDp = with(density) {
        // Extra gap so italic "Food" / icons don't crowd the system status row.
        val inset = if (topPx > 0) topPx.toDp() else 28.dp
        inset + 10.dp
    }
    return this.padding(top = topDp)
}
