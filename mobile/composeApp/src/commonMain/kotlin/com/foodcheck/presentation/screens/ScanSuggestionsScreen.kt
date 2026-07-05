package com.foodcheck.presentation.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.domain.entities.ScanCandidate
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.presentation.components.IngredientChip
import com.foodcheck.presentation.components.IngredientSectionHeader
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.theme.AppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanSuggestionsScreen(
    viewModel: ScanViewModel,
    onCandidateClick: (ScanCandidate) -> Unit,
    onIngredientClick: (AnnotatedIngredient) -> Unit
) {
    val outcome by viewModel.outcome.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Which one did you scan?", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        when (val result = outcome) {
            is ScanOutcome.Suggestions -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(result.candidates) { candidate ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AppColors.Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable { onCandidateClick(candidate) },
                            color = AppColors.Surface.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.product.name, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Brand: ${candidate.product.brand ?: "Unknown"}", color = Color.Gray, fontSize = 12.sp)
                                }
                                Text("${candidate.matchScore}% match", color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            is ScanOutcome.IngredientsOnly -> {
                Text(
                    "We couldn't confidently match a product, but here's what we read from the label:",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (result.ingredients.isEmpty()) {
                    Text("No ingredients could be read from the photo.", color = AppColors.TextMuted)
                } else {
                    IngredientSectionHeader("Detected Ingredients (${result.ingredients.size})", AppColors.Secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        result.ingredients.forEach { ingredient ->
                            IngredientChip(ingredient) { onIngredientClick(ingredient) }
                        }
                    }
                }
            }
            else -> Text("No scan result to show.", color = AppColors.TextMuted)
        }
    }
}
