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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer" to "https://www.lookmovie2.to/"
    )

    // Main method - matches what PlayerActivity expects
    suspend fun extract(tmdbId: Int, isMovie: Boolean, season: Int = 1, episode: Int = 1): String? = 
        extractLookMovie(tmdbId, isMovie, season, episode)

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

            if (html.contains("Thread Defence", ignoreCase = true) || 
                html.contains("recaptcha", ignoreCase = true) || 
                html.contains("challenge", ignoreCase = true)) {
                Log.d(TAG, "Verification needed for $finalUrl")
                return@withContext finalUrl
            }

            val storageRegex = if (isMovie) {
                Pattern.compile("""movie_storage"\]\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            } else {
                Pattern.compile("""show_storage"\]\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            }

            val matcher = storageRegex.matcher(html)
            if (!matcher.find()) return@withContext finalUrl

            val storage = matcher.group(1)
            val hashMatcher = Pattern.compile("""hash["']?\s*:\s*["']([^"']+)""").matcher(storage)
            val idKey = if (isMovie) "id_movie" else "id_episode"
            val idMatcher = Pattern.compile("""$idKey["']?\s*:\s*(\d+)""").matcher(storage)

            if (hashMatcher.find() && idMatcher.find()) {
                val hash = hashMatcher.group(1)
                val id = idMatcher.group(1)
                val apiPath = if (isMovie) "movie-access" else "episode-access"
                val apiUrl = "$base/api/v1/security/$apiPath?$idKey=$id&hash=$hash"

                val apiRequest = Request.Builder().url(apiUrl).apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }.build()

                val apiResp = client.newCall(apiRequest).execute()
                val jsonStr = apiResp.body?.string() ?: return@withContext finalUrl

                val json = JSONObject(jsonStr)
                val streamsObj = json.optJSONObject("streams") ?: json.optJSONObject("data")?.optJSONObject("streams")
                if (streamsObj != null && streamsObj.length() > 0) {
                    val directUrl = streamsObj.getString(streamsObj.keys().next())
                    Log.d(TAG, "✅ Direct LookMovie stream: $directUrl")
                    return@withContext directUrl
                }
            }
            return@withContext finalUrl
        } catch (e: Exception) {
            Log.e(TAG, "LookMovie extraction error", e)
            return@withContext null
        }
    }

    // Compatibility
    fun getLastChallengeUrl(): String? = null
}
