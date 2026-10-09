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

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _stories.value = network.listStories().stories
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
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
                load()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun like() {
        val story = _selected.value ?: return
        viewModelScope.launch {
            try {
                network.toggleLike(story.id)
                open(story.id)
                load()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
