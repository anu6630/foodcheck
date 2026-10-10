package com.foodcheck.presentation.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.data.dto.StoryDto
import com.foodcheck.domain.entities.AnnotatedIngredient
import com.foodcheck.presentation.components.CompactIngredientChips
import com.foodcheck.presentation.components.LabelIngredientChips
import com.foodcheck.presentation.navigation.HandleBack
import com.foodcheck.presentation.state.FeedViewModel
import com.foodcheck.presentation.theme.AppColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedScreen(
    onIngredientClick: (AnnotatedIngredient) -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val stories by viewModel.stories.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val likedIdList by viewModel.likedIds.collectAsState()
    val likedIds = remember(likedIdList) { likedIdList.toSet() }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.load() }

    // Story detail is not a nav destination — intercept system/gesture back.
    HandleBack(enabled = selected != null) { viewModel.close() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        error?.let {
            Text(it, color = Color.Red, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
        }

        if (selected == null) {
            FeedList(
                stories = stories,
                likedIds = likedIds,
                loading = loading,
                loadingMore = loadingMore,
                hasMore = hasMore,
                onOpen = { viewModel.open(it) },
                onLike = { viewModel.like(it) },
                onComment = { viewModel.open(it) },
                onLoadMore = { viewModel.loadMore() },
            )
        } else {
            val story = selected!!
            val liked = story.id in likedIds
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        story.caption?.ifBlank { "Label scan" } ?: "Label scan",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp,
                    )
                    Text(
                        story.disclaimer
                            ?: "Status is for the substances on this label, not a certificate for the brand.",
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    LabelIngredientChips(
                        ingredients = story.ingredients,
                        onIngredientClick = onIngredientClick,
                    )
                    Spacer(Modifier.height(4.dp))
                    StoryActionBar(
                        liked = liked,
                        likeCount = story.likeCount,
                        commentCount = story.commentCount,
                        onLike = { viewModel.like(story.id) },
                        onComment = { },
                        onShare = { },
                    )
                    Text("Comments", fontWeight = FontWeight.Bold, color = Color.White)
                }
                items(story.comments, key = { it.id }) { c ->
                    Text(c.body, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = { Text("Add a comment…") },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (draft.isNotBlank()) {
                                    viewModel.comment(draft)
                                    draft = ""
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Post comment",
                                tint = AppColors.Primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedList(
    stories: List<StoryDto>,
    likedIds: Set<Int>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    onOpen: (Int) -> Unit,
    onLike: (Int) -> Unit,
    onComment: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && last >= total - 2
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) onLoadMore()
    }

    when {
        loading && stories.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        }
        stories.isEmpty() -> {
            Text("No stories yet. Share a scan from a label.", color = AppColors.TextMuted)
        }
        else -> {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Extra bottom inset so the next card peeks when the list is short/tall.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(stories, key = { _, s -> s.id }) { _, story ->
                    StoryFeedCard(
                        story = story,
                        liked = story.id in likedIds,
                        onOpen = { onOpen(story.id) },
                        onLike = { onLike(story.id) },
                        onComment = { onComment(story.id) },
                    )
                }
                if (loadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = AppColors.Primary,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryFeedCard(
    story: StoryDto,
    liked: Boolean,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
) {
    val title = story.caption?.ifBlank { "Label scan" } ?: "Label scan"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Surface.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                story.disclaimer
                    ?: "Status is for the substances on this label, not a certificate for the brand.",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (story.ingredients.isEmpty()) {
                Text("No ingredients on this label.", color = AppColors.TextMuted, fontSize = 12.sp)
            } else {
                CompactIngredientChips(ingredients = story.ingredients, maxVisible = 6)
            }
            Spacer(Modifier.height(10.dp))
            StoryActionBar(
                liked = liked,
                likeCount = story.likeCount,
                commentCount = story.commentCount,
                onLike = onLike,
                onComment = onComment,
                onShare = { },
            )
        }
    }
}

@Composable
private fun StoryActionBar(
    liked: Boolean,
    likeCount: Int,
    commentCount: Int,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ActionIcon(
            icon = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            tint = if (liked) AppColors.Red else Color.White,
            count = likeCount,
            contentDescription = "Like",
            onClick = onLike,
        )
        ActionIcon(
            icon = Icons.Default.ChatBubbleOutline,
            tint = Color.White,
            count = commentCount,
            contentDescription = "Comment",
            onClick = onComment,
        )
        ActionIcon(
            icon = Icons.Default.Share,
            tint = Color.White,
            count = null,
            contentDescription = "Share",
            onClick = onShare,
        )
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    tint: Color,
    count: Int?,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                count.toString(),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
