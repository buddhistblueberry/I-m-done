package com.mariocart.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MoviesViewModel : ViewModel() {

    private val repo = ContentRepository()

    private val _popular = MutableStateFlow<List<TmdbItem>>(emptyList())
    val popular: StateFlow<List<TmdbItem>> = _popular

    private val _nowPlaying = MutableStateFlow<List<TmdbItem>>(emptyList())
    val nowPlaying: StateFlow<List<TmdbItem>> = _nowPlaying

    private val _topRated = MutableStateFlow<List<TmdbItem>>(emptyList())
    val topRated: StateFlow<List<TmdbItem>> = _topRated

    private var popularPage = 1

    init { load() }

    private fun load() {
        viewModelScope.launch { _popular.value = repo.getPopularMovies() }
        viewModelScope.launch { _nowPlaying.value = repo.getNowPlaying() }
        viewModelScope.launch { _topRated.value = repo.getTopRatedMovies() }
    }

    fun loadMore() = viewModelScope.launch {
        popularPage++
        _popular.value = _popular.value + repo.getPopularMovies(popularPage)
    }
}
