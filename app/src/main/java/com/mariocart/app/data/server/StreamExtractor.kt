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
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Referer" to "https://www.lookmovie2.to/",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    suspend fun extractLookMovie(
        tmdbId: Int,
        contentType: String = "movie", // "movie" or "tv"
        season: Int = 1,
        episode: Int = 1
    ): String? = withContext(Dispatchers.IO) {
        try {
            val base = "https://www.lookmovie2.to"
            val playUrl = if (contentType == "movie") {
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

            // Handle captcha / verification
            if (html.contains("Thread Defence", ignoreCase = true) || 
                html.contains("recaptcha", ignoreCase = true) ||
                html.contains("challenge", ignoreCase = true)) {
                Log.d(TAG, "Verification needed: $finalUrl")
                return@withContext finalUrl // Let PlayerActivity handle verification
            }

            // Extract storage data
            val storageRegex = if (contentType == "movie") {
                Pattern.compile("""movie_storage"\]\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            } else {
                Pattern.compile("""show_storage"\]\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            }
            
            val matcher = storageRegex.matcher(html)
            if (!matcher.find()) {
                return@withContext finalUrl
            }

            val storage = matcher.group(1)
            val hashMatcher = Pattern.compile("""hash["']?\s*:\s*["']([^"']+)""").matcher(storage)
            val idKey = if (contentType == "movie") "id_movie" else "id_episode"
            val idMatcher = Pattern.compile("""$idKey["']?\s*:\s*(\d+)""").matcher(storage)

            if (hashMatcher.find() && idMatcher.find()) {
                val hash = hashMatcher.group(1)
                val id = idMatcher.group(1)

                val apiUrl = "$base/api/v1/security/${if (contentType == "movie") "movie-access" else "episode-access"}"
                val params = "?${idKey}=$id&hash=$hash"

                val apiRequest = Request.Builder().url("$apiUrl$params").apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }.build()

                val apiResponse = client.newCall(apiRequest).execute()
                val apiJson = apiResponse.body?.string() ?: return@withContext finalUrl
                
                val json = JSONObject(apiJson)
                val streams = json.optJSONObject("streams") ?: json.optJSONObject("data")?.optJSONObject("streams")
                
                if (streams != null && streams.length() > 0) {
                    val directUrl = streams.getString(streams.keys().next())
                    Log.d(TAG, "Direct LookMovie stream found: $directUrl")
                    return@withContext directUrl
                }
            }

            return@withContext finalUrl // fallback
        } catch (e: Exception) {
            Log.e(TAG, "LookMovie extraction failed", e)
            return@withContext null
        }
    }
}
