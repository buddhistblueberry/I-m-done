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

    private var searchJob: Job? = null

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.length < 2) {
            _results.value = emptyList()
            return
        }
        debounceSearch()
    }

    fun updateGenre(genreId: String) {
        // For future use / compatibility
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(700)
            if (_query.value.length >= 2) {
                performSearch()
            }
        }
    }

    private fun performSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = repo.search(_query.value.trim())
                _results.value = items
            } catch (e: Exception) {
                e.printStackTrace()
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
