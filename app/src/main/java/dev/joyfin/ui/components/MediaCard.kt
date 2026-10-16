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
    showSubtitle: Boolean = true,
    preferSeriesTitle: Boolean = true,
) {
    val tv = LocalDeviceLayout.current.television
    val imageUrl =
        remember(item, images, wide) { if (wide) images.backdrop(item) else images.poster(item) }
    val imageRatio = MediaCardLayout.ratio(wide)
    FocusSurface(
        onClick,
        modifier
            .padding(vertical = dev.joyfin.ui.theme.MotionTokens.MediaCardVerticalReserve)
            .width(
                cardWidth ?: MediaCardLayout.width(tv, wide)
            )
            .then(focusModifier ?: Modifier.focusTarget(focusKey)),
        transparent = true,
        focusedScale = if (tv) dev.joyfin.ui.theme.MotionTokens.CardFocusedScale else dev.joyfin.ui.theme.MotionTokens.FixedScale,
    ) {
        Column(Modifier.padding(MediaCardLayout.ContentInset)) {
            Box(
                Modifier.clip(RoundedCornerShape(MediaCardLayout.Corner))
            ) {
                if (item.type == "Episode")
                    EpisodeArtwork(item, images, Modifier.fillMaxWidth(), imageRatio)
                else
                    Artwork(
                        imageUrl,
                        images,
                        Modifier.fillMaxWidth()
                            .aspectRatio(imageRatio)
                            .clip(RoundedCornerShape(MediaCardLayout.Corner)),
                        item.name,
                    )
                if (item.resumeTicks > 0 && item.ticks > 0) {
                    val progress = (item.resumeTicks.toFloat() / item.ticks).coerceIn(0.02f, 1f)
                    Box(
                        Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f),
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color(0xFFCBD5E1),
                                            androidx.compose.ui.graphics.Color.White,
                                        )
                                    )
                                )
                        )
                    }
                }
                val unplayed = item.unplayedCount
                if (unplayed != null && unplayed > 0 && item.type == "Series") {
                    CardOverlayBadge(
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(6.dp),
                        text = "$unplayed",
                    )
                } else if (item.played && item.resumeTicks <= 0) {
                    CardOverlayBadge(
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(6.dp),
                        icon = Glyph.Check,
                    )
                }
                if (has4kQuality(item)) {
                    CardOverlayBadge(
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(6.dp),
                        text = "4K",
                    )
                }
            }
            Text(
                if (preferSeriesTitle && wide && item.seriesName.isNotBlank()) item.seriesName else item.name,
                modifier = Modifier.padding(top = MediaCardLayout.TextGap),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = Palette.Text,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showSubtitle && !item.libraryDirectory) Text(
                if (item.type == "Episode") "第 ${item.season} 季 · 第 ${item.episode} 集"
                else
                    listOf(item.year.takeIf { it > 0 }?.toString(), item.genres.firstOrNull())
                        .filterNotNull()
                        .joinToString(" · "),
                color = Palette.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.2.sp,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
