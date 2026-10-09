package com.foodcheck.presentation.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.foodcheck.domain.entities.ScanCandidate
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.presentation.components.IngredientChip
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
    val actionMessage by viewModel.actionMessage.collectAsState()
    val ingredients = outcome?.labelIngredients().orEmpty()
    val similar = when (val o = outcome) {
        is ScanOutcome.Suggestions -> o.candidates
        is ScanOutcome.Confident -> emptyList()
        else -> emptyList()
    }
    var saveOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveBrand by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("From this label", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Status is for these names under published rules. Not a certificate for a brand.",
            color = AppColors.TextMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ingredients.isEmpty()) {
                item { Text("No ingredients could be read from the photo.", color = AppColors.TextMuted) }
            } else {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ingredients.forEach { ingredient ->
                            IngredientChip(ingredient) { onIngredientClick(ingredient) }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { saveOpen = true }) {
                        Text("Save to my list", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { shareOpen = true }) {
                        Text("Share as story", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (similar.isNotEmpty()) {
                item {
                    Text("Similar packs", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(
                        "These are similar packs in the catalog — not a confirmed match.",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
                items(similar) { candidate ->
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
                            Text("${candidate.matchScore}%", color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (saveOpen) {
        AlertDialog(
            onDismissRequest = { saveOpen = false },
            title = { Text("Save to my list") },
            text = {
                Column {
                    OutlinedTextField(saveName, { saveName = it }, label = { Text("Name this pack") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(saveBrand, { saveBrand = it }, label = { Text("Brand (optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveToShelf(saveName, saveBrand)
                    saveOpen = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Cancel") } }
        )
    }
    if (shareOpen) {
        AlertDialog(
            onDismissRequest = { shareOpen = false },
            title = { Text("Share as story") },
            text = {
                Column {
                    Text("Posts this label in the app. It does not become an official recipe.", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(caption, { caption = it }, label = { Text("Caption (optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.publishStory(caption)
                    shareOpen = false
                }) { Text("Publish story") }
            },
            dismissButton = { TextButton(onClick = { shareOpen = false }) { Text("Cancel") } }
        )
    }
    actionMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearActionMessage() },
            title = { Text("Done") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { viewModel.clearActionMessage() }) { Text("OK") } }
        )
    }
}
