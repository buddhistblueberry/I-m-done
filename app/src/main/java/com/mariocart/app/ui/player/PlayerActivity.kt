package com.mariocart.app.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mariocart.app.data.server.StreamExtractor
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SEASON = "season"
        private const val EXTRA_EPISODE = "episode"

        fun newIntent(
            context: Context,
            tmdbId: Int,
            type: String,
            title: String,
            season: Int = 1,
            episode: Int = 1
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
    private var title = ""

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null
    private lateinit var loadingText: TextView
    private lateinit var loadingOverlay: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        contentType = intent.getStringExtra(EXTRA_TYPE) ?: "movie"
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Movie"
        season = intent.getIntExtra(EXTRA_SEASON, 1)
        episode = intent.getIntExtra(EXTRA_EPISODE, 1)

        setupUI()
        loadLookMovieOnly()
    }

    private fun setupUI() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = true
        }

        loadingText = TextView(this).apply {
            text = "Loading from LookMovie..."
            setTextColor(Color.WHITE)
            textSize = 18f
        }

        loadingOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(loadingText)
        }

        root.addView(playerView)
        root.addView(loadingOverlay)
        setContentView(root)
    }

    private fun loadLookMovieOnly() {
        lifecycleScope.launch {
            loadingText.text = "Fetching from LookMovie2.to..."
            val streamUrl = StreamExtractor.extract(tmdbId, contentType, season, episode)

            if (streamUrl != null) {
                if (streamUrl.contains("challenge") || streamUrl.contains("verify")) {
                    loadingText.text = "Verification needed on LookMovie. Try again later."
                } else {
                    playStream(streamUrl)
                }
            } else {
                loadingText.text = "Failed to load from LookMovie. Check connection."
            }
        }
    }

    private fun playStream(url: String) {
        runOnUiThread {
            loadingOverlay.visibility = View.GONE
            playerView.visibility = View.VISIBLE

            exoPlayer = ExoPlayer.Builder(this).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
            playerView.player = exoPlayer
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
