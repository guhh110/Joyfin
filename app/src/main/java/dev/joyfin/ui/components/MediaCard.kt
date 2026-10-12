package dev.joyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.joyfin.data.Media
import dev.joyfin.ui.device.LocalDeviceLayout
import dev.joyfin.ui.interaction.focusTarget
import dev.joyfin.ui.model.ArtworkSource
import dev.joyfin.ui.theme.Palette

@Composable
fun MediaCard(
    item: Media,
    images: ArtworkSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    focusKey: String = "media:${item.id}",
    cardWidth: Dp? = null,
    focusModifier: Modifier? = null,
) {
    val tv = LocalDeviceLayout.current.television
    val imageUrl =
        remember(item, images, wide) { if (wide) images.backdrop(item) else images.poster(item) }
    val imageRatio = if (wide) 16f / 9f else 2f / 3f
    FocusSurface(
        onClick,
        modifier
            .width(
                cardWidth ?: if (wide) if (tv) 260.dp else 232.dp else if (tv) 156.dp else 132.dp
            )
            .then(focusModifier ?: Modifier.focusTarget(focusKey)),
        transparent = true,
    ) {
        Column(Modifier.padding(3.dp)) {
            Box {
                if (item.type == "Episode")
                    EpisodeArtwork(item, images, Modifier.fillMaxWidth(), imageRatio)
                else
                    Artwork(
                        imageUrl,
                        images,
                        Modifier.fillMaxWidth()
                            .aspectRatio(imageRatio)
                            .clip(RoundedCornerShape(7.dp)),
                        item.name,
                    )
                if (item.resumeTicks > 0 && item.ticks > 0)
                    Box(
                        Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                            .fillMaxWidth(
                                (item.resumeTicks.toFloat() / item.ticks).coerceIn(0f, 1f)
                            )
                            .height(2.dp)
                            .background(Palette.Text)
                    )
            }
            Text(
                if (wide && item.seriesName.isNotBlank()) item.seriesName else item.name,
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Palette.Text,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item.type == "Episode") "S${item.season} · E${item.episode} · ${item.name}"
                else
                    listOf(item.year.takeIf { it > 0 }?.toString(), item.genres.firstOrNull())
                        .filterNotNull()
                        .joinToString(" · "),
                color = Palette.Muted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
