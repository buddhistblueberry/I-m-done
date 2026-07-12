package com.mariocart.app.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mariocart.app.data.server.StreamExtractor
import com.mariocart.app.ui.theme.MarioCartTheme
import kotlinx.coroutines.delay

@UnstableApi
class PlayerActivity : ComponentActivity() {

    companion object {
        fun newIntent(
            context: Context,
            tmdbId: Int,
            contentType: String = "movie",
            season: Int = 1,
            episode: Int = 1,
            title: String = "Now Playing"
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("TMDB_ID", tmdbId)
            putExtra("CONTENT_TYPE", contentType)
            putExtra("SEASON", season)
            putExtra("EPISODE", episode)
            putExtra("TITLE", title)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val contentType = intent.getStringExtra("CONTENT_TYPE") ?: "movie"
        val season = intent.getIntExtra("SEASON", 1)
        val episode = intent.getIntExtra("EPISODE", 1)
        val title = intent.getStringExtra("TITLE") ?: "Now Playing"

        if (tmdbId == -1) {
            Log.e("PlayerActivity", "Invalid TMDB ID")
            finish()
            return
        }

        setContent {
            MarioCartTheme {
                PlayerScreen(tmdbId, contentType, season, episode, title)
            }
        }
    }
}

@UnstableApi
@Composable
fun PlayerScreen(
    tmdbId: Int,
    contentType: String,
    season: Int,
    episode: Int,
    title: String
) {
    val localContext = LocalContext.current
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tmdbId, contentType, season, episode) {
        isLoading = true
        error = null

        Log.d("Player", "🔍 Fetching stream for TMDB $tmdbId ($contentType S$season E$episode)")

        try {
            // Try Python scraper first
            var url: String? = try {
                StreamExtractor.extract(
                    context = localContext,
                    tmdbId = tmdbId,
                    contentType = contentType,
                    season = season,
                    episode = episode
                )
            } catch (e: Exception) {
                Log.w("Player", "Python scraper unavailable: ${e.message}")
                null
            }

            // Fallback to native stream fetcher
            if (url.isNullOrBlank()) {
                Log.d("Player", "Trying native stream fetcher...")
                val fetcher = StreamFetcher(localContext)
                url = fetcher.fetchStreamUrl(tmdbId, contentType, season, episode)
            }

            if (!url.isNullOrBlank()) {
                streamUrl = url
                Log.i("Player", "✅ Stream ready: $url")
            } else {
                error = "Could not find a stream for this title. Try again later."
            }
        } catch (e: Exception) {
            Log.e("Player", "Stream fetch error: ${e.message}", e)
            error = "Error loading stream: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Finding stream...",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "⚠️ ${error}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { (localContext as? ComponentActivity)?.finish() }) {
                        Text("Back")
                    }
                }
            }

            streamUrl != null -> {
                var player: ExoPlayer? by remember { mutableStateOf(null) }

                DisposableEffect(Unit) {
                    onDispose {
                        player?.release()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val exoPlayer = try {
                            ExoPlayer.Builder(ctx).build().apply {
                                addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) {
                                        Log.d("ExoPlayer", "State: $state")
                                    }

                                    override fun onPlayerError(error: PlaybackException) {
                                        Log.e("ExoPlayer", "Playback error: ${error.errorCodeName}")
                                    }
                                })
                                setMediaItem(MediaItem.fromUri(streamUrl!!))
                                prepare()
                                playWhenReady = true
                            }
                        } catch (e: Exception) {
                            Log.e("ExoPlayer", "Failed to create player: ${e.message}")
                            null
                        }

                        player = exoPlayer

                        if (exoPlayer != null) {
                            PlayerView(ctx).apply {
                                this.player = exoPlayer
                                useController = true
                                controllerShowTimeoutMs = 3000
                            }
                        } else {
                            android.widget.FrameLayout(ctx).apply {
                                addView(android.widget.TextView(ctx).apply {
                                    text = "Failed to initialize player"
                                })
                            }
                        }
                    },
                    update = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
