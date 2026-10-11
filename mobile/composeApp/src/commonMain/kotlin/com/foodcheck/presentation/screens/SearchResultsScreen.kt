package com.foodcheck.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.ads.AdsController
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.presentation.components.ErrorBanner
import com.foodcheck.presentation.components.IngredientChip
import com.foodcheck.presentation.components.IngredientSectionHeader
import com.foodcheck.presentation.components.ProductCard
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

    // No ad on the tap itself: it would stand between the user and what they asked for.
    fun openProduct(result: SearchProductResult) = onProductClick(result)

    fun openIngredient(ingredient: AnnotatedIngredient) = onIngredientClick(ingredient)

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
                "Type a product name, brand, or additive.",
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
                results.ingredients.isEmpty() && results.products.isEmpty() && shelf.isEmpty() -> {
                    if (query.isNotBlank()) {
                        Text("No matches for \"$query\".", color = AppColors.TextMuted)
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (shelf.isNotEmpty()) {
                            item { IngredientSectionHeader("My list (${shelf.size})", AppColors.Primary) }
                            items(shelf) { item ->
                                Column {
                                    Text(item.displayName, color = Color.White)
                                    item.brand?.let { Text(it, color = AppColors.TextMuted) }
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item.ingredients.forEach { ingredient ->
                                            IngredientChip(ingredient) { openIngredient(ingredient) }
                                        }
                                    }
                                }
                            }
                        }
                        if (results.ingredients.isNotEmpty()) {
                            item { IngredientSectionHeader("Ingredients (${results.ingredients.size})", AppColors.Secondary) }
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
                        if (results.products.isNotEmpty()) {
                            item { IngredientSectionHeader("Products (${results.products.size})", AppColors.Primary) }
                            items(results.products) { result ->
                                ProductCard(result) { openProduct(result) }
                            }
                        }
                    }
                }
            }
        }
    }
}
