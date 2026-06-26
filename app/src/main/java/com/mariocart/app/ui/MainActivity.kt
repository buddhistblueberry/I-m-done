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
                            // item is TmdbItem - adjust based on your TmdbItem structure
                            val intent = PlayerActivity.newIntent(
                                context = this,
                                tmdbId = item.id,           // assuming TmdbItem has .id
                                contentType = if (item.isTvShow) "tv" else "movie", // adjust as needed
                                season = 1,
                                episode = 1
                            )
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
