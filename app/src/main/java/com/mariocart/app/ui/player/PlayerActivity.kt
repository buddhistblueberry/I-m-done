package com.mariocart.app.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.mariocart.app.data.server.StreamExtractor
import com.mariocart.app.ui.theme.MarioCartTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        // Guard so we only escalate to the user N times per playback session.
        const val MAX_VERIFICATION_ROUNDS = 2

        fun newIntent(
            context: Context,
            tmdbId: Int,
            contentType: String = "movie",
            season: Int = 1,
            episode: Int = 1,
            title: String = "Now Playing",
            year: String? = null
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("TMDB_ID", tmdbId)
            putExtra("CONTENT_TYPE", contentType)
            putExtra("SEASON", season)
            putExtra("EPISODE", episode)
            putExtra("TITLE", title)
            putExtra("YEAR", year)
        }
    }

    /**
     * Outcome of a human-verification round-trip.
     *
     * [signal] is a monotonically increasing counter so that the Compose tree
     * can reliably detect each new result even if two successive verifications
     * return identical cookies (MutableStateFlow conflates equal values, so
     * we can't rely on the cookie string alone to trigger recomposition).
     */
    data class VerificationOutcome(
        val success: Boolean,
        val signal: Int
    )

    /** Updated by the ActivityResult callback; observed by the Compose tree. */
    private val verificationOutcome = MutableStateFlow(VerificationOutcome(false, 0))

    private lateinit var verificationLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register the launcher for the VerificationActivity round-trip.
        // When the user solves the challenge, cookies come back in the result
        // intent and we feed them into the extraction retry via
        // [verificationOutcome].
        verificationLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val prev = verificationOutcome.value
                if (result.resultCode == RESULT_OK) {
                    val cookies = result.data?.getStringExtra(VerificationActivity.EXTRA_COOKIES)
                    val finalUrl = result.data?.getStringExtra(VerificationActivity.EXTRA_FINAL_URL)
                    Log.i(TAG, "✅ Verification solved. cookies=${cookies?.length ?: 0} chars; finalUrl=$finalUrl")
                    if (!cookies.isNullOrBlank()) {
                        // Push cookies into OkHttp's jar for the retry.
                        StreamExtractor.injectCookies(cookies)
                    }
                    verificationOutcome.value = VerificationOutcome(success = true, signal = prev.signal + 1)
                } else {
                    Log.w(TAG, "Verification cancelled by user.")
                    verificationOutcome.value = VerificationOutcome(success = false, signal = prev.signal + 1)
                }
            }

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val contentType = intent.getStringExtra("CONTENT_TYPE") ?: "movie"
        val season = intent.getIntExtra("SEASON", 1)
        val episode = intent.getIntExtra("EPISODE", 1)
        val title = intent.getStringExtra("TITLE") ?: "Now Playing"
        val year = intent.getStringExtra("YEAR")

        if (tmdbId == -1) {
            Log.e(TAG, "Invalid TMDB ID")
            finish()
            return
        }

        setContent {
            MarioCartTheme {
                PlayerScreen(
                    tmdbId = tmdbId,
                    contentType = contentType,
                    season = season,
                    episode = episode,
                    title = title,
                    year = year,
                    verificationOutcome = verificationOutcome.asStateFlow().collectAsState().value,
                    onLaunchVerification = { challengeUrl, referer ->
                        launchVerificationActivity(challengeUrl, referer)
                    }
                )
            }
        }
    }

    private fun launchVerificationActivity(challengeUrl: String, referer: String) {
        Log.i(TAG, "🤖 Launching human verification for $challengeUrl (referer=$referer)")
        verificationLauncher.launch(
            VerificationActivity.newIntent(this, challengeUrl, referer)
        )
    }
}

/**
 * The player screen.
 *
 * Extraction flow:
 *  1. Call [StreamExtractor.extract], which returns a [StreamExtractor.Result].
 *  2. If [StreamExtractor.Result.Stream] → play with ExoPlayer, passing the
 *     headers map (which includes `Cookie: t_hash=<hash>`, `Referer`,
 *     `User-Agent`) so HLS segments load directly — **no WebView for playback**.
 *  3. If [StreamExtractor.Result.Challenge] → launch [VerificationActivity]
 *     via [onLaunchVerification]. When the user solves it, [verificationOutcome]
 *     changes and we retry extraction (cookies already injected into OkHttp).
 *  4. If [StreamExtractor.Result.Error] → show the message; allow manual retry.
 */
@UnstableApi
@Composable
fun PlayerScreen(
    tmdbId: Int,
    contentType: String,
    season: Int,
    episode: Int,
    title: String = "Now Playing",
    year: String? = null,
    verificationOutcome: PlayerActivity.VerificationOutcome,
    onLaunchVerification: (challengeUrl: String, referer: String) -> Unit
) {
    val localContext = LocalContext.current

    // --- Player + extraction state ---------------------------------- //
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var streamHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // --- Loop control ------------------------------------------------ //
    // attempt is the LaunchedEffect key; bumping it re-runs extraction.
    var attempt by remember { mutableStateOf(0) }
    // How many times we've escalated to the user for human verification.
    var verificationRounds by remember { mutableStateOf(0) }
    // Track the pending challenge so we only launch the activity once.
    var pendingChallengeUrl by remember { mutableStateOf<String?>(null) }
    var pendingReferer by remember { mutableStateOf("https://www.lookmovie2.to/") }

    // --------------------------------------------------------------- //
    //  Extraction LaunchedEffect                                       //
    // --------------------------------------------------------------- //
    LaunchedEffect(tmdbId, contentType, season, episode, attempt) {
        isLoading = true
        error = null
        infoMessage = null

        Log.d("Player", "🔎 Extraction attempt #$attempt for \"$title\" ($year) $contentType S$season E$episode")

        try {
            val result = StreamExtractor.extract(title, year, contentType, season, episode)

            when (result) {
                is StreamExtractor.Result.Stream -> {
                    Log.i("Player", "✅ Direct playable URL: ${result.url}")
                    Log.d("Player", "   headers=${result.headers}")
                    streamUrl = result.url
                    streamHeaders = result.headers
                    pendingChallengeUrl = null
                }

                is StreamExtractor.Result.Challenge -> {
                    Log.w("Player", "🤖 Challenge detected at ${result.challengeUrl}")
                    // Only escalate to the user up to N times to avoid loops.
                    if (verificationRounds < PlayerActivity.MAX_VERIFICATION_ROUNDS) {
                        verificationRounds++
                        pendingChallengeUrl = result.challengeUrl
                        pendingReferer = result.referer
                        infoMessage =
                            "Human verification required. Please complete the challenge in " +
                            "the browser that just opened, then tap Done."
                    } else {
                        error = "Still blocked after verification. Please try again later."
                    }
                }

                is StreamExtractor.Result.Error -> {
                    Log.e("Player", "❌ ${result.message}")
                    error = result.message
                }
            }
        } catch (e: Exception) {
            Log.e("Player", "💥 Extraction failed", e)
            error = e.message ?: "Failed to load stream."
        } finally {
            isLoading = false
        }
    }

    // --------------------------------------------------------------- //
    //  Launch VerificationActivity when a challenge is pending         //
    // --------------------------------------------------------------- //
    LaunchedEffect(pendingChallengeUrl) {
        val url = pendingChallengeUrl ?: return@LaunchedEffect
        // Consume immediately so we don't re-launch on recomposition.
        pendingChallengeUrl = null
        onLaunchVerification(url, pendingReferer)
    }

    // --------------------------------------------------------------- //
    //  React to a completed verification round-trip                   //
    //  Keyed on the signal counter so it fires on every result.        //
    // --------------------------------------------------------------- //
    LaunchedEffect(verificationOutcome.signal) {
        // signal == 0 is the initial state; nothing to do.
        if (verificationOutcome.signal == 0) return@LaunchedEffect

        if (verificationOutcome.success) {
            // Cookies were already injected into OkHttp by the activity.
            // Bump attempt to re-run extraction with the fresh cookies.
            Log.i("Player", "🔄 Retrying extraction after verification…")
            infoMessage = "Verification complete — finding your stream…"
            isLoading = true
            error = null
            attempt++
        } else {
            error = "Verification was cancelled."
        }
    }

    // --------------------------------------------------------------- //
    //  UI                                                              //
    // --------------------------------------------------------------- //
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        infoMessage ?: "Finding best stream…",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("⚠️ $error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        // Reset verification rounds on a manual retry so the
                        // user can re-attempt if the site is challenging again.
                        verificationRounds = 0
                        error = null
                        attempt++
                    }) { Text("Retry") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        (localContext as? ComponentActivity)?.finish()
                    }) { Text("Back") }
                }
            }

            infoMessage != null && streamUrl == null -> {
                // Waiting for the user to finish verification in the WebView.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        infoMessage!!,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        // If the auto-detect in VerificationActivity didn't
                        // work, the user can retry from here too.
                        attempt++
                        infoMessage = null
                    }) { Text("Retry extraction") }
                }
            }

            streamUrl != null -> {
                ExoPlayerView(streamUrl!!, streamHeaders)
            }
        }
    }
}

// ---------------------------------------------------------------- //
//  ExoPlayer composable                                            //
// ---------------------------------------------------------------- //

@UnstableApi
@Composable
private fun ExoPlayerView(url: String, headers: Map<String, String>) {
    var player: ExoPlayer? by remember { mutableStateOf(null) }

    AndroidView(
        factory = { ctx ->
            // LookMovie HLS manifests require Referer + User-Agent, and the
            // segments require the `t_hash` cookie (matching the Kodi addon's
            // serverHTTP.py proxy). The headers map from StreamExtractor
            // already contains all of these.
            //
            // IMPORTANT: DefaultHttpDataSource sets its own User-Agent via
            // setRequestProperty() inside open(), which takes priority over
            // setDefaultRequestProperties(). So we MUST call setUserAgent()
            // explicitly, and pass only the remaining headers (Referer, Cookie)
            // via setDefaultRequestProperties().
            val userAgent = headers["User-Agent"]
                ?: "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
            val remainingHeaders = headers.filterKeys { it != "User-Agent" }

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(remainingHeaders)

            val dataSourceFactory: DataSource.Factory = httpFactory

            val exoPlayer = ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSourceFactory)
                )
                .build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            Log.d("ExoPlayer", "State: $state")
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("ExoPlayer", "Error: ${error.errorCodeName} - ${error.message}")
                        }
                    })
                    setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                    prepare()
                    playWhenReady = true
                }
            player = exoPlayer

            PlayerView(ctx).apply {
                this.player = exoPlayer
                useController = true
                controllerShowTimeoutMs = 3000
            }
        },
        update = {},
        onRelease = { player?.release() },
        modifier = Modifier.fillMaxSize()
    )
}
