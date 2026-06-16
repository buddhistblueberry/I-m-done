package com.mariocart.app.data.repository

import com.mariocart.app.BuildConfig
import com.mariocart.app.data.api.ApiClient
import com.mariocart.app.data.model.StreamingServer
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.data.model.TmdbResponse
import com.mariocart.app.data.model.TvSeasonsResponse

class ContentRepository {

    private val api = ApiClient.tmdbApi
    private val key = BuildConfig.TMDB_API_KEY

    // ── Streaming Servers ──────────────────────────────────────────────────────
    // Ordered by tested reachability. Servers confirmed dead as of testing have
    // been removed. VidSrc.me family uses a multi-step extraction in StreamExtractor.
    val streamingServers: List<StreamingServer> = listOf(

        // ── Tier 1: VidSrc family (most reachable) ─────────────────────────────
        StreamingServer("VidSrc.me",    "https://vidsrc.me/embed"),
        StreamingServer("VidSrc.to",    "https://vidsrc.to/embed"),
        StreamingServer("VidSrc.pm",    "https://vidsrc.pm/embed"),
        StreamingServer("VidSrc.dev",   "https://vidsrc.dev/embed"),
        StreamingServer("VidSrc.in",    "https://vidsrc.in/embed"),
        StreamingServer("VidSrc.nl",    "https://vidsrc.nl/embed"),
        StreamingServer("VidSrc2",      "https://vidsrc2.to/embed"),
        StreamingServer("VidSrc.lol",   "https://vidsrc.lol/embed"),

        // ── Tier 2: Other reachable embed providers ────────────────────────────
        StreamingServer("VidLink",      "https://vidlink.pro"),
        StreamingServer("NontonGo",     "https://www.nontongo.win/embed"),
        StreamingServer("2Embed.cc",    "https://www.2embed.cc/embed"),
        StreamingServer("2Embed.skin",  "https://www.2embed.skin/embed"),
        StreamingServer("MultiEmbed",   "https://multiembed.mov/embed"),
        StreamingServer("EmbedSito",    "https://embedsito.com/embed"),

        // ── Tier 3: Fallbacks ──────────────────────────────────────────────────
        StreamingServer("AutoEmbed",    "https://autoembed.cc/embed"),
        StreamingServer("AutoEmbed.co", "https://autoembed.co/embed"),
        StreamingServer("SuperEmbed",   "https://superembed.stream/embed"),
        StreamingServer("SmashyStream", "https://smashystream.com/embed"),
        StreamingServer("MoviesAPI",    "https://moviesapi.club/embed"),
        StreamingServer("RiveStream",   "https://rivestream.live/embed"),
        StreamingServer("Embed.su",     "https://embed.su/embed"),
        StreamingServer("FlixEmbed",    "https://flixembed.net/embed"),
        StreamingServer("EmbedMe",      "https://embedme.top/embed"),
        StreamingServer("StreamWish",   "https://streamwish.to/embed"),
        StreamingServer("DoodStream",   "https://dood.to/embed"),
        StreamingServer("StreamTape",   "https://streamtape.com/embed"),
        StreamingServer("MixDrop",      "https://mixdrop.ag/embed"),
        StreamingServer("GoMovies",     "https://gomovies.sx/embed"),
        StreamingServer("CineZone",     "https://cinezone.to/embed"),
        StreamingServer("HiMovies",     "https://himovies.sx/embed"),
        StreamingServer("SFlix",        "https://sflix.to/embed"),
        StreamingServer("LookMovie",    "https://lookmovie2.to/embed"),
        StreamingServer("Flix2Day",     "https://flix2day.to/embed"),
        StreamingServer("123Movies",    "https://w4.123moviesfree.net/embed"),
        StreamingServer("Zoechip",      "https://zoechip.cc/embed"),
        StreamingServer("FMovies",      "https://fmovies.ps/embed"),
        StreamingServer("YesMovies",    "https://yesmovies.mn/embed"),
        StreamingServer("SolarMovie",   "https://solarmovie.pe/embed"),
        StreamingServer("PrimeWire",    "https://www.primewire.tf/embed"),
        StreamingServer("NovaCinema",   "https://novacinema.app/embed"),
        StreamingServer("FlixHive",     "https://flixhive.net/embed"),
    )

    // ── TMDB Content ────────────────────────────────────────────────────────────

    private fun filterEnglish(items: List<TmdbItem>): List<TmdbItem> =
        items.filter { (it.originalLanguage == null || it.originalLanguage == "en") && it.posterPath != null }

    suspend fun getTrending(page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.getTrending(key, page = page).results) }.getOrDefault(emptyList())

    suspend fun getNowPlaying(page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.getNowPlaying(key, page = page).results) }.getOrDefault(emptyList())

    suspend fun getPopularMovies(page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.getPopularMovies(key, page = page).results) }.getOrDefault(emptyList())

    suspend fun getTopRatedMovies(page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.getTopRatedMovies(key, page = page).results) }.getOrDefault(emptyList())

    suspend fun getPopularTV(page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.getPopularTV(key, page = page).results) }.getOrDefault(emptyList())

    suspend fun getAiringToday(page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.getAiringToday(key, page = page).results) }.getOrDefault(emptyList())

    suspend fun getTopRatedTV(page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.getTopRatedTV(key, page = page).results) }.getOrDefault(emptyList())

    suspend fun getTvDetails(tvId: Int): TvSeasonsResponse? =
        runCatching { api.getTvDetails(tvId, key) }.getOrNull()

    suspend fun discover(
        type: String,
        genreId: String? = null,
        sortBy: String = "popularity.desc",
        page: Int = 1
    ): List<TmdbItem> =
        runCatching {
            filterEnglish(api.discover(type, key, genreId, sortBy = sortBy, page = page).results)
        }.getOrDefault(emptyList())

    suspend fun search(query: String, type: String = "multi", year: String? = null, page: Int = 1): List<TmdbItem> =
        runCatching { filterEnglish(api.search(type, key, query, year = year, page = page).results) }.getOrDefault(emptyList())
}
