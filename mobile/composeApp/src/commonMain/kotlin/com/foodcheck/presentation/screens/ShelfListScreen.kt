package com.foodcheck.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ShelfStance
import com.foodcheck.presentation.components.CompactChoiceDialog
import com.foodcheck.presentation.components.CompactChoiceOption
import com.foodcheck.presentation.components.CompactIngredientChips
import com.foodcheck.presentation.components.LabelIngredientChips
import com.foodcheck.presentation.components.SearchHeroBar
import com.foodcheck.presentation.components.SmallChoice
import com.foodcheck.presentation.state.ComparisonsViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import com.foodcheck.presentation.state.ShelfViewModel
import com.foodcheck.presentation.theme.AppColors
import org.koin.compose.viewmodel.koinViewModel

private const val COLLAPSED_CHIP_LIMIT = 4

@Composable
fun ShelfListScreen(
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    onOpenComparison: (Int) -> Unit,
    onCompareItem: (ShelfItemDto) -> Unit,
    viewModel: ShelfViewModel = koinViewModel(),
    comparisonsViewModel: ComparisonsViewModel = koinViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val comparisons by comparisonsViewModel.items.collectAsState()
    val comparisonsError by comparisonsViewModel.error.collectAsState()
    var showComparisons by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { comparisonsViewModel.load() }
    val query by viewModel.query.collectAsState()
    val error by viewModel.error.collectAsState()
    var expandedIds by remember { mutableStateOf(setOf<Int>()) }
    var stanceEditId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "Your saved packs",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Stance colors how you treat each one. Private to this device.",
            color = AppColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallChoice("Packs ${items.size}", selected = !showComparisons) { showComparisons = false }
            SmallChoice("Comparisons ${comparisons.size}", selected = showComparisons) { showComparisons = true }
        }
        Spacer(Modifier.height(12.dp))

        if (showComparisons) {
            comparisonsError?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            if (comparisons.isEmpty()) {
                Text(
                    "No comparisons yet. Use Compare to put 2–3 packs side by side.",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp,
                )
            } else {
                ComparisonListItems(items = comparisons, onOpen = { onOpenComparison(it.id) })
            }
            return@Column
        }

        SearchHeroBar(
            query = query,
            onQueryChange = { viewModel.load(it) },
            placeholder = "Filter your list",
            showScopeHints = false,
        )

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            StanceLegend()
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.Red, fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))

        when {
            items.isEmpty() && query.isBlank() -> {
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Save a pack from Scan",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "It stays on this device only",
                        color = AppColors.TextMuted.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            items.isEmpty() -> {
                Text(
                    "No matches in your list.",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.id }) { item ->
                        val expanded = item.id in expandedIds
                        ShelfScanCard(
                            item = item,
                            expanded = expanded,
                            onToggle = {
                                expandedIds = if (expanded) expandedIds - item.id else expandedIds + item.id
                            },
                            onStanceClick = { stanceEditId = item.id },
                            onIngredientClick = onIngredientClick,
                            onCompare = { onCompareItem(item) },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    stanceEditId?.let { id ->
        val current = items.firstOrNull { it.id == id }
        val selected = ShelfStance.fromApi(current?.stance)
        CompactChoiceDialog(
            title = "Stance",
            options = ShelfStance.entries.map { option ->
                CompactChoiceOption(
                    id = option.apiValue,
                    label = option.label,
                    accent = stanceAccent(option),
                    selected = option == selected,
                )
            },
            onSelect = { choice ->
                viewModel.updateStance(id, ShelfStance.fromApi(choice.id))
                stanceEditId = null
            },
            onDismiss = { stanceEditId = null },
        )
    }
}

@Composable
private fun StanceLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShelfStance.entries.forEach { stance ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(stanceAccent(stance)),
                )
                Text(
                    stance.label,
                    color = AppColors.TextMuted.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ShelfScanCard(
    item: ShelfItemDto,
    expanded: Boolean,
    onToggle: () -> Unit,
    onStanceClick: () -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    onCompare: () -> Unit,
) {
    val stance = ShelfStance.fromApi(item.stance)
    val accent = stanceAccent(stance)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = buildList {
                            item.brand?.takeIf { it.isNotBlank() }?.let(::add)
                            item.companies.map { it.name }.takeIf { it.isNotEmpty() }
                                ?.let { add(it.joinToString(", ")) }
                            item.tags.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                        }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                meta,
                                color = AppColors.TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.clickable(onClick = onStanceClick),
                        color = accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            stance.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = AppColors.TextMuted.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (item.ingredients.isEmpty()) {
                    Text("No ingredients on this scan.", color = AppColors.TextMuted, fontSize = 12.sp)
                } else if (expanded) {
                    LabelIngredientChips(
                        ingredients = item.ingredients,
                        onIngredientClick = onIngredientClick,
                    )
                    if (item.scanId != null) {
                        Text(
                            "Compare with another ›",
                            modifier = Modifier.clickable(onClick = onCompare).padding(top = 10.dp),
                            color = AppColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
}

private fun stanceAccent(stance: ShelfStance): Color = when (stance) {
    ShelfStance.Avoid -> Color(0xFFEF4444)
    ShelfStance.Occasional -> Color(0xFFF59E0B)
    ShelfStance.Ok -> AppColors.Primary
    ShelfStance.Unsure -> Color(0xFF94A3B8)
}
