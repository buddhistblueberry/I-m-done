package com.mariocart.app.data.subtitles

import android.util.Log
import com.mariocart.app.data.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Fetches subtitle URLs from Subdl using the TMDB ID. Free, one HTTP call. */
object SubdlFetcher {
    private const val TAG = "SubdlFetcher"
    private const val BASE = "https://api.subdl.com/api/v1/subtitles"
    private const val API_KEY = "subdl_PD4ndM3fuvKCTaGddtZnGQ23-NpydD2Be3qJk3gwIAE"

    suspend fun fetch(
        tmdbId: Int,
        contentType: String,
        season: Int = 1,
        episode: Int = 1
    ): List<Subtitle> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$BASE?tmdb_id=$tmdbId&languages=en&api_key=$API_KEY")
            if (contentType == "tv") {
                append("&type=tv&season_number=$season&episode_number=$episode")
            } else {
                append("&type=movie")
            }
        }
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            if (!json.optBoolean("status", false)) {
                Log.w(TAG, "Subdl error: ${json.optString("message")}")
                return@withContext emptyList()
            }
            val subs = json.optJSONArray("subtitles") ?: return@withContext emptyList()
            val result = mutableListOf<Subtitle>()
            for (i in 0 until subs.length()) {
                val sub = subs.getJSONObject(i)
                result.add(Subtitle(
                    url = sub.optString("url", ""),
                    language = sub.optString("language", "en"),
                    label = sub.optString("name", "CC (${sub.optString("language", "en")})")
                ))
            }
            Log.i(TAG, "Fetched ${result.size} subs from Subdl")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Subdl fetch failed: ${e.message}")
            emptyList()
        }
    }
}
