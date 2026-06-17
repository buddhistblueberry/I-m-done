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
 * ServerTester — Re-enabled for auto-detection.
 *
 * Probes servers in parallel to find which ones are currently reachable.
 */
object ServerTester {

    private const val TAG = "ServerTester"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Probes each server in parallel and returns a list of working servers.
     */
    suspend fun rankForContent(
        servers: List<StreamingServer>,
        tmdbId: Int,
        type: String,
        season: Int = 1,
        episode: Int = 1
    ): List<StreamingServer> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting auto-detection for ${servers.size} servers...")
        
        val results = servers.map { server ->
            async {
                val url = if (type == "movie") server.movieUrl(tmdbId) 
                          else server.tvUrl(tmdbId, season, episode)
                
                val isWorking = checkServer(url)
                if (isWorking) {
                    Log.d(TAG, "✅ Server working: ${server.name}")
                    server
                } else {
                    Log.d(TAG, "❌ Server down: ${server.name}")
                    null
                }
            }
        }.awaitAll().filterNotNull()

        if (results.isEmpty()) {
            Log.w(TAG, "No working servers found during auto-detection. Returning original list.")
            servers
        } else {
            Log.d(TAG, "Auto-detection found ${results.size} working servers.")
            results
        }
    }

    private fun checkServer(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head() // Use HEAD request for speed
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 403 // 403 often means it's up but needs browser headers
            }
        } catch (e: Exception) {
            false
        }
    }
}
