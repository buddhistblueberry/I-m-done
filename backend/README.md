I'm Done — Streaming Backend
============================

A FastAPI service that finds working video streams for the
ashtonhardy555-stack/I-m-done Android app.

Architecture
------------
1.  `GET /api/stream?tmdbId=...&type=movie|tv&season=&episode=`
    - Probes every configured embed provider in parallel (5-minute cache).
    - Attempts server-side direct-stream extraction (m3u8 / mp4 / mpd / mkv / webm).
    - Falls back to the best ALIVE embed URL + headers so the Android
      `StreamExtractor` (OkHttp, NOT WebView) can finish extraction
      from the user's residential IP.
    - Direct extraction often fails from datacenter IPs because the
      embed providers fingerprint AWS / GCP / Azure ranges; the app-side
      extractor succeeds from the device.

2.  `POST /api/report` lets the app tell the backend whether playback
    actually worked. Scores adjust per-server, which feeds back into the
    `find_best_stream` ranking.

Endpoints
---------
    GET  /api/                    Service info + supported types
    GET  /api/health              Health check
    GET  /api/servers             Configured providers
    GET  /api/servers/status      Live probe (alive/latency/status_code/score)
    GET  /api/stream              Auto-resolve (or pin via serverId=)
    GET  /api/embed               Build embed URL for a provider
    POST /api/cache/clear         Drop in-memory caches
    POST /api/report              App feedback (server_id, success, ...)
    GET  /api/scores              Adaptive scores
    GET  /api/history             Recent resolves

Response shape  (matches `StreamingBackendClient.kt` exactly)
-------------------------------------------------------------
    {
      "success": true,
      "url":     "https://...m3u8|mp4|mpd|...|embed-url",
      "serverId": "vidlink",
      "contentType": "application/x-mpegurl",  // MIME type
      "streamType":  "hls",                    // hls/mp4/dash/mkv/webm/embed
      "isDirect":    true,
      "headers": { "Referer": "...", "Origin": "...", "User-Agent": "..." },
      "subtitles": [{"url": "...", "label": "...", "language": "en"}]
    }

Supported stream types
----------------------
    HLS    (.m3u8)            -> ExoPlayer HlsMediaSource
    MP4    (.mp4)             -> ExoPlayer ProgressiveMediaSource
    DASH   (.mpd)             -> ExoPlayer DashMediaSource
    MKV    (.mkv)             -> ExoPlayer ProgressiveMediaSource
    WebM   (.webm)            -> ExoPlayer ProgressiveMediaSource

Android integration (one-line change)
-------------------------------------
Edit `app/src/main/java/com/mariocart/app/data/api/ApiClient.kt`:

    private const val STREAMING_BACKEND_BASE = "https://<your-backend>/"

That's it — the response schema in `StreamingBackendClient.kt` already
matches this backend exactly (camelCase params, flat StreamResponse).

The Android `PlayerActivity` already does the right thing:

    if (response.isDirect == true) { playNative(response.url) }
    else { StreamExtractor.extract(response.url, ...) }  // OkHttp, no WebView

Provider list
-------------
20 providers across three priority tiers — see `servers_config.py`.
Each provider has the correct movie / TV URL format, Referer, and
priority. Add new providers there.

Local development
-----------------
    cd /app/backend
    uvicorn server:app --reload --port 8001

The service is supervised by /etc/supervisor — it auto-restarts on
code changes.
