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

    private val _results = MutableStateFlow<List<TmdbItem>>(emptyList())
    val results: StateFlow<List<TmdbItem>> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _searchType = MutableStateFlow("multi")
    val searchType: StateFlow<String> = _searchType

    private val _yearFilter = MutableStateFlow<String?>(null)
    val yearFilter: StateFlow<String?> = _yearFilter

    private val _genre = MutableStateFlow("")
    val genre: StateFlow<String> = _genre

    private val _sortBy = MutableStateFlow("popularity.desc")
    val sortBy: StateFlow<String> = _sortBy

    private val _suggestions = MutableStateFlow<List<TmdbItem>>(emptyList())
    val suggestions: StateFlow<List<TmdbItem>> = _suggestions

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _suggestions.value = repo.getTrending().take(18)
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank() && _genre.value.isNotEmpty()) {
            performDiscover()
        } else {
            debounceSearch()
        }
    }

    fun updateType(type: String) {
        _searchType.value = type
        when {
            _query.value.isNotBlank() -> performSearch()
            _genre.value.isNotEmpty() -> performDiscover()
        }
    }

    fun updateYear(year: String?) {
        _yearFilter.value = year
        when {
            _query.value.isNotBlank() -> performSearch()
            _genre.value.isNotEmpty() -> performDiscover()
        }
    }

    fun updateGenre(genreId: String) {
        _genre.value = genreId
        if (_query.value.isBlank()) {
            if (genreId.isEmpty()) {
                _results.value = emptyList()
            } else {
                performDiscover()
            }
        } else {
            performSearch()
        }
    }

    fun updateSortBy(sort: String) {
        _sortBy.value = sort
        when {
            _query.value.isNotBlank() -> performSearch()
            _genre.value.isNotEmpty() -> performDiscover()
        }
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            if (_query.value.isBlank()) {
                _results.value = emptyList()
            } else {
                performSearch()
            }
        }
    }

    private fun performSearch() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            val type = if (_searchType.value == "multi") "multi" else _searchType.value
            var items = repo.search(q, type, _yearFilter.value)
            val yf = _yearFilter.value
            if (yf != null) {
                items = items.filter { item -> item.year.isEmpty() || item.year >= yf }
            }
            if (_genre.value.isNotEmpty()) {
                items = items.filter { item -> item.genreIds.contains(_genre.value.toIntOrNull()) }
            }
            items = sortItems(items)
            _results.value = items
            _isLoading.value = false
        }
    }

    private fun performDiscover() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            val type = when (_searchType.value) {
                "tv" -> "tv"
                else -> "movie"
            }
            val genreId = _genre.value.ifEmpty { null }
            val items = repo.discover(
                type = type,
                genreId = genreId,
                sortBy = _sortBy.value,
                page = 1
            )
            _results.value = items
            _isLoading.value = false
        }
    }

    private fun sortItems(items: List<TmdbItem>): List<TmdbItem> = when (_sortBy.value) {
        "vote_average.desc"          -> items.sortedByDescending { it.voteAverage }
        "primary_release_date.desc"  -> items.sortedByDescending { it.year }
        else                         -> items
    }
}
