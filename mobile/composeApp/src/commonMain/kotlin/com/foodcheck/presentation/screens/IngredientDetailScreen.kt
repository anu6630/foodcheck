package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import com.foodcheck.presentation.state.IngredientDetailViewModel
import androidx.compose.material.icons.filled.ChevronLeft
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.presentation.theme.Platform
import com.foodcheck.presentation.theme.currentPlatform
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun IngredientDetailScreen(
    ingredientId: String,
    onBack: () -> Unit,
    viewModel: IngredientDetailViewModel = koinViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(ingredientId) { viewModel.load(ingredientId) }

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
            Text("Ingredient Detail", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            error != null -> Text(text = error ?: "", color = MaterialTheme.colorScheme.error)
            detail != null -> {
                val ing = detail!!
                val flaggedBans = ing.bans.filter { it.status != "permitted" }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val overallSeverity = if (flaggedBans.any { it.status == "banned" }) "red"
                                else if (flaggedBans.isNotEmpty()) "amber" else "green"
                            Box(modifier = Modifier.size(10.dp).background(AppColors.severityColor(overallSeverity), shape = RoundedCornerShape(5.dp)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ing.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                        }
                    }
                    if (ing.eNumber != null) {
                        item {
                            Text("Regulatory Code: ${ing.eNumber}", fontWeight = FontWeight.Bold, color = AppColors.SecondaryLight)
                        }
                    }
                    val category = ing.category
                    if (category != null) {
                        item {
                            Text("Category: ${category.uppercase()}", fontWeight = FontWeight.SemiBold, color = AppColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                    item {
                        HorizontalDivider(color = AppColors.Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    }

                    if (flaggedBans.isNotEmpty()) {
                        items(flaggedBans) { ban ->
                            val banColor = AppColors.severityColor(if (ban.status == "banned") "red" else "amber")
                            Surface(
                                color = banColor.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, banColor.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Country Regulation (${ban.countryCode})", fontWeight = FontWeight.Bold, color = banColor, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Status: ${ban.status.uppercase()}", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                                    if (ban.condition != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Condition: ${ban.condition}", fontSize = 13.sp, color = AppColors.TextFaint)
                                    }
                                    if (ban.regulationRef != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Ref: ${ban.regulationRef}", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            // Deliberately not "verified safe" - ban data only covers a small subset
                            // of the ingredient catalog today, so absence of a flag can mean either.
                            Text(
                                text = "Not flagged in the ${ing.trackedCountries.size} countries we track" +
                                    " (${ing.trackedCountries.joinToString(", ")})." +
                                    " Coverage is limited to a small set of ingredients today, so this isn't" +
                                    " a guarantee it's unregulated elsewhere.",
                                color = AppColors.TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
