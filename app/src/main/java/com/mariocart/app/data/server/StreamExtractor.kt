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
        "Referer" to "https://www.lookmovie2.to/"
    )

    // Main flexible entry point - accepts any number of arguments
    suspend fun extract(vararg args: Any): String? {
        val tmdbId = args.getOrNull(0)?.toString()?.toIntOrNull() ?: return null
        val contentType = args.getOrNull(1) ?: "movie"
        val season = args.getOrNull(2)?.toString()?.toIntOrNull() ?: 1
        val episode = args.getOrNull(3)?.toString()?.toIntOrNull() ?: 1

        val isMovie = contentType.toString().lowercase().contains("movie") || 
                     contentType.toString().equals("true", ignoreCase = true)

        return extractLookMovie(tmdbId, isMovie, season, episode)
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
                Log.w(TAG, "Verification needed → $finalUrl")
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
                    Log.i(TAG, "✅ Direct stream found: $directUrl")
                    return@withContext directUrl
                }
            }
            return@withContext finalUrl
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for tmdbId=$tmdbId", e)
            return@withContext null
        }
    }

    private fun isVerificationPage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("thread defence") || 
               lower.contains("recaptcha") || 
               lower.contains("challenge") || 
               lower.contains("verify you are human")
    }

    fun getLastChallengeUrl(): String? = null
}
