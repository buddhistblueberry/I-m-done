package com.mariocart.app.data.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamExtractor {

    private const val TAG = "StreamExtractor"
    private const val TIMEOUT_MS = 12_000L   // Shorter to avoid hanging

    private val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val adDomains = setOf("doubleclick", "googlesyndication", "adservice", "adnxs", "outbrain", "taboola")

    private val videoPatterns = listOf(
        Regex(""""file"\s*:\s*["']?(https?://[^"'\s]+?\.m3u8[^"'\s]*)["']?""", RegexOption.IGNORE_CASE),
        Regex(""""src"\s*:\s*["']?(https?://[^"'\s]+?\.m3u8[^"'\s]*)["']?""", RegexOption.IGNORE_CASE),
        Regex("""sources\s*:\s*\[.*?["']?(https?://[^"'\s]+?\.m3u8)""", RegexOption.IGNORE_CASE),
        Regex("""master\.m3u8""", RegexOption.IGNORE_CASE),
        Regex("""(https?://[^\s"'<>\)]+\.m3u8(?:\?[^\s"'<>)]*)?)""", RegexOption.IGNORE_CASE),
        Regex("""(https?://[^\s"'<>\)]+\.mp4(?:\?[^\s"'<>)]*)?)""", RegexOption.IGNORE_CASE)
    )

    suspend fun extract(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Trying $embedUrl")
            val html = fetch(embedUrl) ?: return@withContext null

            var videoUrl = findVideoUrl(html)
            if (videoUrl != null) return@withContext videoUrl

            // Aggressive iframe following
            val iframeRegex = Regex("""<iframe[^>]+src=["']?(https?://[^"'\s>]+)""", RegexOption.IGNORE_CASE)
            iframeRegex.findAll(html).forEach { match ->
                val iframeSrc = match.groupValues[1]
                if (!isAdUrl(iframeSrc)) {
                    val frameHtml = fetch(iframeSrc) ?: return@forEach
                    videoUrl = findVideoUrl(frameHtml)
                    if (videoUrl != null) return@withContext videoUrl
                }
            }

            Log.d(TAG, "No video found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            null
        }
    }

    private fun fetch(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", url)
                .build()
            val resp = client.newCall(req).execute()
            val body = if (resp.isSuccessful) resp.body?.string() else null
            resp.close()
            body
        } catch (e: Exception) { null }
    }

    private fun findVideoUrl(html: String): String? {
        for (pattern in videoPatterns) {
            pattern.findAll(html).forEach { match ->
                val url = match.groupValues.getOrNull(1)?.trim() ?: match.value
                if (url.contains(".m3u8") || url.contains(".mp4")) {
                    if (!isAdUrl(url)) return url
                }
            }
        }
        return null
    }

    private fun isAdUrl(url: String): Boolean {
        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        return adDomains.any { host.contains(it) }
    }
}
