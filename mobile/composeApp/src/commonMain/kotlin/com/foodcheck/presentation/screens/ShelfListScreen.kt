package com.foodcheck.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.presentation.components.IngredientChip
import com.foodcheck.presentation.state.ShelfViewModel
import com.foodcheck.presentation.theme.AppColors
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShelfListScreen(
    onBack: () -> Unit = {},
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    viewModel: ShelfViewModel = koinViewModel()
) {
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("Back", color = AppColors.Primary) }
        Text("My list", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Surface.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.displayName, fontWeight = FontWeight.SemiBold, color = Color.White)
                        item.brand?.let { Text(it, color = AppColors.TextMuted, fontSize = 12.sp) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.ingredients.forEach { ing ->
                                IngredientChip(ing) { onIngredientClick(ing) }
                            }
                        }
                    }
                }
            }
        }
    }
}
