package com.mariocart.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.ui.theme.Red
import com.mariocart.app.ui.theme.TextPrimary
import com.mariocart.app.ui.util.responsiveDims

@Composable
fun ContentRow(
    title: String,
    emoji: String,
    items: List<TmdbItem>,
    onItemClick: (TmdbItem) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dims = responsiveDims()
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.rowPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$emoji $title",
                color = TextPrimary,
                fontSize = if (dims.isTv) 22.sp else 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (onLoadMore != null) {
                TextButton(onClick = onLoadMore) {
                    Text("More", color = Red, fontSize = if (dims.isTv) 15.sp else 13.sp)
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = dims.rowPadding),
            horizontalArrangement = Arrangement.spacedBy(dims.cardSpacing)
        ) {
            items(
                count = items.size,
                // Stable key = the TMDB id + content type. Using the index in
                // the key (as before) caused the entire LazyRow to recompose
                // whenever load-more appended items, because every shifted
                // position looked like a "new" item to the lazy list.
                key = { idx ->
                    val item = items[idx]
                    "${item.id}_${item.contentType}"
                }
            ) { idx ->
                val item = items[idx]
                ContentCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    dims = dims
                )
            }
        }
    }
}
