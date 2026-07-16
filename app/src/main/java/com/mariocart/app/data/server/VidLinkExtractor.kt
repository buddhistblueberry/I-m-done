package com.mariocart.app.data.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * VidLinkExtractor — resolves a **direct playable** HLS stream URL from the
 * VidLink provider (`vidlink.pro`) using only plain HTTP (no WebView).
 *
 * ## How it works
 *
 * VidLink encrypts the TMDB id with a custom cipher hosted at `enc-dec.app`,
 * then exposes a JSON API that returns the direct `.m3u8` playlist URL:
 *
 *  1. **Encrypt** the TMDB id:
 *     `GET https://enc-dec.app/api/enc-vidlink?text={tmdbId}`
 *     → `{ "status": 200, "result": "<encodedId>" }`
 *
 *  2. **Fetch the stream playlist**:
 *     - Movie: `GET https://vidlink.pro/api/b/movie/{encodedId}?multiLang=0`
 *     - TV:    `GET https://vidlink.pro/api/b/tv/{encodedId}/{season}/{episode}?multiLang=0`
 *     → `{ "stream": { "playlist": "https://…/master.m3u8" } }`
 *
 *  3. **Verify** the playlist URL returns a real `#EXTM3U` manifest.
 *
 * This is a primary extractor (runs in the parallel race alongside VidStorm
 * and VidSrc) because it resolves a direct HLS URL in ~2 HTTP round-trips
 * with no JS execution — fast and reliable on real user devices.
 */
object VidLinkExtractor {

    private const val TAG = "VidLink"

    private const val ENC_DEC_BASE = "https://enc-dec.app/api"
    private const val VIDLINK_BASE = "https://vidlink.pro"
    private const val REFERER = "https://vidlink.pro"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Separate short-timeout client for URL verification. */
    private val verifier by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // ───────────────────────────────────────────────────────────────────────//
    //  Result type                                                          //
    // ───────────────────────────────────────────────────────────────────────//

    sealed class Result {
        /** A direct playable URL + headers ExoPlayer should send. */
        data class Stream(
            val url: String,
            val headers: Map<String, String>,
            val providerName: String = "VidLink"
        ) : Result()

        /** Extraction found nothing usable. */
        data class Error(val message: String) : Result()
    }

    // ───────────────────────────────────────────────────────────────────────//
    //  Public API                                                           //
    // ───────────────────────────────────────────────────────────────────────//

    /**
     * Resolve a direct playable stream for the given TMDB content.
     *
     * @param tmdbId       TMDB id of the movie or TV show.
     * @param contentType  "movie" or "tv".
     * @param season       season number (tv only).
     * @param episode      episode number (tv only).
     */
    suspend fun extract(
        tmdbId: Int,
        contentType: String,
        season: Int = 1,
        episode: Int = 1
    ): Result = withContext(Dispatchers.IO) {
        val type = if (contentType == "tv") "tv" else "movie"

        // Step 1: Encrypt the TMDB id via enc-dec.app.
        val encodedId = try {
            encryptTmdbId(tmdbId.toString())
        } catch (e: Exception) {
            Log.w(TAG, "enc-dec.app encryption failed: ${e.message}")
            return@withContext Result.Error("VidLink: encryption failed")
        }
        Log.d(TAG, "🔍 VidLink encoded id: $encodedId")

        // Step 2: Fetch the stream playlist from the VidLink API.
        val apiUrl = if (type == "tv") {
            "$VIDLINK_BASE/api/b/tv/$encodedId/$season/$episode?multiLang=0"
        } else {
            "$VIDLINK_BASE/api/b/movie/$encodedId?multiLang=0"
        }
        Log.d(TAG, "🔍 VidLink API: $apiUrl")

        val body = try {
            fetchJson(apiUrl)
        } catch (e: Exception) {
            Log.w(TAG, "VidLink API fetch failed: ${e.message}")
            return@withContext Result.Error("VidLink: API unreachable")
        }

        // Step 3: Parse the playlist URL from the JSON response.
        val playlistUrl = try {
            val json = JSONObject(body)
            // Response shape: { "stream": { "playlist": "https://…/master.m3u8" } }
            val stream = json.optJSONObject("stream")
            stream?.optString("playlist") ?: json.optString("playlist")
        } catch (e: Exception) {
            Log.w(TAG, "VidLink JSON parse failed: ${e.message}")
            return@withContext Result.Error("VidLink: invalid response")
        }

        if (playlistUrl.isNullOrBlank()) {
            Log.w(TAG, "VidLink: no playlist URL in response")
            return@withContext Result.Error("VidLink: no stream found")
        }
        Log.d(TAG, "🔍 VidLink playlist: $playlistUrl")

        // Step 4: Verify the HLS manifest is actually live.
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to REFERER
        )
        if (!verifyHls(playlistUrl, headers)) {
            Log.w(TAG, "VidLink: playlist verification failed (dead URL)")
            return@withContext Result.Error("VidLink: stream not playable")
        }

        Log.i(TAG, "✅ VidLink stream: $playlistUrl")
        Result.Stream(
            url = playlistUrl,
            headers = headers,
            providerName = "VidLink"
        )
    }

    // ───────────────────────────────────────────────────────────────────────//
    //  Encryption (delegated to enc-dec.app)                                //
    // ───────────────────────────────────────────────────────────────────────//

    /**
     * Calls the enc-dec.app API to encrypt the TMDB id in VidLink's custom
     * format. Returns the encoded string, or throws on failure.
     */
    private fun encryptTmdbId(tmdbId: String): String {
        val encUrl = "$ENC_DEC_BASE/enc-vidlink?text=${URLEncoder.encode(tmdbId, "UTF-8")}"
        val req = Request.Builder()
            .url(encUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, */*")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw java.io.IOException("empty body")
            val json = JSONObject(body)
            val result = json.optString("result")
            if (result.isNullOrBlank()) {
                throw java.io.IOException("enc-dec.app returned no result")
            }
            return result
        }
    }

    // ───────────────────────────────────────────────────────────────────────//
    //  Verification                                                         //
    // ───────────────────────────────────────────────────────────────────────//

    /**
     * Lightweight HLS verification: a small full GET that must return 2xx
     * and contain `#EXTM3U` in the body.
     */
    private fun verifyHls(url: String, headers: Map<String, String>): Boolean {
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            builder.get()
            verifier.newCall(builder.build()).execute().use { resp ->
                if (resp.code !in 200..299) {
                    Log.d(TAG, "VidLink verify: HTTP ${resp.code}")
                    return false
                }
                val body = resp.body?.string().orEmpty()
                val ok = body.contains("#EXTM3U")
                Log.d(TAG, "VidLink verify: ${body.length} chars, EXTM3U=$ok")
                ok
            }
        } catch (e: Exception) {
            Log.d(TAG, "VidLink verify: connection failed (${e.message})")
            false
        }
    }

    // ───────────────────────────────────────────────────────────────────────//
    //  HTTP helper                                                          //
    // ───────────────────────────────────────────────────────────────────────//

    private fun fetchJson(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", REFERER)
            .header("Origin", VIDLINK_BASE)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("HTTP ${resp.code}")
            }
            return resp.body?.string() ?: throw java.io.IOException("empty body")
        }
    }
}
