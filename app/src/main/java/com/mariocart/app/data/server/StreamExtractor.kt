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
        "doubleclick", "googlesyndication", "adservice",
        "adnxs", "outbrain", "taboola", "popads", "popcash"
    )

    private val videoPatterns = listOf(
        // JSON file / src keys
        Regex(""""file"\s*:\s*["']?(https?://[^"'\s,}\]]+\.m3u8[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        Regex(""""file"\s*:\s*["']?(https?://[^"'\s,}\]]+\.mp4[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        Regex(""""src"\s*:\s*["']?(https?://[^"'\s,}\]]+\.m3u8[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        Regex(""""src"\s*:\s*["']?(https?://[^"'\s,}\]]+\.mp4[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        Regex(""""url"\s*:\s*["']?(https?://[^"'\s,}\]]+\.m3u8[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        Regex(""""url"\s*:\s*["']?(https?://[^"'\s,}\]]+\.mp4[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        // playlist / stream
        Regex(""""playlist"\s*:\s*["']?(https?://[^"'\s,}\]]+\.m3u8[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        Regex(""""stream"\s*:\s*["']?(https?://[^"'\s,}\]]+\.m3u8[^"'\s,}\]]*)""", RegexOption.IGNORE_CASE),
        // Playerjs
        Regex("""Playerjs\(\{[^}]*?file\s*:\s*["']?(https?://[^"'\s,}\]]+)""", RegexOption.IGNORE_CASE),
        // HLS.js
        Regex("""hls\.loadSource\(\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        // Video.js sources array
        Regex(""""sources"\s*:\s*\[\s*\{\s*"src"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)"""", RegexOption.IGNORE_CASE),
        // data-src / data-url attributes
        Regex("""data-(?:src|file|url|hls)\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)""", RegexOption.IGNORE_CASE),
        // JWPlayer setup
        Regex("""jwplayer\s*\([^)]+\)\s*\.setup\s*\([^)]*"file"\s*:\s*"(https?://[^"]+)""", RegexOption.IGNORE_CASE),
        // Generic bare URLs (last resort)
        Regex("""(https?://[^\s"'<>()\]]+\.m3u8(?:\?[^\s"'<>()\]]*)?)""", RegexOption.IGNORE_CASE),
        Regex("""(https?://[^\s"'<>()\]]+\.mp4(?:\?[^\s"'<>()\]]*)?)""", RegexOption.IGNORE_CASE),
        // Common path pattern
        Regex("""["'](https?://[^"'\s]+/(?:master|index|playlist)\.m3u8[^"'\s]*)["']""", RegexOption.IGNORE_CASE),
    )

    // ── Main entry ────────────────────────────────────────────────────────────

    suspend fun extract(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val host = Uri.parse(embedUrl).host?.lowercase() ?: ""
            Log.d(TAG, "Extracting: $embedUrl")

            val result = when {
                host.contains("vidsrc.me")  || host.contains("vsembed")  -> extractVidSrcFamily(embedUrl)
                host.contains("vidsrc.io")                                -> extractVidSrcFamily(embedUrl)
                host.contains("vidsrc.pm")                                -> extractVidSrcFamily(embedUrl)
                host.contains("vidsrc.to")                                -> extractVidSrcTo(embedUrl)
                host.contains("vidlink")                                  -> extractVidLink(embedUrl)
                host.contains("videasy")                                  -> extractVideasy(embedUrl)
                host.contains("autoembed")                                -> extractAutoEmbed(embedUrl)
                host.contains("2embed")                                   -> extractGenericWithApi(embedUrl)
                host.contains("embedrise")                                -> scrapeUrl(embedUrl)
                else                                                      -> scrapeUrl(embedUrl)
            }

            if (result != null) Log.d(TAG, "✅ Found: $result")
            else Log.d(TAG, "❌ Nothing found for $embedUrl")
            result
        } catch (e: Exception) {
            Log.e(TAG, "extract() error for $embedUrl: ${e.message}")
            null
        }
    }

    // ── Server-specific extractors ────────────────────────────────────────────

    /**
     * VidSrc.me / VidSrc.io / VidSrc.pm / vsembed.ru
     * All serve the same webpack bundle. Grab data-i then hit internal AJAX APIs.
     */
    private fun extractVidSrcFamily(pageUrl: String): String? {
        val html = fetch(pageUrl) ?: return null
        findVideoUrl(html)?.let { return it }
        tryBase64Urls(html)?.let { return it }

        val dataI = Regex("""data-i\s*=\s*["']?(\w+)""").find(html)?.groupValues?.get(1)
            ?: return followIframes(html, pageUrl)

        val origin = pageUrl.toOrigin()
        Log.d(TAG, "VidSrc data-i=$dataI")

        val ajaxPaths = listOf(
            "$origin/ajax/embed/episode?id=$dataI",
            "$origin/ajax/embed/movie?id=$dataI",
            "$origin/ajax/v2/embed/episode?id=$dataI",
            "$origin/ajax/v2/embed/movie?id=$dataI",
            "$origin/ajax/sources/$dataI",
            "$origin/api/source/$dataI",
            "$origin/api/v2/source/$dataI",
            "$origin/api/v3/source/$dataI",
        )
        for (path in ajaxPaths) {
            val resp = fetchJson(path, referer = pageUrl) ?: continue
            findVideoUrl(resp)?.let { return it }
            tryBase64Urls(resp)?.let { return it }
        }
        return followIframes(html, pageUrl)
    }

    /**
     * VidSrc.to — embed page redirects to vsembed.ru which is the same family.
     */
    private fun extractVidSrcTo(pageUrl: String): String? {
        // First try hitting it directly as vsembed mirror
        val html = fetch(pageUrl) ?: return null
        findVideoUrl(html)?.let { return it }

        // The real final URL after redirect is vsembed.ru — extract data-i there
        val dataI = Regex("""data-i\s*=\s*["']?(\w+)""").find(html)?.groupValues?.get(1)
        if (dataI != null) {
            val origin = pageUrl.toOrigin()
            val ajaxPaths = listOf(
                "$origin/ajax/embed/episode?id=$dataI",
                "$origin/ajax/embed/movie?id=$dataI",
                "$origin/api/source/$dataI",
                "https://vsembed.ru/ajax/embed/episode?id=$dataI",
                "https://vsembed.ru/ajax/embed/movie?id=$dataI",
                "https://vidsrc.me/ajax/embed/episode?id=$dataI",
            )
            for (path in ajaxPaths) {
                val resp = fetchJson(path, referer = pageUrl) ?: continue
                findVideoUrl(resp)?.let { return it }
            }
        }
        return followIframes(html, pageUrl)
    }

    /**
     * VidLink.pro — `/api/b/movie/{id}` returns JSON with a playlist field.
     */
    private fun extractVidLink(pageUrl: String): String? {
        val uri = Uri.parse(pageUrl)
        val segs = uri.pathSegments
        val apiUrl = when {
            segs.size >= 2 && segs[0] == "movie" ->
                "https://vidlink.pro/api/b/movie/${segs[1]}"
            segs.size >= 4 && segs[0] == "tv" ->
                "https://vidlink.pro/api/b/tv/${segs[1]}/${segs[2]}/${segs[3]}"
            segs.size >= 3 && segs[1] == "movie" ->
                "https://vidlink.pro/api/b/movie/${segs[2]}"
            segs.size >= 5 && segs[1] == "tv" ->
                "https://vidlink.pro/api/b/tv/${segs[2]}/${segs[3]}/${segs[4]}"
            else -> null
        }
        if (apiUrl != null) {
            Log.d(TAG, "VidLink API: $apiUrl")
            val resp = fetchJson(apiUrl, referer = pageUrl)
            if (resp != null) {
                Regex(""""playlist"\s*:\s*["']([^"']+\.m3u8[^"']*)["']""")
                    .find(resp)?.groupValues?.get(1)?.let { return it }
                findVideoUrl(resp)?.let { return it }
            }
        }
        return scrapeUrl(pageUrl)
    }

    /**
     * Videasy — player endpoint returns HTML with a video config.
     */
    private fun extractVideasy(pageUrl: String): String? {
        val html = fetch(pageUrl) ?: return null
        findVideoUrl(html)?.let { return it }
        // Try their API pattern
        val tmdbId = Regex("""/movie/(\d+)""").find(pageUrl)?.groupValues?.get(1)
            ?: Regex("""/tv/(\d+)""").find(pageUrl)?.groupValues?.get(1)
        if (tmdbId != null) {
            val isMovie = pageUrl.contains("/movie/")
            val apiUrl = if (isMovie)
                "https://player.videasy.net/api/movie/$tmdbId"
            else {
                val s = Regex("""/tv/\d+/(\d+)/(\d+)""").find(pageUrl)
                if (s != null) "https://player.videasy.net/api/tv/$tmdbId/${s.groupValues[1]}/${s.groupValues[2]}"
                else "https://player.videasy.net/api/tv/$tmdbId/1/1"
            }
            fetchJson(apiUrl, referer = pageUrl)?.let { resp ->
                findVideoUrl(resp)?.let { return it }
            }
        }
        return followIframes(html, pageUrl)
    }

    /**
     * AutoEmbed — has a known /api/v2/ pattern.
     */
    private fun extractAutoEmbed(pageUrl: String): String? {
        val segs = Uri.parse(pageUrl).pathSegments
        val type = when {
            segs.contains("movie") -> "movie"
            segs.contains("tv")    -> "tv"
            else -> null
        }
        val id = segs.lastOrNull { it.all(Char::isDigit) }
        if (type != null && id != null) {
            val apiUrl = "https://autoembed.cc/api/v2/$type/$id"
            fetchJson(apiUrl, referer = pageUrl)?.let { resp ->
                findVideoUrl(resp)?.let { return it }
            }
        }
        return scrapeUrl(pageUrl)
    }

    /**
     * Generic provider with possible /api/ endpoints.
     */
    private fun extractGenericWithApi(pageUrl: String): String? {
        val html = fetch(pageUrl) ?: return null
        findVideoUrl(html)?.let { return it }
        tryBase64Urls(html)?.let { return it }
        return followIframes(html, pageUrl)
    }

    // ── Generic scraper ───────────────────────────────────────────────────────

    private fun scrapeUrl(url: String): String? {
        val html = fetch(url) ?: return null
        findVideoUrl(html)?.let { return it }
        tryBase64Urls(html)?.let { return it }
        return followIframes(html, url)
    }

    private fun followIframes(html: String, parentUrl: String, depth: Int = 0): String? {
        if (depth > 2) return null
        val re = Regex("""<iframe[^>]+src=["']?(https?://[^"'\s>]+)""", RegexOption.IGNORE_CASE)
        for (m in re.findAll(html)) {
            val src = m.groupValues[1]
            if (isAdUrl(src)) continue
            val child = fetch(src) ?: continue
            findVideoUrl(child)?.let { return it }
            tryBase64Urls(child)?.let { return it }
            followIframes(child, src, depth + 1)?.let { return it }
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

    private fun tryBase64Urls(html: String): String? {
        // atob("...") calls
        Regex("""atob\(\s*["']([A-Za-z0-9+/=]{20,})["']\s*\)""").findAll(html).forEach { m ->
            try {
                val decoded = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                if (isValidVideo(decoded)) return decoded
            } catch (_: Exception) { }
        }
        // Standalone long base64 strings that decode to http URLs
        Regex("""["']([A-Za-z0-9+/]{60,}={0,2})["']""").findAll(html).forEach { m ->
            try {
                val decoded = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                if (decoded.startsWith("http") && isValidVideo(decoded)) return decoded
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
            .header("Sec-Fetch-Site", "cross-site")
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
            .header("Accept", "application/json, text/plain, */*")
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
        val host = try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        return adDomains.any { host.contains(it) }
    }

    private fun String.toOrigin(): String = try {
        val u = Uri.parse(this)
        "${u.scheme}://${u.host}"
    } catch (_: Exception) { this }
        }
