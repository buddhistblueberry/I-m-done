package com.mariocart.app.data.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamExtractor {

    private const val TAG = "StreamExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun extract(
        tmdbId: Int,
        contentType: String,
        season: Int = 1,
        episode: Int = 1
    ): String? = withContext(Dispatchers.IO) {
        val servers = listOf(
            "https://vidlink.pro",
            "https://vidsrc.to",
            "https://vidsrc-embed.ru",
            "https://vsembed.ru",
            "https://embed.su"
        )

        for (base in servers) {
            try {
                val url = when (contentType.lowercase()) {
                    "tv" -> "$base/tv/$tmdbId/$season/$episode"
                    else -> "$base/movie/$tmdbId"
                }

                Log.d(TAG, "Trying server: $url")

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Referer", base)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }

                val body = response.body?.string() ?: ""
                response.close()

                // Try to extract direct video links
                val directUrl = extractDirectUrl(body, base)
                if (!directUrl.isNullOrBlank()) {
                    Log.i(TAG, "✅ Found direct stream: $directUrl")
                    return@withContext directUrl
                }

            } catch (e: Exception) {
                Log.w(TAG, "Server failed: $base", e)
            }
        }

        Log.w(TAG, "No direct stream found, returning fallback embed")
        // Return a good embed as last resort
        val fallbackBase = "https://vidlink.pro"
        return@withContext when (contentType.lowercase()) {
            "tv" -> "$fallbackBase/tv/$tmdbId/$season/$episode"
            else -> "$fallbackBase/movie/$tmdbId"
        }
    }

    private fun extractDirectUrl(html: String, base: String): String? {
        // m3u8 patterns
        val m3u8Regex = """["']([^"']*\.m3u8[^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
        m3u8Regex.find(html)?.let {
            var url = it.groupValues[1]
            if (!url.startsWith("http")) url = "$base$url"
            return url
        }

        // mp4 patterns
        val mp4Regex = """["']([^"']*\.mp4[^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
        mp4Regex.find(html)?.let {
            var url = it.groupValues[1]
            if (!url.startsWith("http")) url = "$base$url"
            return url
        }

        return null
    }
}
