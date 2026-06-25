package com.mariocart.app.data.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object StreamExtractor {

    private const val TAG = "StreamExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer" to "https://www.lookmovie2.to/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    )

    // Overloads to handle different call signatures from PlayerActivity
    suspend fun extract(tmdbId: Any, contentType: Any = "movie", season: Any = 1, episode: Any = 1): String? {
        return extractWithExtras(tmdbId, contentType, season, episode)
    }

    suspend fun extract(tmdbId: Any, contentType: Any, season: Any, episode: Any, extra1: Any?, extra2: Any?): String? {
        return extractWithExtras(tmdbId, contentType, season, episode)
    }

    private suspend fun extractWithExtras(
        tmdbId: Any,
        contentType: Any = "movie",
        season: Any = 1,
        episode: Any = 1
    ): String? {
        val id = tmdbId.toString().toIntOrNull() ?: return null
        val isMovie = contentType.toString().lowercase().contains("movie") || 
                     contentType.toString().equals("true", ignoreCase = true)
        val s = season.toString().toIntOrNull() ?: 1
        val e = episode.toString().toIntOrNull() ?: 1
        return extractLookMovie(id, isMovie, s, e)
    }

    suspend fun extractLookMovie(tmdbId: Int, isMovie: Boolean, season: Int = 1, episode: Int = 1): String? = withContext(Dispatchers.IO) {
        try {
            val base = "https://www.lookmovie2.to"
            val playUrl = if (isMovie) {
                "$base/movies/play/$tmdbId"
            } else {
                "$base/shows/play/$tmdbId/$season/$episode"
            }

            val request = Request.Builder().url(playUrl).apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }.build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null
            val finalUrl = response.request.url.toString()

            if (isVerificationPage(html)) {
                Log.w(TAG, "Verification needed: $finalUrl")
                return@withContext finalUrl
            }

            val storageRegex = if (isMovie) {
                Pattern.compile("""movie_storage["']\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            } else {
                Pattern.compile("""show_storage["']\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            }

            val matcher = storageRegex.matcher(html)
            if (!matcher.find()) return@withContext finalUrl

            val storage = matcher.group(1)
            val hashMatcher = Pattern.compile("""hash["']?\s*:\s*["']([^"']+)""").matcher(storage)
            val idKey = if (isMovie) "id_movie" else "id_episode"
            val idMatcher = Pattern.compile("""$idKey["']?\s*:\s*(\d+)""").matcher(storage)

            if (hashMatcher.find() && idMatcher.find()) {
                val hash = hashMatcher.group(1)
                val itemId = idMatcher.group(1)
                val apiPath = if (isMovie) "movie-access" else "episode-access"
                val apiUrl = "$base/api/v1/security/$apiPath?$idKey=$itemId&hash=$hash"

                val apiRequest = Request.Builder().url(apiUrl).apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }.build()

                val apiResp = client.newCall(apiRequest).execute()
                val jsonStr = apiResp.body?.string() ?: return@withContext finalUrl

                val json = JSONObject(jsonStr)
                val streams = json.optJSONObject("streams") ?: json.optJSONObject("data")?.optJSONObject("streams")
                if (streams != null && streams.length() > 0) {
                    val directUrl = streams.getString(streams.keys().next())
                    Log.i(TAG, "✅ Direct stream: $directUrl")
                    return@withContext directUrl
                }
            }
            return@withContext finalUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream", e)
            return@withContext null
        }
    }

    private fun isVerificationPage(html: String): Boolean {
        return html.contains("Thread Defence", ignoreCase = true) ||
               html.contains("recaptcha", ignoreCase = true) ||
               html.contains("challenge", ignoreCase = true) ||
               html.contains("verify you are human", ignoreCase = true)
    }

    fun getLastChallengeUrl(): String? = null
}
