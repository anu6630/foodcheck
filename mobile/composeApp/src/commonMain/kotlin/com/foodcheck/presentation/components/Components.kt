package com.foodcheck.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform



@Composable
fun IngredientSectionHeader(title: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .background(color, shape = RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            fontSize = 15.sp
        )
    }
}

@Composable
fun ProductCard(result: SearchProductResult, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = AppColors.cardBackground,
        border = AppColors.cardBorder,
        shape = AppColors.cardShape
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(result.severity)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Brand: ${result.product.brand ?: "Unknown"}",
                color = AppColors.Primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Text(
                text = "Barcode: ${result.product.barcode}",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SeverityBadge(severity: String) {
    val color = AppColors.severityColor(severity)
    val shape = if (currentPlatform == Platform.IOS) RoundedCornerShape(10.dp) else RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = shape)
            .border(1.dp, color.copy(alpha = 0.4f), shape = shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = when (severity.lowercase()) {
                "red" -> "BANNED"
                "amber" -> "RESTRICTED"
                "green", "permitted" -> "AUTHORISED"
                else -> "NO DATA"
            },
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun IngredientChip(ingredient: AnnotatedIngredient, onClick: () -> Unit) {
    val color = AppColors.severityColor(ingredient.severity)
    val icon = when (ingredient.severity.lowercase()) {
        "red" -> Icons.Default.Cancel
        "amber" -> Icons.Default.Warning
        "green" -> Icons.Default.CheckCircle
        else -> Icons.Default.Help
    }
    val isIos = currentPlatform == Platform.IOS
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        color = if (isIos) Color.White.copy(alpha = 0.05f) else color.copy(alpha = 0.08f),
        border = if (isIos) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = if (isIos) RoundedCornerShape(12.dp) else RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            val code = ingredient.codesText()
            val label = if (code != null) "${ingredient.name} ($code)" else ingredient.name
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String) {
    Surface(
        color = AppColors.Red.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, AppColors.Red.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.Red)
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = message, color = AppColors.RedLight)
        }
    }
}
