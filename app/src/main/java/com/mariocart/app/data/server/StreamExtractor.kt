package com.mariocart.app.data.server

import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamExtractor {

    private const val TAG = "StreamExtractor"
    private const val TIMEOUT_MS = 20_000L

    private val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val adDomains = setOf(
        "doubleclick", "googlesyndication", "adservice", "adnxs", "outbrain", "taboola"
    )

    // Ordered from most-specific to most-generic
    private val videoPatterns = listOf(
        // JWPlayer setup (multiline)
        Regex(""""file"\s*:\s*["']?(https?://[^"'\s,\}]+\.m3u8[^"'\s,\}]*)""", RegexOption.IGNORE_CASE),
        Regex(""""file"\s*:\s*["']?(https?://[^"'\s,\}]+\.mp4[^"'\s,\}]*)""", RegexOption.IGNORE_CASE),
        Regex(""""src"\s*:\s*["']?(https?://[^"'\s,\}]+\.m3u8[^"'\s,\}]*)""", RegexOption.IGNORE_CASE),
        Regex(""""src"\s*:\s*["']?(https?://[^"'\s,\}]+\.mp4[^"'\s,\}]*)""", RegexOption.IGNORE_CASE),
        // Playerjs
        Regex("""Playerjs\(\{[^}]*?file\s*:\s*["']?(https?://[^"'\s,\}]+)""", RegexOption.IGNORE_CASE),
        // Video.js / HLS
        Regex("""hls\.loadSource\(\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex(""""sources"\s*:\s*\[\s*\{\s*"src"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)"""", RegexOption.IGNORE_CASE),
        // playlist / stream keys
        Regex(""""playlist"\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)""", RegexOption.IGNORE_CASE),
        Regex(""""stream"\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)""", RegexOption.IGNORE_CASE),
        Regex(""""url"\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)""", RegexOption.IGNORE_CASE),
        // data-src / data-url attributes
        Regex("""data-(?:src|file|url)\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)""", RegexOption.IGNORE_CASE),
        // Generic bare URL
        Regex("""(https?://[^\s"'<>\)]+\.m3u8(?:\?[^\s"'<>\)]*)?)""", RegexOption.IGNORE_CASE),
        Regex("""(https?://[^\s"'<>\)]+\.mp4(?:\?[^\s"'<>\)]*)?)""", RegexOption.IGNORE_CASE),
        // Common path patterns
        Regex("""["'](https?://[^"'\s]+/(?:master|index|playlist)\.m3u8[^"'\s]*)["']""", RegexOption.IGNORE_CASE),
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main entry point. Tries server-specific extraction first, then falls back
     * to generic HTML scraping.
     */
    suspend fun extract(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val host = Uri.parse(embedUrl).host?.lowercase() ?: ""

            // 1. Server-specific extractors
            val specific = when {
                host.contains("vidsrc.me") || host.contains("vsembed") ->
                    extractVidSrcFamily(embedUrl)
                host.contains("vidlink") ->
                    extractVidLink(embedUrl)
                host.contains("autoembed") ->
                    extractAutoEmbed(embedUrl)
                else -> null
            }
            if (specific != null) {
                Log.d(TAG, "✅ Specific extractor: $specific")
                return@withContext specific
            }

            // 2. Generic HTML scrape (+ one level of iframes)
            val generic = scrapeUrl(embedUrl)
            if (generic != null) {
                Log.d(TAG, "✅ Generic scrape: $generic")
                return@withContext generic
            }

            Log.d(TAG, "No video found for $embedUrl")
            null
        } catch (e: Exception) {
            Log.e(TAG, "extract() error: ${e.message}")
            null
        }
    }

    // ── Server-specific extractors ────────────────────────────────────────────

    /**
     * VidSrc.me / VidSrc.to / vsembed.ru family.
     * Pages carry a `data-i` attribute (internal content ID).
     * The JS then calls internal AJAX endpoints — we replicate those calls.
     */
    private fun extractVidSrcFamily(pageUrl: String): String? {
        val html = fetch(pageUrl) ?: return null

        // Try plain patterns first (in case a source leaks into the HTML)
        findVideoUrl(html)?.let { return it }

        // Extract internal content ID
        val dataI = Regex("""data-i\s*=\s*["']?(\w+)""").find(html)?.groupValues?.get(1)
            ?: return null
        val origin = pageUrl.toOrigin()

        Log.d(TAG, "VidSrc data-i=$dataI origin=$origin")

        // Try all known internal AJAX paths
        val ajaxPaths = listOf(
            "$origin/ajax/embed/episode?id=$dataI",
            "$origin/ajax/embed/movie?id=$dataI",
            "$origin/ajax/v2/embed/episode?id=$dataI",
            "$origin/ajax/v2/embed/movie?id=$dataI",
            "$origin/api/source/$dataI",
            "$origin/api/v2/source/$dataI",
        )

        for (path in ajaxPaths) {
            val resp = fetchJson(path, referer = pageUrl) ?: continue
            val url = findVideoUrl(resp) ?: tryBase64Urls(resp)
            if (url != null) {
                Log.d(TAG, "VidSrc AJAX found at $path")
                return url
            }
        }

        // Fallback: follow iframes in the page
        return followIframes(html, pageUrl)
    }

    /**
     * VidLink.pro — they have a documented `/api/b/` endpoint that returns JSON
     * with a `playlist` field containing the m3u8 URL.
     */
    private fun extractVidLink(pageUrl: String): String? {
        // Parse tmdbId from URL like: https://vidlink.pro/movie/550
        //                          or: https://vidlink.pro/embed/movie/550
        val uri = Uri.parse(pageUrl)
        val segments = uri.pathSegments

        // Reconstruct API URL
        val apiUrl = when {
            segments.size >= 2 && segments[0] == "movie" -> {
                "https://vidlink.pro/api/b/movie/${segments[1]}"
            }
            segments.size >= 4 && segments[0] == "tv" -> {
                "https://vidlink.pro/api/b/tv/${segments[1]}/${segments[2]}/${segments[3]}"
            }
            segments.size >= 3 && segments[1] == "movie" -> {
                "https://vidlink.pro/api/b/movie/${segments[2]}"
            }
            segments.size >= 5 && segments[1] == "tv" -> {
                "https://vidlink.pro/api/b/tv/${segments[2]}/${segments[3]}/${segments[4]}"
            }
            else -> null
        }

        if (apiUrl != null) {
            Log.d(TAG, "VidLink API: $apiUrl")
            val resp = fetchJson(apiUrl, referer = pageUrl)
            if (resp != null) {
                // {"stream":{"playlist":"https://...m3u8"}}
                val url = Regex(""""playlist"\s*:\s*["']([^"']+\.m3u8[^"']*)["']""")
                    .find(resp)?.groupValues?.get(1)
                    ?: findVideoUrl(resp)
                if (url != null) return url
            }
        }

        // Fallback: scrape the page
        return scrapeUrl(pageUrl)
    }

    /**
     * AutoEmbed — has a known API pattern.
     */
    private fun extractAutoEmbed(pageUrl: String): String? {
        val uri = Uri.parse(pageUrl)
        val segs = uri.pathSegments
        // /embed/movie/550 → /api/v2/movie/550
        if (segs.size >= 2) {
            val type = if (segs.contains("movie")) "movie" else "tv"
            val id = segs.lastOrNull { it.all(Char::isDigit) } ?: return scrapeUrl(pageUrl)
            val apiUrl = "https://autoembed.cc/api/v2/$type/$id"
            val resp = fetchJson(apiUrl, referer = pageUrl)
            if (resp != null) {
                findVideoUrl(resp)?.let { return it }
            }
        }
        return scrapeUrl(pageUrl)
    }

    // ── Generic scraper ───────────────────────────────────────────────────────

    private fun scrapeUrl(url: String): String? {
        val html = fetch(url) ?: return null
        findVideoUrl(html)?.let { return it }
        return followIframes(html, url)
    }

    private fun followIframes(html: String, parentUrl: String): String? {
        val iframeRe = Regex("""<iframe[^>]+src=["']?(https?://[^"'\s>]+)""", RegexOption.IGNORE_CASE)
        for (match in iframeRe.findAll(html)) {
            val src = match.groupValues[1]
            if (isAdUrl(src)) continue
            val child = fetch(src) ?: continue
            findVideoUrl(child)?.let { return it }
            // One more level deep
            for (m2 in iframeRe.findAll(child)) {
                val src2 = m2.groupValues[1]
                if (isAdUrl(src2)) continue
                val child2 = fetch(src2) ?: continue
                findVideoUrl(child2)?.let { return it }
            }
        }
        return null
    }

    // ── URL finders ───────────────────────────────────────────────────────────

    private fun findVideoUrl(html: String): String? {
        for (pattern in videoPatterns) {
            for (match in pattern.findAll(html)) {
                val url = (match.groupValues.getOrNull(1)?.trim()
                    ?.takeIf { it.isNotBlank() } ?: match.value).trim()
                if (isValidVideo(url)) return url
            }
        }
        return null
    }

    /**
     * Some sites base64-encode the stream URL and call atob() in JS.
     * We can decode those without running JS.
     */
    private fun tryBase64Urls(html: String): String? {
        val atobRe = Regex("""atob\(["']([A-Za-z0-9+/=]{20,})["']\)""")
        for (m in atobRe.findAll(html)) {
            try {
                val decoded = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                if (isValidVideo(decoded)) return decoded
            } catch (_: Exception) { }
        }
        // Also look for standalone base64 strings that decode to video URLs
        val b64Re = Regex("""["']([A-Za-z0-9+/]{40,}={0,2})["']""")
        for (m in b64Re.findAll(html)) {
            try {
                val decoded = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                if (isValidVideo(decoded)) return decoded
            } catch (_: Exception) { }
        }
        return null
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun fetch(url: String, referer: String? = null): String? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer ?: url)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Sec-Fetch-Dest", "iframe")
            .header("Sec-Fetch-Mode", "navigate")
            .build()
        val resp = client.newCall(req).execute()
        val body = if (resp.isSuccessful) resp.body?.string() else null
        resp.close()
        body
    } catch (_: Exception) { null }

    private fun fetchJson(url: String, referer: String? = null): String? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer ?: url)
            .header("Accept", "application/json,text/plain,*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", url.toOrigin())
            .build()
        val resp = client.newCall(req).execute()
        val body = if (resp.isSuccessful) resp.body?.string() else null
        resp.close()
        body
    } catch (_: Exception) { null }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun isValidVideo(url: String): Boolean {
        if (url.isBlank() || url.length < 20) return false
        val lower = url.lowercase()
        return (lower.contains(".m3u8") || lower.contains(".mp4")) && !isAdUrl(url)
    }

    private fun isAdUrl(url: String): Boolean {
        val host = try {
            Uri.parse(url).host?.lowercase() ?: ""
        } catch (_: Exception) { "" }
        return adDomains.any { host.contains(it) }
    }

    private fun String.toOrigin(): String = try {
        val u = Uri.parse(this)
        "${u.scheme}://${u.host}"
    } catch (_: Exception) { this }
        }
