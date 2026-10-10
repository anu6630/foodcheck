package com.foodcheck.presentation.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.presentation.components.CompactIngredientChips
import com.foodcheck.presentation.components.LabelIngredientChips
import com.foodcheck.presentation.state.ShelfViewModel
import com.foodcheck.presentation.theme.AppColors
import org.koin.compose.viewmodel.koinViewModel

private const val COLLAPSED_CHIP_LIMIT = 4

@Composable
fun ShelfListScreen(
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    viewModel: ShelfViewModel = koinViewModel()
) {
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val error by viewModel.error.collectAsState()
    var expandedIds by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Only this device. Not a public product catalog.", color = AppColors.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.load(it) },
            label = { Text("Search mine") },
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("No saved scans yet. Save one from a label scan.", color = AppColors.TextMuted, fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item ->
                    val expanded = item.id in expandedIds
                    ShelfScanCard(
                        item = item,
                        expanded = expanded,
                        onToggle = {
                            expandedIds = if (expanded) expandedIds - item.id else expandedIds + item.id
                        },
                        onIngredientClick = onIngredientClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfScanCard(
    item: ShelfItemDto,
    expanded: Boolean,
    onToggle: () -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Surface.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.displayName,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val brandLine = item.brand?.takeIf { it.isNotBlank() }
                    val companyLine = item.companies.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }
                    when {
                        brandLine != null && companyLine != null ->
                            Text(
                                "$brandLine · $companyLine",
                                color = AppColors.TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        brandLine != null ->
                            Text(brandLine, color = AppColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        companyLine != null ->
                            Text(companyLine, color = AppColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (item.ingredients.size > COLLAPSED_CHIP_LIMIT || expanded) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (item.ingredients.isEmpty()) {
                Text("No ingredients on this scan.", color = AppColors.TextMuted, fontSize = 12.sp)
            } else if (expanded) {
                LabelIngredientChips(
                    ingredients = item.ingredients,
                    onIngredientClick = onIngredientClick,
                )
            } else {
                CompactIngredientChips(
                    ingredients = item.ingredients,
                    maxVisible = COLLAPSED_CHIP_LIMIT,
                    onOverflowClick = onToggle,
                    onIngredientClick = { ing ->
                        if (ing.isGroup) onToggle()
                        else if (ing.id != null) onIngredientClick(ing)
                    },
                )
            }
        }
    }
}
