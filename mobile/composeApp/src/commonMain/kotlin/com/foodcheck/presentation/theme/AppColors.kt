package com.foodcheck.presentation.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


/** Shared dark glassmorphism palette, extracted so every screen file draws from one source. */
object AppColors {
    val Background = Color(0xFF020617)
    val BackgroundSecondary = Color(0xFF0F172A)
    val Surface = Color(0xFF1E293B)
    val Border = Color(0xFF475569)
    val OnBackground = Color(0xFFF8FAFC)
    val Primary = Color(0xFF10B981) // Emerald 500
    val Secondary = Color(0xFF6366F1) // Indigo 500
    val SecondaryLight = Color(0xFF818CF8)
    val SecondaryLighter = Color(0xFFE0E7FF)
    val TextMuted = Color(0xFF94A3B8)
    val TextFaint = Color(0xFFCBD5E1)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFF94A3B8)
    val Red = Color(0xFFF43F5E)
    val RedLight = Color(0xFFFDA4AF)
    val Amber = Color(0xFFF59E0B)
    val Green = Color(0xFF34D399)

    fun severityColor(severity: String): Color = when (severity.lowercase()) {
        "red" -> Red
        "amber" -> Amber
        "green" -> Green
        else -> TextMuted
    }

    // Platform-specific design system tokens
    val cardBackground: Color
        @Composable
        get() = if (currentPlatform == Platform.IOS) {
            Color.White.copy(alpha = 0.06f)
        } else {
            Surface.copy(alpha = 0.4f)
        }

    val cardBorder: BorderStroke
        @Composable
        get() = if (currentPlatform == Platform.IOS) {
            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
        } else {
            BorderStroke(1.dp, Border.copy(alpha = 0.3f))
        }

    val cardShape: RoundedCornerShape
        @Composable
        get() = if (currentPlatform == Platform.IOS) {
            RoundedCornerShape(22.dp)
        } else {
            RoundedCornerShape(16.dp)
        }

    val searchFieldContainerColor: Color
        @Composable
        get() = if (currentPlatform == Platform.IOS) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Surface.copy(alpha = 0.4f)
        }

    val searchFieldBorderStroke: BorderStroke?
        @Composable
        get() = if (currentPlatform == Platform.IOS) {
            null
        } else {
            BorderStroke(1.dp, Primary.copy(alpha = 0.3f))
        }

    val searchFieldShape: RoundedCornerShape
        @Composable
        get() = if (currentPlatform == Platform.IOS) {
            RoundedCornerShape(10.dp)
        } else {
            RoundedCornerShape(16.dp)
        }
}

