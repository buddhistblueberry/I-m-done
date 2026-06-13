package com.mariocart.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mariocart.app.data.model.Genre
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BrowseViewModel : ViewModel() {

    private val repo = ContentRepository()

    private val _items = MutableStateFlow<List<TmdbItem>>(emptyList())
    val items: StateFlow<List<TmdbItem>> = _items

    private val _selectedGenre = MutableStateFlow<Genre?>(null)
    val selectedGenre: StateFlow<Genre?> = _selectedGenre

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var page = 1

    init {
        loadGenre(null, "movie")
    }

    fun loadGenre(genre: Genre?, type: String = "movie") {
        _selectedGenre.value = genre
        page = 1
        viewModelScope.launch {
            _isLoading.value = true
            _items.value = repo.discover(
                type = genre?.type ?: type,
                genreId = genre?.id?.takeIf { it.isNotEmpty() },
                page = 1
            )
            _isLoading.value = false
        }
    }

    fun loadMore() {
        val genre = _selectedGenre.value
        page++
        viewModelScope.launch {
            val more = repo.discover(
                type = genre?.type ?: "movie",
                genreId = genre?.id?.takeIf { it.isNotEmpty() },
                page = page
            )
            _items.value = _items.value + more
        }
    }
}
