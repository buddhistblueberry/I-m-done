package com.mariocart.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.ui.browse.BrowseScreen
import com.mariocart.app.ui.home.HomeScreen
import com.mariocart.app.ui.movies.MoviesScreen
import com.mariocart.app.ui.player.PlayerActivity
import com.mariocart.app.ui.search.SearchScreen
import com.mariocart.app.ui.theme.Bg
import com.mariocart.app.ui.theme.Bg2
import com.mariocart.app.ui.theme.Bg3
import com.mariocart.app.ui.theme.MarioCartTheme
import com.mariocart.app.ui.theme.Red
import com.mariocart.app.ui.theme.TextMuted
import com.mariocart.app.ui.theme.TextPrimary
import com.mariocart.app.ui.tv.SeasonEpisodePicker
import com.mariocart.app.ui.tv.TvScreen
import com.mariocart.app.ui.updates.UpdatesScreen
import com.mariocart.app.ui.util.responsiveDims

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Movies("Movies", Icons.Default.Movie),
    TV("TV", Icons.Default.Tv),
    Browse("Browse", Icons.Default.GridView),
    Updates("Updates", Icons.Default.Upgrade)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Expose the application context to ViewModels for the stream-
        // availability probe (so the browse grid can filter to only-streamable
        // titles without an Activity-scoped reference).
        com.mariocart.app.ui.browse.AppContextHolder.context = applicationContext

        setContent {
            MarioCartTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dims = responsiveDims()
    var currentTab by remember { mutableStateOf(Tab.Home) }
    var showSearch by remember { mutableStateOf(false) }
    var selectedTv by remember { mutableStateOf<TmdbItem?>(null) }
    var searchGenre by remember { mutableStateOf<String?>(null) }


    // Check for an update on launch (silent unless one is available)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.mariocart.app.ui.AutoUpdater.checkAndPrompt(context)
    }
    // ── Navigation helpers ────────────────────────────────────────── //
    fun launchMovie(item: TmdbItem) {
        val intent = PlayerActivity.newIntent(
            context = context,
            tmdbId = item.id,
            contentType = "movie",
            title = item.displayTitle,
            year = item.year,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl
        )
        context.startActivity(intent)
    }

    fun launchTv(item: TmdbItem, season: Int, episode: Int) {
        val intent = PlayerActivity.newIntent(
            context = context,
            tmdbId = item.id,
            contentType = "tv",
            season = season,
            episode = episode,
            title = item.displayTitle,
            year = item.year,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl
        )
        context.startActivity(intent)
    }

    val onItemClick: (TmdbItem) -> Unit = { item ->
        if (item.isMovie) {
            launchMovie(item)
        } else {
            // TV show → open the season/episode picker first.
            selectedTv = item
        }
    }

    // ── Search overlay takes priority ─────────────────────────────── //
    if (showSearch) {
        SearchScreen(
            onItemClick = onItemClick,
            onClose = {
                showSearch = false
                searchGenre = null
            },
            initialGenre = searchGenre
        )
        return
    }

    // ── Season / episode picker overlay for TV shows ──────────────── //
    selectedTv?.let { tv ->
        SeasonEpisodePicker(
            item = tv,
            onPlay = { season, episode -> launchTv(tv, season, episode) },
            onBack = { selectedTv = null }
        )
        return
    }

    // ── Layout: TV uses a side navigation rail; phones use bottom nav ──
    if (dims.isTv) {
        Row(modifier = Modifier.fillMaxSize().background(Bg)) {
            TvSideNav(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                onSearchClick = { showSearch = true },
                dims = dims
            )
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    Tab.Home -> HomeScreen(
                        onItemClick = onItemClick,
                        onSearchWithGenre = { genreId ->
                            searchGenre = genreId
                            showSearch = true
                        }
                    )
                    Tab.Movies -> MoviesScreen(onItemClick = onItemClick)
                    Tab.TV -> TvScreen(onItemClick = onItemClick)
                    Tab.Browse -> BrowseScreen(onItemClick = onItemClick)
                    Tab.Updates -> UpdatesScreen()
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    currentTab = currentTab,
                    onSearchClick = { showSearch = true }
                )
            },
            bottomBar = {
                BottomNav(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            },
            containerColor = Bg
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentTab) {
                    Tab.Home -> HomeScreen(
                        onItemClick = onItemClick,
                        onSearchWithGenre = { genreId ->
                            searchGenre = genreId
                            showSearch = true
                        }
                    )
                    Tab.Movies -> MoviesScreen(onItemClick = onItemClick)
                    Tab.TV -> TvScreen(onItemClick = onItemClick)
                    Tab.Browse -> BrowseScreen(onItemClick = onItemClick)
                    Tab.Updates -> UpdatesScreen()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────── //
//  Top app bar (phone) — title + search icon                         //
// ─────────────────────────────────────────────────────────────────── //
@Composable
private fun TopAppBar(currentTab: Tab, onSearchClick: () -> Unit) {
    Surface(color = Bg2, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mario Cart",
                color = Red,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────── //
//  Bottom navigation bar (phone)                                     //
// ─────────────────────────────────────────────────────────────────── //
@Composable
private fun BottomNav(currentTab: Tab, onTabSelected: (Tab) -> Unit) {
    Surface(color = Bg2, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.entries.forEach { tab ->
                NavItem(
                    tab = tab,
                    isSelected = tab == currentTab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, isSelected: Boolean, onClick: () -> Unit) {
    val color = if (isSelected) Red else TextMuted
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(tab.label, color = color, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ─────────────────────────────────────────────────────────────────── //
//  TV side navigation rail (Android TV)                              //
//  A vertical icon+label rail on the left edge, D-pad focusable with  //
//  a red highlight on the focused item. The search icon sits at top.  //
// ─────────────────────────────────────────────────────────────────── //
@Composable
private fun TvSideNav(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    onSearchClick: () -> Unit,
    dims: com.mariocart.app.ui.util.ResponsiveDims
) {
    Surface(
        color = Bg2,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxHeight()
            .width(120.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App title
            Text(
                text = "Mario\nCart",
                color = Red,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Search button
            TvNavItem(
                icon = Icons.Default.Search,
                label = "Search",
                isSelected = false,
                onClick = onSearchClick
            )
            Tab.entries.forEach { tab ->
                TvNavItem(
                    icon = tab.icon,
                    label = tab.label,
                    isSelected = tab == currentTab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun TvNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val color = if (isSelected) Red else TextPrimary
    val highlight = isFocused || isSelected

    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) Red.copy(alpha = 0.15f) else Color.Transparent)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Red, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = color,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
