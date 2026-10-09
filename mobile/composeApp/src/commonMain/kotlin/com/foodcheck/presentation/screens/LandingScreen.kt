package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.presentation.theme.AppColors

@Composable
fun LandingScreen(
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Scan entry point (top)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clickable(onClick = onScanClick)
                .border(1.dp, AppColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            color = AppColors.Primary.copy(alpha = 0.08f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = "Scan",
                    tint = AppColors.Primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scan a Product",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Photograph the front of the pack, or the\ningredients label, and we'll check it for you.",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Color.White.copy(alpha = 0.9f))) {
                        append("Life")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 20.sp, color = AppColors.Primary)) {
                        append("X")
                    }
                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = Color.White)) {
                        append(" Food")
                    }
                }
            )
            Text(
                text = "Know what's banned, anywhere.",
                color = AppColors.TextMuted,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Search entry point (bottom)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSearchClick)
                .border(1.dp, AppColors.Border.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            color = AppColors.Surface.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.Primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Search product or ingredient...",
                    color = AppColors.TextMuted,
                    fontSize = 15.sp
                )
            }
        }
    }
}
