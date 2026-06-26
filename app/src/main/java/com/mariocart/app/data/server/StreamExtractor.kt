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
    private const val LOOKMOVIE_BASE = "https://www.lookmovie2.to"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(
        tmdbId: Int,
        contentType: String = "movie",
        season: Int = 1,
        episode: Int = 1
    ): String? = withContext(Dispatchers.IO) {

        val playUrl = if (contentType.lowercase() == "tv") {
            "$LOOKMOVIE_BASE/shows/play/$tmdbId/$season/$episode"
        } else {
            "$LOOKMOVIE_BASE/movies/play/$tmdbId"
        }

        Log.d(TAG, "Loading LookMovie play page: $playUrl")

        try {
            val pageRequest = Request.Builder()
                .url(playUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", LOOKMOVIE_BASE)
                .build()

            val pageResponse = client.newCall(pageRequest).execute()
            if (!pageResponse.isSuccessful) {
                pageResponse.close()
                return@withContext fallback()
            }

            val html = pageResponse.body?.string() ?: ""
            pageResponse.close()

            // Extract storage data and hash
            val storageRegex = if (contentType.lowercase() == "tv") {
                "show_storage\"\\]\\s*=\\s*(\\{.*?\\})".toRegex(RegexOption.DOT_MATCHES_ALL)
            } else {
                "movie_storage\"\\]\\s*=\\s*(\\{.*?\\})".toRegex(RegexOption.DOT_MATCHES_ALL)
            }

            val storageMatch = storageRegex.find(html)
            if (storageMatch != null) {
                val storageJson = storageMatch.groupValues[1]
                val hashMatch = "hash\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(storageJson)
                val idMatch = if (contentType.lowercase() == "tv") {
                    "id_episode\"\\s*:\\s*(\\d+)".toRegex().find(storageJson)
                } else {
                    "id_movie\"\\s*:\\s*(\\d+)".toRegex().find(storageJson)
                }

                if (hashMatch != null && idMatch != null) {
                    val hash = hashMatch.groupValues[1]
                    val id = idMatch.groupValues[1]

                    val apiUrl = if (contentType.lowercase() == "tv") {
                        "$LOOKMOVIE_BASE/api/v1/security/episode-access"
                    } else {
                        "$LOOKMOVIE_BASE/api/v1/security/movie-access"
                    }

                    val apiRequest = Request.Builder()
                        .url("$apiUrl?id=${if (contentType.lowercase() == "tv") "id_episode" else "id_movie"}=$id&hash=$hash")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .build()

                    val apiResponse = client.newCall(apiRequest).execute()
                    if (apiResponse.isSuccessful) {
                        val apiJson = apiResponse.body?.string() ?: ""
                        apiResponse.close()

                        val json = JSONObject(apiJson)
                        val streams = json.optJSONObject("streams") ?: json.optJSONObject("data")?.optJSONObject("streams")

                        if (streams != null && streams.length() > 0) {
                            val bestStream = streams.keys().next() as String
                            val streamUrl = streams.getString(bestStream)
                            Log.i(TAG, "✅ LookMovie API direct stream: $streamUrl")
                            return@withContext streamUrl
                        }
                    }
                }
            }

            // Fallback regex extraction from page
            val direct = extractDirectUrl(html, LOOKMOVIE_BASE)
            if (direct != null) return@withContext direct

        } catch (e: Exception) {
            Log.e(TAG, "LookMovie extraction error", e)
        }

        return@withContext fallback()
    }

    private fun fallback(): String {
        return "$LOOKMOVIE_BASE/movies/play/550" // Test with known ID
    }

    private fun extractDirectUrl(html: String, base: String): String? {
        val patterns = listOf(
            """["']([^"']+\.m3u8[^"']*)["']""",
            """["']([^"']+\.mp4[^"']*)["']"""
        )
        for (p in patterns) {
            val matcher = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(html)
            while (matcher.find()) {
                var url = matcher.group(1) ?: continue
                if (url.startsWith("//")) url = "https:$url"
                if (!url.startsWith("http")) url = "$base$url"
                if (url.contains(".m3u8") || url.contains(".mp4")) return url
            }
        }
        return null
    }
}
