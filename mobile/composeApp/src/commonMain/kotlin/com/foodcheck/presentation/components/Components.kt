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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.labelRegulationSummary
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform

@Composable
fun WatchTag(compact: Boolean = false) {
    val amber = AppColors.severityColor("amber")
    Row(
        modifier = Modifier
            .background(amber.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .border(1.dp, amber.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "Health watch",
            tint = amber,
            modifier = Modifier.size(if (compact) 10.dp else 11.dp)
        )
        if (!compact) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                "Watch",
                color = amber,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
fun LabelSummaryStrip(
    ingredients: List<AnnotatedIngredient>,
    modifier: Modifier = Modifier,
) {
    val summary = remember(ingredients) { labelRegulationSummary(ingredients) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Regulation",
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${summary.banned} banned · ${summary.restricted} restricted · " +
                "${summary.permitted} permitted · ${summary.unknown} unknown",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (summary.watch > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WatchTag(compact = true)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${summary.watch} with health signals",
                    color = AppColors.severityColor("amber"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

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
                "green", "permitted" -> "PERMITTED"
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
    // Read stored fields directly so Compose tracks them (custom getters can be skipped).
    val watch = ingredient.hasHealthWatch || ingredient.healthFlags.isNotEmpty() ||
        ingredient.members.any { it.hasHealthWatch || it.healthFlags.isNotEmpty() }
    val isIos = currentPlatform == Platform.IOS
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        color = if (isIos) Color.White.copy(alpha = 0.05f) else color.copy(alpha = 0.08f),
        border = if (isIos) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = if (isIos) RoundedCornerShape(12.dp) else RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (watch) {
                    Spacer(modifier = Modifier.width(6.dp))
                    WatchTag(compact = false)
                }
            }
            if (watch && ingredient.healthFlags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    ingredient.healthFlags.joinToString(" · ") { it.label },
                    color = AppColors.severityColor("amber"),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** Parent chip for Colours/Emulsifiers groups — banned style if any member is banned. */
@Composable
fun GroupIngredientChip(
    ingredient: AnnotatedIngredient,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val color = AppColors.severityColor(ingredient.severity)
    val icon = when (ingredient.severity.lowercase()) {
        "red" -> Icons.Default.Cancel
        "amber" -> Icons.Default.Warning
        "green" -> Icons.Default.CheckCircle
        else -> Icons.Default.Help
    }
    val count = ingredient.members.size
    val role = ingredient.role?.ifBlank { ingredient.name } ?: ingredient.name
    val watch = ingredient.hasHealthWatch || ingredient.healthFlags.isNotEmpty() ||
        ingredient.members.any { it.hasHealthWatch || it.healthFlags.isNotEmpty() }
    Surface(
        modifier = Modifier.clickable(onClick = onToggle),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$role ($count)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (watch) {
                    Spacer(modifier = Modifier.width(6.dp))
                    WatchTag(compact = false)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            val preview = ingredient.memberCodePreview()
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(preview, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
            if (watch && ingredient.healthFlags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    ingredient.healthFlags.joinToString(" · ") { it.label },
                    color = AppColors.severityColor("amber").copy(alpha = 0.9f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** One–two row chip strip for feed/shelf cards; overflow as +N more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactIngredientChips(
    ingredients: List<AnnotatedIngredient>,
    maxVisible: Int = 6,
    modifier: Modifier = Modifier,
    onOverflowClick: (() -> Unit)? = null,
    onIngredientClick: ((AnnotatedIngredient) -> Unit)? = null,
) {
    val visible = ingredients.take(maxVisible)
    val overflow = (ingredients.size - visible.size).coerceAtLeast(0)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { ingredient ->
            CompactIngredientChip(
                ingredient = ingredient,
                onClick = onIngredientClick?.let { handler ->
                    {
                        if (ingredient.id != null || ingredient.isGroup) handler(ingredient)
                    }
                },
            )
        }
        if (overflow > 0) {
            Text(
                "+$overflow more",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .then(
                        if (onOverflowClick != null) Modifier.clickable(onClick = onOverflowClick)
                        else Modifier
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun CompactIngredientChip(
    ingredient: AnnotatedIngredient,
    onClick: (() -> Unit)? = null,
) {
    val color = AppColors.severityColor(ingredient.severity)
    val icon = when (ingredient.severity.lowercase()) {
        "red" -> Icons.Default.Cancel
        "amber" -> Icons.Default.Warning
        "green" -> Icons.Default.CheckCircle
        else -> Icons.Default.Help
    }
    val label = if (ingredient.isGroup) {
        val role = ingredient.role?.ifBlank { ingredient.name } ?: ingredient.name
        "$role (${ingredient.members.size})"
    } else {
        ingredient.name.let { if (it.length > 22) it.take(20) + "…" else it }
    }
    val watch = ingredient.hasHealthWatch || ingredient.healthFlags.isNotEmpty() ||
        ingredient.members.any { it.hasHealthWatch || it.healthFlags.isNotEmpty() }
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (watch) {
                Spacer(modifier = Modifier.width(4.dp))
                WatchTag(compact = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelIngredientChips(
    ingredients: List<AnnotatedIngredient>,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ingredients.forEachIndexed { index, ingredient ->
            if (ingredient.isGroup) {
                val key = "${ingredient.role}-${ingredient.labelText}-$index"
                val expanded = key in expandedKeys
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupIngredientChip(
                        ingredient = ingredient,
                        expanded = expanded,
                        onToggle = {
                            expandedKeys = if (expanded) expandedKeys - key else expandedKeys + key
                        }
                    )
                    if (expanded) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ingredient.members.forEach { member ->
                                IngredientChip(member) {
                                    if (member.id != null) onIngredientClick(member)
                                }
                            }
                        }
                        ingredient.labelText?.takeIf { it.isNotBlank() }?.let { label ->
                            Text(
                                "as on label: $label",
                                color = AppColors.TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            } else {
                IngredientChip(ingredient) {
                    if (ingredient.id != null) onIngredientClick(ingredient)
                }
            }
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
