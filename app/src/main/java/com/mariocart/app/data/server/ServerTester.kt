package com.mariocart.app.data.server

import android.util.Log
import com.mariocart.app.data.model.StreamingServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ServerTester — Probes servers in parallel and ranks them by latency.
 *
 * Each server is tested with a timed HEAD request against the actual
 * content URL. Servers that respond are sorted fastest-first so that
 * PlayerActivity always tries the quickest reachable server first.
 *
 * Servers that fail to respond within the timeout are excluded entirely
 * unless NO servers respond, in which case the original list is returned
 * as a safe fallback.
 */
object ServerTester {

    private const val TAG = "ServerTester"
    private const val CONNECT_TIMEOUT_S = 6L
    private const val READ_TIMEOUT_S    = 6L

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Probes every server in [servers] in parallel for the given content,
     * measures each one's response latency, and returns the reachable
     * servers sorted fastest-first.
     *
     * A server is considered "reachable" when it returns HTTP 200, 301,
     * 302, 403, or any 2xx/3xx code — a 403 typically means the CDN is
     * alive but requires a real browser session, which is fine for WebView.
     *
     * @return Reachable servers sorted by ascending latency, or [servers]
     *         unchanged if none respond (so playback can still be attempted).
     */
    suspend fun rankForContent(
        servers: List<StreamingServer>,
        tmdbId: Int,
        type: String,
        season: Int = 1,
        episode: Int = 1
    ): List<StreamingServer> = withContext(Dispatchers.IO) {

        Log.d(TAG, "Probing ${servers.size} servers for tmdbId=$tmdbId type=$type ...")

        data class Result(val server: StreamingServer, val latencyMs: Long)

        val results: List<Result> = servers.map { server ->
            async {
                val url = if (type == "movie") server.movieUrl(tmdbId)
                          else server.tvUrl(tmdbId, season, episode)
                val latency = measureLatency(url)
                if (latency >= 0) {
                    Log.d(TAG, "✅ ${server.name} — ${latency}ms")
                    Result(server, latency)
                } else {
                    Log.d(TAG, "❌ ${server.name} — unreachable")
                    null
                }
            }
        }.awaitAll().filterNotNull()

        if (results.isEmpty()) {
            Log.w(TAG, "No reachable servers found — returning original list as fallback.")
            servers
        } else {
            val ranked = results.sortedBy { it.latencyMs }.map { it.server }
            Log.d(TAG, "Ranked ${ranked.size} servers: ${ranked.joinToString { it.name }}")
            ranked
        }
    }

    /**
     * Sends a HEAD request to [url] and returns the round-trip time in
     * milliseconds, or -1 if the request fails or times out.
     */
    private fun measureLatency(url: String): Long {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
                )
                .build()

            val start = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                // Accept any code that indicates the server is alive.
                // 403 = alive but needs browser context (fine for WebView).
                if (response.isSuccessful || response.code in 300..403) elapsed else -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }
}
