package com.mariocart.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mariocart.app.ui.home.HomeScreen
import com.mariocart.app.ui.player.PlayerActivity
import com.mariocart.app.ui.theme.MarioCartTheme

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
                        onItemClick = { item ->
                            // Safe handling - adjust property names if your TmdbItem has different fields
                            val tmdbId = item.id // most common field name
                            val contentType = if (item.type == "tv" || item.mediaType == "tv") "tv" else "movie"

                            val intent = PlayerActivity.newIntent(
                                context = this,
                                tmdbId = tmdbId,
                                contentType = contentType
                                // season/episode default to 1 for movies or first episode
                            )
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
