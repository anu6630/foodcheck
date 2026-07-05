package com.foodcheck.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.presentation.components.ErrorBanner
import com.foodcheck.presentation.components.IngredientChip
import com.foodcheck.presentation.components.IngredientSectionHeader
import com.foodcheck.presentation.components.SeverityBadge
import com.foodcheck.presentation.state.ProductDetailViewModel
import androidx.compose.material.icons.filled.ChevronLeft
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform

import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(
    barcode: String,
    onBack: () -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    viewModel: ProductDetailViewModel = koinViewModel()
) {
    val product by viewModel.product.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(barcode) { viewModel.load(barcode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isIos = currentPlatform == Platform.IOS
            IconButton(
                onClick = onBack,
                modifier = if (isIos) {
                    Modifier.size(38.dp)
                } else {
                    Modifier.background(AppColors.Surface, shape = RoundedCornerShape(12.dp)).size(38.dp)
                }
            ) {
                Icon(
                    imageVector = if (isIos) Icons.Default.ChevronLeft else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(if (isIos) 28.dp else 18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text("Product Detail", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            error != null -> Text(text = error ?: "", color = MaterialTheme.colorScheme.error)
            product != null -> {
                val details = product!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().border(1.dp, AppColors.Border.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                            color = AppColors.Surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(details.product.name, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color.White)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Brand: ${details.product.brand ?: "Unknown"}",
                                            color = AppColors.Primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SeverityBadge(details.severity)
                                }
                                HorizontalDivider(color = AppColors.Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 14.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("BARCODE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(details.product.barcode, color = AppColors.TextFaint, fontSize = 13.sp)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("SOURCE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(details.product.source.uppercase(), color = AppColors.TextFaint, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    val banned = details.ingredients.filter { it.severity.lowercase() == "red" }
                    val restricted = details.ingredients.filter { it.severity.lowercase() == "amber" }
                    val safeAndUnknown = details.ingredients.filter { it.severity.lowercase() != "red" && it.severity.lowercase() != "amber" }

                    if (banned.isNotEmpty()) {
                        item { IngredientSectionHeader("🚨 Banned Additives (${banned.size})", AppColors.Red) }
                        item {
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                banned.forEach { ingredient -> IngredientChip(ingredient) { onIngredientClick(ingredient) } }
                            }
                        }
                    }
                    if (restricted.isNotEmpty()) {
                        item { IngredientSectionHeader("⚠️ Restricted/Warning Additives (${restricted.size})", AppColors.Amber) }
                        item {
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                restricted.forEach { ingredient -> IngredientChip(ingredient) { onIngredientClick(ingredient) } }
                            }
                        }
                    }
                    if (safeAndUnknown.isNotEmpty()) {
                        item { IngredientSectionHeader("🔍 General Ingredients & Safe Additives", AppColors.TextMuted) }
                        item {
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                safeAndUnknown.forEach { ingredient -> IngredientChip(ingredient) { onIngredientClick(ingredient) } }
                            }
                        }
                    }
                }
            }
        }
    }
}
