package com.mariocart.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.mariocart.app.data.api.ApiClient
import com.mariocart.app.data.api.CaptchaSubmitRequest
import com.mariocart.app.data.api.ReportPayload
import com.mariocart.app.data.api.StreamResponse
import com.mariocart.app.ui.captcha.CaptchaActivity
import kotlinx.coroutines.launch

/**
 * 100% backend-driven, 100% native player.
 *
 *  - The app NEVER scrapes streams locally.
 *  - The app NEVER falls back to a WebView for playback.
 *  - If the backend reports `needsCaptcha`, we open the small CaptchaActivity
 *    (a one-shot WebView), capture the `_rcp` token and POST it back so the
 *    backend finishes extraction.
 *  - Season/episode are user-selectable for TV content.
 *  - Supports HLS (.m3u8), DASH (.mpd), MP4, MKV, WebM via ExoPlayer.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PlayerActivity"

        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_TYPE    = "type"
        private const val EXTRA_TITLE   = "title"
        private const val EXTRA_SEASON  = "season"
        private const val EXTRA_EPISODE = "episode"

        fun newIntent(
            context: Context,
            tmdbId: Int,
            type: String,            // "movie" or "tv"
            title: String,
            season: Int = 1,
            episode: Int = 1,
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_TMDB_ID, tmdbId)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SEASON, season)
            putExtra(EXTRA_EPISODE, episode)
        }
    }

    private var tmdbId = 0
    private var contentType = "movie"
    private var season = 1
    private var episode = 1
    private var videoTitle = ""

    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingText: TextView
    private lateinit var episodeButton: TextView
    private lateinit var retryButton: TextView
    private var exo: ExoPlayer? = null

    private var pendingCaptchaSessionId: String? = null

    // ---- ActivityResult bridge to CaptchaActivity ----
    private lateinit var captchaLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        tmdbId      = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        contentType = intent.getStringExtra(EXTRA_TYPE) ?: "movie"
        videoTitle  = intent.getStringExtra(EXTRA_TITLE) ?: ""
        season      = intent.getIntExtra(EXTRA_SEASON, 1)
        episode     = intent.getIntExtra(EXTRA_EPISODE, 1)

        captchaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val token = result.data?.getStringExtra(CaptchaActivity.EXTRA_TOKEN)
                val session = pendingCaptchaSessionId
                if (!token.isNullOrBlank() && !session.isNullOrBlank()) {
                    submitCaptcha(session, token)
                } else {
                    showError("CAPTCHA token missing. Please try again.")
                }
            } else {
                showError("Verification cancelled. Tap retry to try again.")
            }
        }

        setupLayout()
        resolveAndPlay()
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------
    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    private fun setupLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
            useController = true
        }

        loadingText = TextView(this).apply {
            text = "Resolving stream from backend…"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 18f
        }

        retryButton = TextView(this).apply {
            text = "Retry"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            setPadding(40, 18, 40, 18)
            textSize = 14f
            visibility = View.GONE
            setOnClickListener { resolveAndPlay() }
        }

        val overlayContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                loadingText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                retryButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 32 },
            )
        }

        loadingOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                overlayContent,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.CENTER },
            )
        }

        episodeButton = TextView(this).apply {
            text = if (contentType == "tv") "S${season}·E${episode}" else "Server"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(40, 20, 40, 20)
            textSize = 14f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 60
                rightMargin = 60
            }
            setOnClickListener {
                if (contentType == "tv") showEpisodePicker() else resolveAndPlay()
            }
        }

        root.addView(playerView)
        root.addView(loadingOverlay)
        root.addView(episodeButton)
        setContentView(root)
    }

    // -------------------------------------------------------------------------
    // Season / Episode picker (TV only)
    // -------------------------------------------------------------------------
    private fun showEpisodePicker() {
        val maxSeasons = 30
        val maxEpisodes = 30
        val seasons = (1..maxSeasons).map { "Season $it" }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select season")
            .setSingleChoiceItems(seasons, season - 1) { dialog, which ->
                dialog.dismiss()
                val newSeason = which + 1
                val episodes = (1..maxEpisodes).map { "Episode $it" }.toTypedArray()
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Season $newSeason — Select episode")
                    .setSingleChoiceItems(episodes, episode - 1) { d2, w2 ->
                        d2.dismiss()
                        season = newSeason
                        episode = w2 + 1
                        episodeButton.text = "S${season}·E${episode}"
                        resolveAndPlay()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // Backend resolve
    // -------------------------------------------------------------------------
    private fun resolveAndPlay() {
        showLoading("Resolving stream from backend…")
        lifecycleScope.launch {
            try {
                val response = ApiClient.streamingBackendApi.getStream(
                    tmdbId = tmdbId,
                    type = contentType,
                    season = if (contentType == "tv") season else null,
                    episode = if (contentType == "tv") episode else null,
                    serverId = null,
                )
                handleStreamResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "resolve error", e)
                showError("Cannot reach backend: ${e.message}")
            }
        }
    }

    private fun handleStreamResponse(response: StreamResponse) {
        if (response.success && response.url != null) {
            playNative(response)
            return
        }
        if (response.needsCaptcha == true && response.captcha != null) {
            val challenge = response.captcha
            pendingCaptchaSessionId = challenge.sessionId
            showLoading("CAPTCHA required — opening verification…")
            captchaLauncher.launch(
                CaptchaActivity.newIntent(
                    this,
                    challengeUrl = challenge.challengeUrl,
                    tokenParam = challenge.tokenParam,
                ),
            )
            return
        }
        showError(response.error ?: "No working stream returned by backend.")
    }

    private fun submitCaptcha(sessionId: String, token: String) {
        showLoading("Verifying & unlocking stream…")
        lifecycleScope.launch {
            try {
                val response = ApiClient.streamingBackendApi.submitCaptcha(
                    CaptchaSubmitRequest(sessionId = sessionId, rcpToken = token),
                )
                handleStreamResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "captcha submit error", e)
                showError("Failed to verify CAPTCHA: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Native playback (ExoPlayer)
    // -------------------------------------------------------------------------
    private fun playNative(resp: StreamResponse) {
        runOnUiThread {
            loadingOverlay.visibility = View.GONE
            playerView.visibility = View.VISIBLE

            val mimeType = when (resp.streamType?.lowercase()) {
                "hls"  -> MimeTypes.APPLICATION_M3U8
                "dash" -> MimeTypes.APPLICATION_MPD
                "mp4"  -> MimeTypes.VIDEO_MP4
                "mkv"  -> MimeTypes.VIDEO_MATROSKA
                "webm" -> MimeTypes.VIDEO_WEBM
                else   -> resp.contentType ?: MimeTypes.VIDEO_MP4
            }

            val httpFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
                .setUserAgent(resp.headers?.get("User-Agent") ?: defaultUA())
                .setDefaultRequestProperties(resp.headers ?: emptyMap())
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)

            val item = MediaItem.Builder()
                .setUri(Uri.parse(resp.url))
                .setMimeType(mimeType)
                .build()

            exo?.release()
            exo = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .build()
                .apply {
                    setMediaItem(item)
                    prepare()
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e(TAG, "playback error", error)
                            reportPlayback(resp.serverId, false)
                            showError("Playback failed. Tap retry or change episode.")
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) reportPlayback(resp.serverId, true)
                        }
                    })
                }
            playerView.player = exo

            // Sideload subtitles
            resp.subtitles?.forEach { sub ->
                try {
                    val subItem = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(sub.language ?: C.LANGUAGE_UNDETERMINED)
                        .setLabel(sub.label ?: "Subtitle")
                        .build()
                    // ExoPlayer 1.x: rebuild MediaItem with subtitle attached
                    exo?.setMediaItem(
                        item.buildUpon().setSubtitleConfigurations(listOf(subItem)).build(),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "subtitle attach failed: ${e.message}")
                }
            }
        }
    }

    private fun reportPlayback(serverId: String?, success: Boolean) {
        if (serverId.isNullOrBlank()) return
        lifecycleScope.launch {
            runCatching {
                ApiClient.streamingBackendApi.report(
                    ReportPayload(
                        server_id = serverId,
                        success = success,
                        tmdb_id = tmdbId,
                        content_type = contentType,
                    ),
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI state helpers
    // -------------------------------------------------------------------------
    private fun showLoading(text: String) {
        runOnUiThread {
            loadingText.text = text
            retryButton.visibility = View.GONE
            loadingOverlay.visibility = View.VISIBLE
            playerView.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            loadingText.text = message
            retryButton.visibility = View.VISIBLE
            loadingOverlay.visibility = View.VISIBLE
            playerView.visibility = View.GONE
        }
    }

    private fun defaultUA(): String =
        "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    override fun onDestroy() {
        super.onDestroy()
        exo?.release()
        exo = null
    }
}
