package com.foodcheck.presentation.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.SearchProductResult
import com.foodcheck.presentation.components.ErrorBanner
import com.foodcheck.presentation.components.IngredientChip
import com.foodcheck.presentation.components.IngredientSectionHeader
import com.foodcheck.presentation.components.ProductCard
import com.foodcheck.presentation.state.SearchViewModel
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform

import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchResultsScreen(
    initialQuery: String,
    onProductClick: (SearchProductResult) -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.search(initialQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    AppColors.searchFieldBorderStroke?.let { stroke ->
                        Modifier.border(stroke, AppColors.searchFieldShape)
                    } ?: Modifier
                ),
            placeholder = { Text("Search products or ingredients...", color = AppColors.TextMuted.copy(alpha = 0.7f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.Primary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.search("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            shape = AppColors.searchFieldShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.searchFieldContainerColor,
                unfocusedContainerColor = AppColors.searchFieldContainerColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            error != null -> ErrorBanner(error ?: "")
            results.ingredients.isEmpty() && results.products.isEmpty() -> {
                if (query.isNotBlank()) {
                    Text("No matches for \"$query\".", color = AppColors.TextMuted)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (results.ingredients.isNotEmpty()) {
                        item { IngredientSectionHeader("Ingredients (${results.ingredients.size})", AppColors.Secondary) }
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                results.ingredients.forEach { ingredient ->
                                    IngredientChip(ingredient) { onIngredientClick(ingredient) }
                                }
                            }
                        }
                    }
                    if (results.products.isNotEmpty()) {
                        item { IngredientSectionHeader("Products (${results.products.size})", AppColors.Primary) }
                        items(results.products) { result ->
                            ProductCard(result) { onProductClick(result) }
                        }
                    }
                }
            }
        }
    }
}
