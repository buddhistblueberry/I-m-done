package com.mariocart.app.data.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Extracts a direct video stream URL from a streaming server embed page
 * using plain HTTP requests — no WebView required.
 *
 * Strategy:
 *  1. Fetch the embed page HTML via OkHttp GET.
 *  2. Scan the HTML/JS for .m3u8 or .mp4 URL patterns.
 *  3. If the page contains an <iframe>, fetch that frame too and scan it.
 *  4. Filter out known ad-network domains before returning.
 *
 * This works well for servers that embed direct stream URLs in their page
 * source without heavy JS obfuscation (VidSrc family, MoviesAPI, AutoEmbed,
 * RiveStream, etc.). Servers that gate URLs behind runtime JS will return
 * null and the player will skip to the next server automatically.
 */
object StreamExtractor {

    private const val TAG = "StreamExtractor"
    private const val TIMEOUT_MS = 15_000L

    private val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    // ── Ad domain blocklist ───────────────────────────────────────────────────
    private val adDomains = setOf(
        "doubleclick", "googlesyndication", "adservice", "adnxs",
        "outbrain", "taboola", "revcontent", "mgid", "propellerads",
        "popcash", "popads", "trafficjunky", "exoclick", "juicyads",
        "adsterra", "hilltopads", "eroadvertising", "pushground", "mondiad",
        "bidvertiser", "yieldmo", "ad-maven", "admaven", "adcash", "adfly",
        "shorte.st", "pubmatic", "openx", "appnexus", "indexexchange",
        "casalemedia", "rubiconproject", "criteo", "teads", "carbon",
        "ethicalads", "buysellads", "pagead2", "amazon-adsystem",
        "a-ads", "clickadu", "popunder", "adspyglass", "fuckingfast",
        "ssp.adkernel", "ads.yahoo", "advertising.com", "media.net",
        "primis.tech", "vidazoo", "connatix", "undertone", "sonobi",
        "sharethrough", "triplelift", "33across", "smartadserver",
        "smaato", "bidswitch", "emxdgt", "sovrn", "lijit",
        "contextweb", "conversantmedia", "demdex", "adsafeprotected"
    )

    // ── Video URL regex patterns (ordered by reliability) ────────────────────
    private val videoPatterns = listOf(
        // JSON "file" key — most common in embed players
        Regex(""""file"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)""""),
        Regex(""""file"\s*:\s*"(https?://[^"]+\.mp4[^"]*)""""),
        // JSON "src" key
        Regex(""""src"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)""""),
        Regex(""""src"\s*:\s*"(https?://[^"]+\.mp4[^"]*)""""),
        // JSON "url" key
        Regex(""""url"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)""""),
        Regex(""""url"\s*:\s*"(https?://[^"]+\.mp4[^"]*)""""),
        // JS variable assignment
        Regex("""source\s*[:=]\s*['"]?(https?://[^\s'"<>]+\.m3u8[^\s'"<>]*)"""),
        Regex("""source\s*[:=]\s*['"]?(https?://[^\s'"<>]+\.mp4[^\s'"<>]*)"""),
        // HTML5 <source src="...">
        Regex("""<source[^>]+src=["'](https?://[^"']+\.m3u8[^"']*)"""),
        Regex("""<source[^>]+src=["'](https?://[^"']+\.mp4[^"']*)"""),
        // Bare URL anywhere in page — broadest fallback
        Regex("""(https?://[^\s"'<>]+\.m3u8(?:\?[^\s"'<>]*)?)"""),
        Regex("""(https?://[^\s"'<>]+\.mp4(?:\?[^\s"'<>]*)?)""")
    )

    // ── Iframe src pattern ────────────────────────────────────────────────────
    private val iframePattern = Regex(
        """<iframe[^>]+src=["'](https?://[^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches [embedUrl] and attempts to extract a direct video stream URL.
     * Returns null if no usable URL is found (caller should try the next server).
     */
    suspend fun extract(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetch(embedUrl) ?: return@withContext null

            // Try to find a video URL in the main page first
            val direct = findVideoUrl(html)
            if (direct != null) {
                Log.d(TAG, "Found direct URL in main page: ${direct.take(80)}")
                return@withContext direct
            }

            // Follow the first same-domain or known-embed iframe
            val iframeSrc = iframePattern.find(html)?.groupValues?.get(1)
            if (!iframeSrc.isNullOrBlank()) {
                val frameHtml = fetch(iframeSrc) ?: return@withContext null
                val frameUrl  = findVideoUrl(frameHtml)
                if (frameUrl != null) {
                    Log.d(TAG, "Found URL in iframe: ${frameUrl.take(80)}")
                    return@withContext frameUrl
                }
            }

            Log.d(TAG, "No video URL found for $embedUrl")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Extract failed for $embedUrl: ${e.message}")
            null
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun fetch(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", "https://www.google.com/")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) { resp.close(); return null }
            val body = resp.body?.string()
            resp.close()
            body
        } catch (e: Exception) {
            Log.d(TAG, "Fetch failed for $url: ${e.message}")
            null
        }
    }

    private fun findVideoUrl(html: String): String? {
        for (pattern in videoPatterns) {
            val match = pattern.find(html) ?: continue
            val url = match.groupValues[1].trim()
            if (url.isBlank()) continue
            if (isAdUrl(url)) continue
            if (isSuspiciousUrl(url)) continue
            return url
        }
        return null
    }

    private fun isAdUrl(url: String): Boolean {
        val host = try {
            android.net.Uri.parse(url).host?.lowercase() ?: return false
        } catch (_: Exception) { return false }
        return adDomains.any { host.contains(it) }
    }

    /**
     * Additional sanity checks — reject URLs that look like ad tracking
     * pixels, analytics beacons, or tiny image/script files rather than video.
     */
    private fun isSuspiciousUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Must contain a video extension
        if (!lower.contains(".m3u8") && !lower.contains(".mp4")) return true
        // Reject obviously non-video paths
        val suspiciousPaths = listOf(
            "/ads/", "/ad/", "/track/", "/pixel/", "/beacon/",
            "/analytics/", "/collect/", "/event/", "/log/"
        )
        return suspiciousPaths.any { lower.contains(it) }
    }
}
