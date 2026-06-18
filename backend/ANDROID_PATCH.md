ANDROID PATCH GUIDE
===================

The backend response schema already matches your existing
`StreamingBackendClient.kt` -- but the data class is missing two new
fields the backend now returns (`streamType`, `subtitles`). Adding them
is optional; Gson will silently ignore unknown fields if you skip this.

To get FULL support for HLS / MP4 / DASH / MKV / WebM + subtitles +
adaptive server scoring, apply the patches below.

------------------------------------------------------------
1) ApiClient.kt  --  point at the new backend
------------------------------------------------------------
File: app/src/main/java/com/mariocart/app/data/api/ApiClient.kt

REPLACE:
    private const val STREAMING_BACKEND_BASE = "https://3000-i59hdrx6en2f31d2ghor8-c8b9202c.us1.manus.computer/"

WITH (your deployed URL -- whatever you point it at):
    private const val STREAMING_BACKEND_BASE = "https://YOUR-BACKEND-DOMAIN/"

The "/" is required by Retrofit. Endpoints in StreamingBackendClient.kt
already include the "api/" prefix, so don't add it here.


------------------------------------------------------------
2) StreamingBackendClient.kt  --  add the new optional fields
------------------------------------------------------------
File: app/src/main/java/com/mariocart/app/data/api/StreamingBackendClient.kt

Replace the StreamResponse data class with:

    data class Subtitle(
        val url: String,
        val label: String? = null,
        val language: String? = null,
    )

    data class StreamResponse(
        val success: Boolean,
        val url: String? = null,
        val serverId: String? = null,
        val contentType: String? = null,    // MIME type
        val streamType: String? = null,     // hls / mp4 / dash / mkv / webm / embed
        val isDirect: Boolean? = null,
        val headers: Map<String, String>? = null,
        val subtitles: List<Subtitle>? = null,
        val cached: Boolean? = null,
        val error: String? = null,
    )

Add a new endpoint to StreamingBackendApi:

    data class ReportBody(
        val server_id: String,
        val success: Boolean,
        val tmdb_id: Int? = null,
        val content_type: String? = null,
        val error: String? = null,
    )

    @retrofit2.http.POST("api/report")
    suspend fun reportPlayback(@retrofit2.http.Body body: ReportBody): Map<String, Any?>


------------------------------------------------------------
3) PlayerActivity.kt  --  support all stream types + report success
------------------------------------------------------------
File: app/src/main/java/com/mariocart/app/ui/player/PlayerActivity.kt

Replace your `playNative(url: String)` with:

    private fun playNative(url: String, streamType: String? = null, headers: Map<String,String>? = null) {
        runOnUiThread {
            loadingOverlay.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            serverButton.visibility = View.VISIBLE

            exoPlayer?.release()

            // Build a HttpDataSource factory that honours backend-provided headers
            val httpDsf = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(headers?.get("User-Agent") ?: "ExoPlayer")
                .setDefaultRequestProperties(headers ?: emptyMap())
                .setAllowCrossProtocolRedirects(true)

            val mediaItem = MediaItem.fromUri(url)
            val source: androidx.media3.exoplayer.source.MediaSource = when {
                streamType == "hls"  || url.contains(".m3u8") ->
                    androidx.media3.exoplayer.hls.HlsMediaSource.Factory(httpDsf).createMediaSource(mediaItem)
                streamType == "dash" || url.contains(".mpd")  ->
                    androidx.media3.exoplayer.dash.DashMediaSource.Factory(httpDsf).createMediaSource(mediaItem)
                else ->
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(httpDsf).createMediaSource(mediaItem)
            }

            exoPlayer = ExoPlayer.Builder(this).build().apply {
                setMediaSource(source)
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("PlayerActivity", "Native Playback Error: ${error.message}")
                        reportPlayback(false, error.message)
                        showError("Playback failed. Try another server.")
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) reportPlayback(true)
                    }
                })
            }
            playerView.player = exoPlayer
        }
    }

    private var reportedThisAttempt = false
    private fun reportPlayback(success: Boolean, errorMsg: String? = null) {
        if (reportedThisAttempt) return
        reportedThisAttempt = true
        val sid = currentServerName.takeIf { it.isNotBlank() } ?: return
        lifecycleScope.launch {
            try {
                ApiClient.streamingBackendApi.reportPlayback(
                    com.mariocart.app.data.api.ReportBody(
                        server_id = sid, success = success, tmdb_id = tmdbId,
                        content_type = contentType, error = errorMsg,
                    )
                )
            } catch (_: Exception) {}
        }
    }

Then update the existing callers in `startDiscovery` and `continueWithServers`
to pass the extra info:

    if (response.success && response.url != null) {
        if (response.isDirect == true) {
            currentServerName = response.serverId ?: ""
            reportedThisAttempt = false
            playNative(response.url, response.streamType, response.headers)
        } else {
            // Backend returned embed URL -- on-device extraction (no WebView)
            val directUrl = StreamExtractor.extract(
                response.url, tmdbId, contentType, season, episode
            )
            if (directUrl != null) {
                currentServerName = response.serverId ?: ""
                reportedThisAttempt = false
                playNative(directUrl, null, response.headers)
            } else {
                tryNextServer()
            }
        }
    }
