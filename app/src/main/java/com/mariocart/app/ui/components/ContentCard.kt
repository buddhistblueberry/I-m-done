package com.mariocart.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.mariocart.app.data.model.TmdbItem
import com.mariocart.app.ui.theme.Bg3
import com.mariocart.app.ui.theme.Gold
import com.mariocart.app.ui.theme.Red
import com.mariocart.app.ui.theme.TextMuted
import com.mariocart.app.ui.util.ResponsiveDims
import com.mariocart.app.ui.util.responsiveDims

@Composable
fun ContentCard(
    item: TmdbItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = responsiveDims()
    ContentCard(item = item, onClick = onClick, modifier = modifier, dims = dims)
}

/**
 * Overload that accepts explicit [ResponsiveDims] so callers that already
 * have the dims (e.g. a grid that computed them once) can avoid re-querying
 * the device on every card.
 */
@Composable
fun ContentCard(
    item: TmdbItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dims: ResponsiveDims
) {
    val context = LocalContext.current
    // Focus state for TV D-pad navigation — when focused, draw a red border
    // so the user can see which card is selected.
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .width(dims.cardWidth)
            .clip(RoundedCornerShape(6.dp))
            .background(Bg3)
            // focusable + clickable: on TV the D-pad moves focus between
            // cards; the focus border shows which one is active. On phones
            // the focus indicator is invisible (focus never lands unless
            // tapped) so this doesn't change the touch experience.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (isFocused) {
                    Modifier.border(3.dp, Red, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.posterUrl)
                    // Crossfade for smoother image loading (reduces flicker
                    // when scrolling fast through a LazyRow).
                    .crossfade(true)
                    .build(),
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(dims.cardWidth)
                    .height(dims.cardImageHeight)
                    .background(Bg3)
            )

            // TV badge
            if (!item.isMovie) {
                Text(
                    text = "TV",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Red, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Rating badge
            if (item.ratingText.isNotEmpty()) {
                Text(
                    text = "\u2B50 ${item.ratingText}",
                    color = Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = item.displayTitle,
                color = Color(0xFFDDDDDD),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.year,
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
