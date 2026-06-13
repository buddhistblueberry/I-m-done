package com.mariocart.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repo = ContentRepository()

    private val _heroItems = MutableStateFlow<List<TmdbItem>>(emptyList())
    val heroItems: StateFlow<List<TmdbItem>> = _heroItems

    private val _trending = MutableStateFlow<List<TmdbItem>>(emptyList())
    val trending: StateFlow<List<TmdbItem>> = _trending

    private val _nowPlaying = MutableStateFlow<List<TmdbItem>>(emptyList())
    val nowPlaying: StateFlow<List<TmdbItem>> = _nowPlaying

    private val _popularTV = MutableStateFlow<List<TmdbItem>>(emptyList())
    val popularTV: StateFlow<List<TmdbItem>> = _popularTV

    private val _topRated = MutableStateFlow<List<TmdbItem>>(emptyList())
    val topRated: StateFlow<List<TmdbItem>> = _topRated

    private val _popularMovies = MutableStateFlow<List<TmdbItem>>(emptyList())
    val popularMovies: StateFlow<List<TmdbItem>> = _popularMovies

    private var trendingPage = 1
    private var nowPlayingPage = 1
    private var popularTVPage = 1
    private var topRatedPage = 1
    private var popularMoviesPage = 1

    init { loadAll() }

    private fun loadAll() {
        viewModelScope.launch {
            val trending = repo.getTrending()
            _heroItems.value = trending.filter { it.backdropPath != null }.take(8)
            _trending.value = trending.filter { it.isMovie }.take(15)
        }
        viewModelScope.launch { _nowPlaying.value = repo.getNowPlaying() }
        viewModelScope.launch { _popularTV.value = repo.getPopularTV() }
        viewModelScope.launch { _topRated.value = repo.getTopRatedMovies() }
        viewModelScope.launch { _popularMovies.value = repo.getPopularMovies() }
    }

    fun loadMoreTrending() = viewModelScope.launch {
        trendingPage++
        _trending.value = _trending.value + repo.getTrending(trendingPage).filter { it.isMovie }
    }

    fun loadMoreNowPlaying() = viewModelScope.launch {
        nowPlayingPage++
        _nowPlaying.value = _nowPlaying.value + repo.getNowPlaying(nowPlayingPage)
    }

    fun loadMorePopularTV() = viewModelScope.launch {
        popularTVPage++
        _popularTV.value = _popularTV.value + repo.getPopularTV(popularTVPage)
    }

    fun loadMoreTopRated() = viewModelScope.launch {
        topRatedPage++
        _topRated.value = _topRated.value + repo.getTopRatedMovies(topRatedPage)
    }

    fun loadMorePopularMovies() = viewModelScope.launch {
        popularMoviesPage++
        _popularMovies.value = _popularMovies.value + repo.getPopularMovies(popularMoviesPage)
    }
}
