package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.ads.AdsController
import com.foodcheck.data.dto.ShelfItemDto
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.domain.entities.ShelfStance
import com.foodcheck.presentation.components.ErrorBanner
import com.foodcheck.presentation.components.IngredientChip
import com.foodcheck.presentation.components.IngredientSectionHeader
import com.foodcheck.presentation.components.SearchHeroBar
import com.foodcheck.presentation.state.SearchViewModel
import com.foodcheck.presentation.theme.AppColors
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchResultsScreen(
    initialQuery: String,
    onQueryChange: (Boolean) -> Unit = {},
    clearQueryTrigger: Int = 0,
    onProductClick: (SearchProductResult) -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val shelf by viewModel.shelf.collectAsState()
    val idle = query.isBlank()

    LaunchedEffect(Unit) {
        AdsController.preloadInterstitial()
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.search(initialQuery)
    }

    LaunchedEffect(query) {
        onQueryChange(query.isNotEmpty())
    }

    LaunchedEffect(clearQueryTrigger) {
        if (clearQueryTrigger > 0) {
            viewModel.search("")
        }
    }

    fun openIngredient(ingredient: AnnotatedIngredient) {
        com.foodcheck.analytics.getAnalyticsTracker().logSearchResultSelected(
            resultType = "ingredient",
            itemId = ingredient.id ?: ingredient.eNumber ?: "unknown",
            itemName = ingredient.name
        )
        onIngredientClick(ingredient)
    }

    val matchedIngredientIds = remember(results.ingredients) {
        buildSet {
            fun collect(ing: AnnotatedIngredient) {
                ing.id?.let { add(it) }
                ing.eNumber?.let { add(it) }
                ing.codes.forEach { add(it.code) }
                ing.members.forEach { collect(it) }
            }
            results.ingredients.forEach { collect(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (idle) 20.dp else 12.dp,
                bottom = if (idle) 20.dp else 12.dp,
            )
    ) {
        if (idle) {
            Text(
                "Find what’s in a pack",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Search any additive, ingredient, or your saved items.",
                color = AppColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        SearchHeroBar(
            query = query,
            onQueryChange = { viewModel.search(it) },
        )

        Spacer(modifier = Modifier.height(if (idle) 20.dp else 14.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
                error != null -> ErrorBanner(error ?: "")
                results.ingredients.isEmpty() && shelf.isEmpty() -> {
                    if (query.isNotBlank()) {
                        Text(
                            "No ingredient reference or saved pack matches for \"$query\".",
                            color = AppColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Results appear as you type",
                                color = AppColors.TextMuted.copy(alpha = 0.45f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Ingredient Regulatory Details FIRST
                        if (results.ingredients.isNotEmpty()) {
                            item {
                                IngredientSectionHeader(
                                    "Ingredient reference & regulations (${results.ingredients.size})",
                                    AppColors.Secondary
                                )
                            }
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    results.ingredients.forEach { ingredient ->
                                        IngredientChip(ingredient) { openIngredient(ingredient) }
                                    }
                                }
                            }
                        }

                        // 2. Found in Your Saved Packs SECOND
                        if (shelf.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IngredientSectionHeader(
                                        "Found in your saved packs (${shelf.size})",
                                        AppColors.Primary
                                    )
                                    Text(
                                        "Matched in your private saved pantry",
                                        color = AppColors.TextMuted.copy(alpha = 0.75f),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            items(shelf, key = { it.id }) { item ->
                                SavedPackSearchCard(
                                    item = item,
                                    query = query,
                                    matchedIngredientIds = matchedIngredientIds,
                                    onIngredientClick = { openIngredient(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedPackSearchCard(
    item: ShelfItemDto,
    query: String,
    matchedIngredientIds: Set<String>,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
) {
    val stance = ShelfStance.fromApi(item.stance)
    val accent = when (stance) {
        ShelfStance.Avoid -> Color(0xFFEF4444)
        ShelfStance.Occasional -> Color(0xFFF59E0B)
        ShelfStance.Ok -> AppColors.Primary
        ShelfStance.Unsure -> Color(0xFF94A3B8)
    }

    val q = query.trim()
    val matched = remember(item.ingredients, matchedIngredientIds, q) {
        fun matches(ing: AnnotatedIngredient): Boolean {
            if (matchedIngredientIds.isNotEmpty()) {
                val id = ing.id
                if (id != null && id in matchedIngredientIds) return true
                val eNum = ing.eNumber
                if (eNum != null && (eNum in matchedIngredientIds || eNum.lowercase() in matchedIngredientIds)) return true
                if (ing.codes.any { it.code in matchedIngredientIds }) return true
            }
            if (q.isNotEmpty()) {
                if (ing.name.contains(q, ignoreCase = true)) return true
                if (ing.eNumber?.contains(q, ignoreCase = true) == true) return true
                if (ing.labelText?.contains(q, ignoreCase = true) == true) return true
            }
            return ing.members.any { matches(it) }
        }
        item.ingredients.filter { matches(it) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.displayName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
                            .border(0.5.dp, accent.copy(alpha = 0.4f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = stance.label.uppercase(),
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
                item.brand?.let {
                    Text(
                        it,
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (matched.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Contains matched ingredient:",
                        color = AppColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        matched.forEach { ing ->
                            IngredientChip(ing) { onIngredientClick(ing) }
                        }
                    }
                } else if (item.ingredients.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.ingredients.take(4).forEach { ing ->
                            IngredientChip(ing) { onIngredientClick(ing) }
                        }
                        if (item.ingredients.size > 4) {
                            Text(
                                "+${item.ingredients.size - 4} more",
                                color = AppColors.TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
