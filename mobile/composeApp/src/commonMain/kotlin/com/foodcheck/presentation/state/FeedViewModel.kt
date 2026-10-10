package com.foodcheck.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodcheck.data.datasources.NetworkDataSource
import com.foodcheck.data.dto.StoryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel(private val network: NetworkDataSource) : ViewModel() {
    private val _stories = MutableStateFlow<List<StoryDto>>(emptyList())
    val stories = _stories.asStateFlow()
    private val _selected = MutableStateFlow<StoryDto?>(null)
    val selected = _selected.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private val _likedIds = MutableStateFlow<List<Int>>(emptyList())
    val likedIds = _likedIds.asStateFlow()

    private val pageSize = 10

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val page = network.listStories(limit = pageSize, offset = 0)
                _stories.value = page.stories
                _hasMore.value = page.hasMore
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun loadMore() {
        if (_loadingMore.value || _loading.value || !_hasMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val page = network.listStories(limit = pageSize, offset = _stories.value.size)
                val existing = _stories.value.map { it.id }.toSet()
                val appended = page.stories.filter { it.id !in existing }
                _stories.value = _stories.value + appended
                _hasMore.value = page.hasMore
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loadingMore.value = false
        }
    }

    fun open(id: Int) {
        viewModelScope.launch {
            try {
                _selected.value = network.getStory(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun close() {
        _selected.value = null
    }

    fun comment(body: String) {
        val story = _selected.value ?: return
        viewModelScope.launch {
            try {
                network.addComment(story.id, body)
                open(story.id)
                patchStoryCounts(story.id, commentDelta = 1)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun like(storyId: Int) {
        val id = storyId
        viewModelScope.launch {
            try {
                val res = network.toggleLike(id)
                val liked = res.liked
                _likedIds.value = if (liked) {
                    (_likedIds.value + id).distinct()
                } else {
                    _likedIds.value.filter { it != id }
                }
                patchStoryCounts(id, likeDelta = if (liked) 1 else -1)
                _selected.value?.takeIf { it.id == id }?.let { cur ->
                    _selected.value = cur.copy(
                        likeCount = (cur.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun patchStoryCounts(id: Int, likeDelta: Int = 0, commentDelta: Int = 0) {
        _stories.value = _stories.value.map { s ->
            if (s.id != id) s
            else s.copy(
                likeCount = (s.likeCount + likeDelta).coerceAtLeast(0),
                commentCount = (s.commentCount + commentDelta).coerceAtLeast(0),
            )
        }
    }
}
