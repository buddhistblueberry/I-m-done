package com.mariocart.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.data.repository.ContentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repo = ContentRepository()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<TmdbItem>>(emptyList())
    val results: StateFlow<List<TmdbItem>> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var searchJob: Job? = null

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        _error.value = null
        debounceSearch()
    }

    fun updateGenre(genreId: String) {
        // Not used in simple version but kept for compatibility
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(600) // Slightly longer debounce
            if (_query.value.length >= 2) {
                performSearch()
            } else {
                _results.value = emptyList()
            }
        }
    }

    private fun performSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val items = repo.search(_query.value.trim())
                _results.value = items
            } catch (e: Exception) {
                _results.value = emptyList()
                _error.value = "Search failed. Check connection."
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
