package com.mariocart.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.ui.components.ContentCard
import com.mariocart.app.ui.theme.Bg
import com.mariocart.app.ui.theme.Bg3
import com.mariocart.app.ui.theme.Red
import com.mariocart.app.ui.theme.TextMuted

private data class CategoryChip(val emoji: String, val label: String, val genreId: String)

private val CATEGORY_CHIPS = listOf(
    CategoryChip("\u2728",          "All",         ""),
    CategoryChip("\uD83D\uDD25",    "Action",      "28"),
    CategoryChip("\uD83D\uDE02",    "Comedy",      "35"),
    CategoryChip("\uD83D\uDC7B",    "Horror",      "27"),
    CategoryChip("\uD83D\uDE80",    "Sci-Fi",      "878"),
    CategoryChip("\uD83C\uDFAD",    "Drama",       "18"),
    CategoryChip("\uD83D\uDD2A",    "Thriller",    "53"),
    CategoryChip("\uD83C\uDF00",    "Animation",   "16"),
    CategoryChip("\uD83D\uDC95",    "Romance",     "10749"),
    CategoryChip("\uD83D\uDD75\uFE0F","Crime",     "80"),
    CategoryChip("\uD83C\uDF0D",    "Adventure",   "12"),
    CategoryChip("\uD83D\uDCFA",    "TV Action",   "10759"),
    CategoryChip("\uD83D\uDCF9",    "Documentary", "99"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onItemClick: (TmdbItem) -> Unit,
    onClose: () -> Unit,
    initialGenre: String? = null,
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val yearFilter by viewModel.yearFilter.collectAsState()
    val genre by viewModel.genre.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    LaunchedEffect(initialGenre) {
        if (initialGenre != null) viewModel.updateGenre(initialGenre)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.copy(alpha = 0.97f))
            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateQuery(it) },
            placeholder = { Text("Search movies, shows\u2026", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Red,
                focusedBorderColor = Red,
                unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Bg3,
                unfocusedContainerColor = Bg3,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        )

        // Category chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CATEGORY_CHIPS.forEach { chip ->
                val selected = genre == chip.genreId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Red else Bg3)
                        .clickable { viewModel.updateGenre(chip.genreId) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${chip.emoji} ${chip.label}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Filters row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            FilterDropdown(
                label = when (searchType) {
                    "movie" -> "Movies"
                    "tv" -> "TV Only"
                    else -> "All Types"
                },
                options = listOf("All Types" to "multi", "Movies" to "movie", "TV Only" to "tv"),
                onSelect = { viewModel.updateType(it) }
            )
            FilterDropdown(
                label = when (sortBy) {
                    "vote_average.desc"         -> "Top Rated"
                    "primary_release_date.desc" -> "Newest"
                    else                        -> "Popular"
                },
                options = listOf(
                    "Popular"   to "popularity.desc",
                    "Top Rated" to "vote_average.desc",
                    "Newest"    to "primary_release_date.desc"
                ),
                onSelect = { viewModel.updateSortBy(it) }
            )
            FilterDropdown(
                label = when (yearFilter) {
                    "2024" -> "2024+"
                    "2023" -> "2023+"
                    "2020" -> "2020+"
                    else -> "Any Year"
                },
                options = listOf(
                    "Any Year" to "",
                    "2024+"    to "2024",
                    "2023+"    to "2023",
                    "2020+"    to "2020"
                ),
                onSelect = { viewModel.updateYear(it.ifEmpty { null }) }
            )
        }

        // Body
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Red, modifier = Modifier.size(36.dp))
                }
            }
            results.isEmpty() && query.isBlank() && genre.isEmpty() -> {
                // Suggestions state
                Column {
                    Text(
                        text = "\uD83D\uDCA1 Suggested for You",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    ResultsGrid(items = suggestions, onItemClick = onItemClick)
                }
            }
            results.isEmpty() && (query.isNotBlank() || genre.isNotEmpty()) -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDD0D", fontSize = 48.sp)
                        Text(
                            "No results found",
                            color = TextMuted,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            else -> {
                val label = when {
                    query.isNotBlank() -> "Results for \"$query\""
                    genre.isNotEmpty() -> {
                        val name = CATEGORY_CHIPS.find { it.genreId == genre }?.label ?: "Genre"
                        "\uD83C\uDFAC $name"
                    }
                    else -> "Results"
                }
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ResultsGrid(items = results, onItemClick = onItemClick)
            }
        }
    }
}

@Composable
private fun ResultsGrid(items: List<TmdbItem>, onItemClick: (TmdbItem) -> Unit) {
    val chunked = items.chunked(3)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chunked) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    ContentCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF444444),
                unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Bg3,
                unfocusedContainerColor = Bg3,
            ),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .size(width = 110.dp, height = 48.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Bg3)
        ) {
            options.forEach { (name, value) ->
                DropdownMenuItem(
                    text = { Text(name, color = Color.White, fontSize = 13.sp) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}
