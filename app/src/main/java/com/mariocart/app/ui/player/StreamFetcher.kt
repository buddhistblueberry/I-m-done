package com.mariocart.app.ui.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct stream fetching from APIs without WebView.
 * Handles multiple streaming servers with fallback logic.
 */
class StreamFetcher(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "StreamFetcher"
        
        // List of direct stream API endpoints
        private val STREAM_SOURCES = listOf(
            "https://vidsrc.me/embed/{id}",
            "https://vidsrc.pro/embed/{id}",
            "https://embed.su/embed/{id}",
            "https://vidlink.pro/embed/{id}"
        )
    }

    /**
     * Fetch video stream URL without WebView.
     * Returns the direct video URL or null if all sources fail.
     */
    suspend fun fetchStreamUrl(
        tmdbId: Int,
        contentType: String = "movie",
        season: Int = 1,
        episode: Int = 1
    ): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching stream for TMDB ID: $tmdbId ($contentType)")

        for (source in STREAM_SOURCES) {
            try {
                val url = when (contentType.lowercase()) {
                    "tv" -> source.replace("{id}", "tv/$tmdbId/$season/$episode")
                    else -> source.replace("{id}", "movie/$tmdbId")
                }

                val videoUrl = fetchDirectVideo(url)
                if (videoUrl != null) {
                    Log.i(TAG, "✅ Found stream: $videoUrl")
                    return@withContext videoUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Source failed: $source - ${e.message}")
            }
        }

        Log.e(TAG, "❌ No stream sources available")
        return@withContext null
    }

    /**
     * Fetch direct video URL from endpoint
     */
    private fun fetchDirectVideo(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                response.close()
                
                // Try to extract video URL from response
                extractVideoUrlFromHtml(body, url)
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from $url: ${e.message}")
            null
        }
    }

    /**
     * Extract actual video URL from HTML/JSON response
     */
    private fun extractVideoUrlFromHtml(html: String, baseUrl: String): String? {
        // Try JSON first
        return try {
            val json = JSONObject(html)
            json.optString("url", null)
        } catch (e: Exception) {
            // Try regex patterns
            extractVideoUrlFromText(html, baseUrl)
        }
    }

    /**
     * Extract video URL using regex patterns
     */
    private fun extractVideoUrlFromText(text: String, baseUrl: String): String? {
        // HLS playlist
        val m3u8Pattern = """(?:"([^"]*\.m3u8[^"]*)"|'([^']*\.m3u8[^']*)')""".toRegex()
        m3u8Pattern.find(text)?.let {
            val url = it.groupValues[1].ifEmpty { it.groupValues[2] }
            return if (url.startsWith("http")) url else baseUrl.substringBeforeLast("/") + "/" + url
        }

        // MP4 files
        val mp4Pattern = """(?:"([^"]*\.mp4[^"]*)"|'([^']*\.mp4[^']*)')""".toRegex()
        mp4Pattern.find(text)?.let {
            val url = it.groupValues[1].ifEmpty { it.groupValues[2] }
            return if (url.startsWith("http")) url else baseUrl.substringBeforeLast("/") + "/" + url
        }

        // src attribute
        val srcPattern = """src\s*=\s*["']([^"']+)["']""".toRegex()
        srcPattern.find(text)?.let {
            val url = it.groupValues[1]
            return if (url.startsWith("http")) url else baseUrl.substringBeforeLast("/") + "/" + url
        }

        return null
    }
}
