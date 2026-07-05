package com.foodcheck.presentation.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun PlatformBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    if (currentPlatform == Platform.IOS) {
        val infiniteTransition = rememberInfiniteTransition()
        val xVal1 by infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(35000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        val yVal1 by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(40000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        val xVal2 by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(38000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        val yVal2 by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(32000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AppColors.Background) // Base dark background
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Drifting Blob 1 (Primary - Emerald)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AppColors.Primary.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(width * xVal1, height * yVal1),
                        radius = width * 0.75f
                    ),
                    center = Offset(width * xVal1, height * yVal1),
                    radius = width * 0.75f
                )

                // Drifting Blob 2 (Secondary - Indigo)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AppColors.Secondary.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(width * xVal2, height * yVal2),
                        radius = width * 0.85f
                    ),
                    center = Offset(width * xVal2, height * yVal2),
                    radius = width * 0.85f
                )
            }

            // Translucent dark cover layer to ensure maximum contrast and readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Background.copy(alpha = 0.5f))
            )

            // Content on top of background
            Box(modifier = Modifier.fillMaxSize(), content = content)
        }
    } else {
        val bgGradient = Brush.verticalGradient(
            colors = listOf(AppColors.Background, AppColors.BackgroundSecondary)
        )
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgGradient),
            content = content
        )
    }
}
