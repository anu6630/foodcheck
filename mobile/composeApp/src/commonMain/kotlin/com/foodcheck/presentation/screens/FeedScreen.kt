package com.foodcheck.presentation.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.presentation.components.IngredientChip
import com.foodcheck.presentation.state.FeedViewModel
import com.foodcheck.presentation.theme.AppColors
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    onBack: () -> Unit = {},
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val stories by viewModel.stories.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val error by viewModel.error.collectAsState()
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = {
            if (selected == null) onBack() else viewModel.close()
        }) { Text(if (selected == null) "Back" else "All stories", color = AppColors.Primary) }
        Text(if (selected == null) "Community" else "Story", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(8.dp))
        error?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
        if (selected == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(stories) { story ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.open(story.id) },
                        color = AppColors.Surface.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(story.caption?.ifBlank { "Label scan" } ?: "Label scan", fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                story.disclaimer ?: "Status is for the substances on this label, not a certificate for the brand.",
                                color = AppColors.TextMuted,
                                fontSize = 11.sp
                            )
                            Text("${story.commentCount} comments · ${story.likeCount} likes", color = AppColors.Primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            val story = selected!!
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TextButton(onClick = { viewModel.close() }) { Text("Back", color = AppColors.Primary) }
                    Text(story.caption ?: "Label scan", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(story.disclaimer ?: "", color = AppColors.TextMuted, fontSize = 11.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        story.ingredients.forEach { ing ->
                            IngredientChip(ing) { onIngredientClick(ing) }
                        }
                    }
                    TextButton(onClick = { viewModel.like() }) { Text("Like (${story.likeCount})", color = AppColors.Primary) }
                    Text("Comments", fontWeight = FontWeight.Bold, color = Color.White)
                }
                items(story.comments) { c ->
                    Text(c.body, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                item {
                    OutlinedTextField(draft, { draft = it }, label = { Text("Write a comment") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        if (draft.isNotBlank()) {
                            viewModel.comment(draft)
                            draft = ""
                        }
                    }) { Text("Post comment", color = AppColors.Primary) }
                }
            }
        }
    }
}
