package com.mariocart.app.data.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

object StreamExtractor {

    private const val TAG = "StreamExtractor"
    private const val TIMEOUT_MS = 25_000L

    private val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val adDomains = setOf(
        "doubleclick", "googlesyndication", "adservice", "adnxs", "outbrain", "taboola",
        "revcontent", "mgid", "propellerads", "popcash", "popads", "trafficjunky",
        "exoclick", "juicyads", "adsterra", "hilltopads"
    )

        private val videoPatterns = listOf(
        // VidSrc / common JSON players
        Regex(""""file"\s*:\s*["']?(https?://[^"'\s]+?\.m3u8[^"'\s]*)["']?""", RegexOption.IGNORE_CASE),
        Regex(""""src"\s*:\s*["']?(https?://[^"'\s]+?\.m3u8[^"'\s]*)["']?""", RegexOption.IGNORE_CASE),
        Regex(""""url"\s*:\s*["']?(https?://[^"'\s]+?\.m3u8[^"'\s]*)["']?""", RegexOption.IGNORE_CASE),
        Regex("""sources\s*:\s*\[.*?["']?(https?://[^"'\s]+?\.m3u8)""", RegexOption.IGNORE_CASE),
        
        // Smashy / other players
        Regex("""master\.m3u8""", RegexOption.IGNORE_CASE),
        Regex("""playlist\.m3u8""", RegexOption.IGNORE_CASE),
        Regex("""<source[^>]+src=["']?(https?://[^"'\s]+?\.m3u8[^"'\s]*)""", RegexOption.IGNORE_CASE),
        
        // Broad fallback (catch any .m3u8/.mp4 in the page)
        Regex("""(https?://[^\s"'<>\)]+\.m3u8(?:\?[^\s"'<>)]*)?)""", RegexOption.IGNORE_CASE),
        Regex("""(https?://[^\s"'<>\)]+\.mp4(?:\?[^\s"'<>)]*)?)""", RegexOption.IGNORE_CASE)
    )
    )

    suspend fun extract(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Trying: $embedUrl")
            val html = fetch(embedUrl) ?: return@withContext null
            val doc = Jsoup.parse(html)

            var videoUrl = findVideoUrl(doc, html)
            if (videoUrl != null) {
                Log.d(TAG, "Found: ${videoUrl.take(120)}")
                return@withContext videoUrl
            }

            // Follow iframes (many sites hide video behind redirects)
            doc.select("iframe").forEach { iframe ->
                val iframeSrc = iframe.absUrl("src")
                if (iframeSrc.isNotBlank() && !isAdUrl(iframeSrc)) {
                    val frameHtml = fetch(iframeSrc) ?: return@forEach
                    val frameDoc = Jsoup.parse(frameHtml)
                    videoUrl = findVideoUrl(frameDoc, frameHtml)
                    if (videoUrl != null) return@withContext videoUrl
                }
            }

            Log.d(TAG, "No video URL found for $embedUrl")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting: ${e.message}")
            null
        }
    }

    private fun fetch(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", url)
                .build()
            val response = client.newCall(request).execute()
            val body = if (response.isSuccessful) response.body?.string() else null
            response.close()
            body
        } catch (e: Exception) {
            null
        }
    }

    private fun findVideoUrl(doc: org.jsoup.nodes.Document, html: String): String? {
        // Check HTML elements
        doc.select("source, video[src], [data-src], [data-video]").forEach { el ->
            val src = el.absUrl("src").ifBlank { el.attr("data-src") }
            if (isValidVideo(src)) return src
        }

        // Regex patterns for common players
        videoPatterns.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                val url = match.groupValues.getOrNull(1)?.trim() ?: match.value
                if (isValidVideo(url)) return url
            }
        }
        return null
    }

    private fun isValidVideo(url: String): Boolean {
        if (url.isBlank() || url.length < 20) return false
        val lower = url.lowercase()
        return (lower.contains(".m3u8") || lower.contains(".mp4")) &&
               !isAdUrl(url) && !lower.contains("/ad") && !lower.contains("popup")
    }

    private fun isAdUrl(url: String): Boolean {
        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        return adDomains.any { host.contains(it) }
    }
}
