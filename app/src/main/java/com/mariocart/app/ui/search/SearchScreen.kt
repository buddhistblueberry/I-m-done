package com.mariocart.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.ui.components.ContentCard
import com.mariocart.app.ui.theme.Bg
import com.mariocart.app.ui.theme.Red
import com.mariocart.app.ui.theme.TextMuted

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

    LaunchedEffect(initialGenre) {
        if (!initialGenre.isNullOrEmpty()) {
            viewModel.updateGenre(initialGenre)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Search", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateQuery(it) },
            placeholder = { Text("Search movies or TV shows...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Red,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Red)
                }
            }
            results.isNotEmpty() -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { item ->
                        ContentCard(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
            query.length >= 2 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results found", color = TextMuted)
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Start typing...", color = TextMuted)
                }
            }
        }
    }
}
