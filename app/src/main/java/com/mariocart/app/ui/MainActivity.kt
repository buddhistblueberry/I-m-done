package com.mariocart.app.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mariocart.app.data.server.StreamExtractor
import com.mariocart.app.ui.home.HomeScreen
import com.mariocart.app.ui.player.PlayerActivity
import com.mariocart.app.ui.theme.MarioCartTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MarioCartTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        onMovieClick = { tmdbId ->
                            startActivity(
                                PlayerActivity.newIntent(
                                    context = this,
                                    tmdbId = tmdbId,
                                    contentType = "movie"
                                )
                            )
                        },
                        onTvClick = { tmdbId, season, episode ->
                            startActivity(
                                PlayerActivity.newIntent(
                                    context = this,
                                    tmdbId = tmdbId,
                                    contentType = "tv",
                                    season = season,
                                    episode = episode
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    MarioCartTheme {
        HomeScreen(
            onMovieClick = {},
            onTvClick = { _, _, _ -> }
        )
    }
}
