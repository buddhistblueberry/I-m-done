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

    private val _genre = MutableStateFlow("")
    val genre: StateFlow<String> = _genre

    private var searchJob: Job? = null

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        debounceSearch()
    }

    fun updateGenre(genreId: String) {
        _genre.value = genreId
        if (_query.value.isBlank() && genreId.isNotEmpty()) {
            performDiscover()
        }
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            if (_query.value.length >= 2) {
                performSearch()
            }
        }
    }

    private fun performSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = repo.search(_query.value)
                _results.value = items
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun performDiscover() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _results.value = repo.discover("movie", _genre.value.ifEmpty { null })
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
